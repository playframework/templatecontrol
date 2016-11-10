package templatecontrol

import better.files._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class TemplateControl(config: TemplateControlConfig) {
  import scala.concurrent.blocking
  import scala.concurrent.ExecutionContext.Implicits._

  import TemplateControl._

  private val githubClient: GithubClient = {
    val user = config.github.credentials.user
    val oauthToken = config.github.credentials.oauthToken
    val remote = config.github.remote
    val upstream = config.github.upstream
    new GithubClient(user, oauthToken, remote, upstream)
  }

  private val webhook = config.github.webhook

  def run(tempDirectory: File, templates: Seq[String]): Future[Seq[ProjectResult]] = {
    Future.sequence {
      templates.map { (templateName: String) =>
        val templateDir: File = tempDirectory / templateName
        createTemplate(templateDir, templateName)
      }
    }
  }

  def createTemplate(templateDir: File, templateName: String): Future[ProjectResult] = Future {
    blocking {
      projectControl(templateDir, templateName) { gitProject =>
        processWebHooks(gitProject, webhook)
        config.branchConfigs.map { branchConfig =>
          branchControl(branchConfig, gitProject) { (finders, inserters) =>
            copy(templateDir, inserters) ++
              findAndReplace(templateDir, finders)
          }
        }
      }
    }
  }

  private def findWebhook(gitProject: GitProject, webhook: Map[String, String]): Boolean = {
    // XXX eventually we'll want complete search and insertion of missing webhooks...
    val configKey = "url"
    gitProject.hooks.exists(_.getConfig.get(configKey) == webhook(configKey))
  }

  private def processWebHooks(gitProject: GitProject, webhook: GithubWebhookConfig) = {
    try {
      if (!findWebhook(gitProject, webhook.config)) {
        val msg = s"Project does not contain $webhook!"
        throw new IllegalStateException(msg)
        // addWebhook does not appear to work because secret has to be defined in header :-(
        //gitProject.addWebhook(webhook.name, webhook.config)
      }
    } catch {
      case ise: IllegalStateException =>
        throw ise
      case e: Exception =>
        throw new IllegalStateException(s"Cannot add webhook $webhook", e)
    }
  }

  private def generateMessage(results: Seq[OperationResult]): String = {
    import java.time.Instant

    val sb = new StringBuilder(s"Updated with template-control on ${Instant.now()}")
    sb ++= "\n"
    results.foreach { result =>
      sb ++= s"  ${result.config.path}:\n"
      sb ++= s"    ${result.modified}\n"
    }
    sb.toString
  }

  private def projectControl(templateDir: File, templateName: String)
                             (branchFunction: GitProject => Seq[BranchResult]): ProjectResult = {
    if (templateDir.exists) {
      throw new IllegalStateException(s"$templateDir already exists!")
    }

    // Clone a git repo for this template.
    val gitRepo = githubClient.clone(templateDir, templateName)
    try {
      // Make sure we have all the branches from remote.
      gitRepo.fetch()

      val results = branchFunction(gitRepo)
      ProjectSuccess(templateName, results)
    } catch {
      case e: Exception =>
        ProjectFailure(templateName, e)
    } finally {
      // close the repo to stop file descriptors from leaking.
      gitRepo.close()
    }
  }

  private def branchControl(branchConfig: BranchConfig, gitRepo: GitProject)
                           (branchFunction: ((Seq[FinderConfig], Seq[CopyConfig]) => Seq[OperationResult])): BranchResult = {
    val branchName: String = branchConfig.name
    try {
      // Create a local branch from the upstream template's branch.
      // i.e. "git branch templatecontrol-2.5.x upstream/2.5.x"
      val localBranchName = s"templatecontrol-$branchName"
      val startPoint = s"upstream/$branchName"
      gitRepo.createBranch(localBranchName, startPoint)

      // Checkout the branch we just created:
      // "git checkout templatecontrol-2.5.x"
      gitRepo.checkout(localBranchName)

      val results = branchFunction(branchConfig.finders, branchConfig.copy)
      val message = generateMessage(results)

      // Did anything change?
      if (!gitRepo.status().isClean) {
        // If so, add the changes to the branch...
        // "git add ."
        gitRepo.add()

        // Commit the added changes...
        // "git commit -m $message"
        gitRepo.commit(message)

        // Push this new branch to the remote repository
        // "git push origin templatecontrol-2.5.x"
        gitRepo.push(localBranchName)

        // And finally, create a pull request
        // from the remote github project ("wsargent/play-streaming-java")
        // to upstream github project ("playframework/play-streaming-java")
        // "hub pull-request \
        //   -h playframework/play-streaming-java:2.5.x \
        //   -b wsargent/play-streaming-java:templatecontrol-2.5.x"
        gitRepo.pullRequest(localBranchName, branchName, message)
      }
      BranchSuccess(branchName, results)
    } catch {
      case e: Exception =>
        BranchFailure(branchName, e)
    }
  }

  private def copy(workingDir: File, copyConfigs: Seq[CopyConfig]): Seq[OperationResult] = {
    copyConfigs.flatMap { c =>
      val templateStream = Option(this.getClass.getResourceAsStream(c.template)).getOrElse(
        throw new IllegalStateException(s"Cannot find resource for ${c.template}")
      )
      val dest: File = s"${workingDir.path.toAbsolutePath}${c.path}".toFile
      blocking {
        for {
          in <- templateStream.autoClosed
          out <- dest.newOutputStream.autoClosed
        } in.pipeTo(out)
        val modified = s"wrote ${c.path}"
        Some(OperationResult(c, modified))
      }
    }
  }

  private def findAndReplace(workingDir: File, finderConfigs: Seq[FinderConfig]): Seq[OperationResult] = {
    finderConfigs.flatMap { finderConfig =>
      workingDir.glob(finderConfig.path).flatMap { file =>
        val tempFile = file.parent / s"${file.name}.tmp"
        val replaceFunctions = finderConfig.conversions.map {
          case (k, v) =>
            (s: String) =>
              k.r.findFirstIn(s) match {
                case Some(_) => v
                case None => s
              }
        }
        val results = file.lines.flatMap { line =>
          val modified = replaceFunctions.foldLeft(line)((acc, f) => f(acc))
          modified >>: tempFile
          if (line.equals(modified)) {
            None
          } else {
            Some(OperationResult(finderConfig, modified))
          }
        }
        file.delete()
        tempFile.renameTo(file.name)
        results
      }
    }
  }
}

object TemplateControl {
  import scala.concurrent.ExecutionContext.Implicits._

  case class OperationResult(config: OperationConfig, modified: String)

  sealed trait BranchResult { def name: String }
  case class BranchSuccess(name: String, results: Seq[OperationResult]) extends BranchResult
  case class BranchFailure(name: String, exception: Exception) extends BranchResult

  sealed trait ProjectResult { def name: String }
  case class ProjectFailure(name: String, exception: Exception) extends ProjectResult
  case class ProjectSuccess(name: String, results: Seq[BranchResult]) extends ProjectResult

  def main(args: Array[String]): Unit = {
    import com.typesafe.config.ConfigFactory
    val config = TemplateControlConfig.fromTypesafeConfig(ConfigFactory.load())
    val control = new TemplateControl(config)

    //    val exampleCodeClient = new ExampleCodeClient(config.exampleCodeServiceUrl)
    //    exampleCodeClient.call().map { maybeData =>
    //      maybeData.foreach { data =>
    //        println(s"data = ${data}")
    //      }
    //    }.andThen {
    //      case _ => exampleCodeClient.close()
    //    }
    val names = config.templates
    val reports = control.run(tempDirectory(config.baseDirectory), names).map { results =>
      val upstream = config.github.upstream
      report(upstream, results)
    }

    Await.result(reports, Duration.Inf)
  }

  def report(upstream: String, results: Seq[ProjectResult]): Unit = {
    val s = results.map {
      case ProjectSuccess(name, branches) =>
        val sb = new StringBuilder
        branches.foreach {
          case BranchSuccess(branch, replacements) if replacements.nonEmpty =>
            sb ++= s"https://github.com/$upstream/$name/tree/$branch - replacements = ${replacements.length}\n"
            replacements.foreach {
              case OperationResult(finder, modified) =>
                sb ++= s"    ${finder.path}:\n"
                sb ++= s"       $modified\n"
            }
          case BranchFailure(branch, e) =>
            sb ++= s"https://github.com/$upstream/$name/tree/$branch - FAILURE\n"
            sb ++= exceptionToString(e)

          case _ =>
          // do nothing
        }
        sb.toString()

      case ProjectFailure(name, e) =>
        val sb = new StringBuilder(s"$name: FAILURE\n")
        sb ++= exceptionToString(e)
        sb.toString

    }
    println(s.mkString(""))
  }

  def exceptionToString(e: Exception): String = {
    val errors = new java.io.StringWriter()
    e.printStackTrace(new java.io.PrintWriter(errors))
    errors.toString
  }

  def tempDirectory(baseDirectory: File): File = {
    import java.nio.file.attribute.PosixFilePermissions
    import java.nio.file.Files

    val perms = PosixFilePermissions.fromString("rwx------")
    val attrs = PosixFilePermissions.asFileAttribute(perms)
    val tempFile: File = Files.createTempDirectory(baseDirectory.toJava.toPath, "", attrs)

    // Note this only happens if you don't interrupt or crash the JVM in some way.
    tempFile.toJava.deleteOnExit()
    tempFile
  }

}
