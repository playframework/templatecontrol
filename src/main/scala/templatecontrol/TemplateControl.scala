package templatecontrol

import better.files._
import com.typesafe.config.Config
import org.slf4j.LoggerFactory

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class TemplateControl(config: TemplateControlConfig, githubClient: GithubClient) {
  import scala.concurrent.blocking
  import scala.concurrent.ExecutionContext.Implicits._

  import TemplateControl._

  private val webhook = config.github.webhook

  private def tasks(config: Config): Seq[Task] = {
    Seq(new CopyTask(config),new FindReplaceTask(config))
  }

  def run(tempDirectory: File, templates: Seq[String], noPush: Boolean): Future[Seq[ProjectResult]] = {
    Future.sequence {
      templates.map { (templateName: String) =>
        val templateDir: File = tempDirectory / templateName
        createTemplate(templateDir, templateName, noPush)
      }
    }
  }

  def createTemplate(templateDir: File, templateName: String, noPush: Boolean): Future[ProjectResult] = Future {
    blocking {
      projectControl(templateDir, templateName) { gitProject =>
        processWebHooks(gitProject, webhook)
        config.branchConfigs.map { branchConfig =>
          branchControl(branchConfig, gitProject, noPush) { t =>
            t.flatMap(_.execute(templateDir))
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

  private def generateMessage(results: Seq[TaskResult]): String = {
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

    logger.info(s"template dir: $templateDir")
    if (templateDir.exists) {
      templateDir.delete()
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

  private def branchControl(branchConfig: BranchConfig, gitRepo: GitProject, noPush: Boolean)
                           (branchFunction: (Seq[Task]) => Seq[TaskResult]): BranchResult = {
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

      val results = branchFunction(tasks(branchConfig.config))
      val message = generateMessage(results)

      // Did anything change?
      if (!gitRepo.status().isClean) {
        // If so, add the changes to the branch...
        // "git add ."
        gitRepo.add()

        // Commit the added changes...
        // "git commit -m $message"
        gitRepo.commit(message)

        if (!noPush) {
          // Push this new branch to the remote repository
          // "git push -f origin templatecontrol-2.5.x"
          gitRepo.push(localBranchName, force = true)

          // And finally, create a pull request
          // from the remote github project ("wsargent/play-streaming-java")
          // to upstream github project ("playframework/play-streaming-java")
          // "hub pull-request \
          //   -h playframework/play-streaming-java:2.5.x \
          //   -b wsargent/play-streaming-java:templatecontrol-2.5.x"
          gitRepo.pullRequest(localBranchName, branchName, message)
        }
      }
      BranchSuccess(branchName, results)
    } catch {
      case e: Exception =>
        e.printStackTrace()
        BranchFailure(branchName, e)
    }
  }
}

object TemplateControl {
  import scala.concurrent.ExecutionContext.Implicits._

  private def liveGithubClient(github: GithubConfig): GithubClient = {
    import templatecontrol.live.LiveGithubClient
    val user = github.credentials.user
    val oauthToken = github.credentials.oauthToken
    val remote = github.remote
    val upstream = github.upstream
    new LiveGithubClient(user, oauthToken, remote, upstream)
  }

  //  private def stubGithubClient(github: GithubConfig): GithubClient = {
  //    val user = github.credentials.user
  //    val oauthToken = github.credentials.oauthToken
  //    val remote = github.remote
  //    val upstream = github.upstream
  //    new StubGithubClient(user, oauthToken, remote, upstream)
  //  }

  val logger = LoggerFactory.getLogger(TemplateControl.getClass)
  def main(args: Array[String]): Unit = {
    import com.typesafe.config.ConfigFactory

    val config =
      TemplateControlConfig.fromTypesafeConfig(ConfigFactory.load())
        .copy(noPush = args.contains("--no-push"))

    logger.info("running dry-run: " + config.noPush)

    val client = liveGithubClient(config.github)
    //val client = stubGithubClient(config.github)

    val control = new TemplateControl(config, client)

    val names = config.templates
    val reports = control.run(tempDirectory(config.baseDirectory), names, config.noPush).map { results =>
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
              case TaskResult(finder, modified) =>
                sb ++= s"    ${finder.path}:\n"
                sb ++= s"       $modified\n"
            }
          case BranchFailure(branch, e) =>
            sb ++= s"https://github.com/$upstream/$name/tree/$branch - FAILURE\n"
            exceptionToString(sb, e)
            sb.append("\n")
          case _ =>
          // do nothing
        }
        sb.toString()

      case ProjectFailure(name, e) =>
        val sb = new StringBuilder(s"$name: FAILURE\n")
        exceptionToString(sb, e)
        sb.append("\n")
        sb.toString

    }
    println(s.mkString(""))
  }

  def exceptionToString(sb: StringBuilder, e: Exception): Unit = {
    sb.append("    Exception: ")
    sb.append(e.getMessage)
    sb.append("\n")
    //val errors = new java.io.StringWriter()
    //e.printStackTrace(new java.io.PrintWriter(errors))
  }

  def tempDirectory(baseDirectory: File): File = {
    import java.nio.file.attribute.PosixFilePermissions
    import java.nio.file.Files

    val perms = PosixFilePermissions.fromString("rwx------")
    val attrs = PosixFilePermissions.asFileAttribute(perms)

    logger.info(s"base dir: $baseDirectory")
    val tempFile: File =
      if (!baseDirectory.isDirectory) Files.createDirectory(baseDirectory.toJava.toPath, attrs)
      else baseDirectory

    // Note this only happens if you don't interrupt or crash the JVM in some way.
    tempFile.toJava.deleteOnExit()
    tempFile
  }

  sealed trait BranchResult { def name: String }
  case class BranchSuccess(name: String, results: Seq[TaskResult]) extends BranchResult
  case class BranchFailure(name: String, exception: Exception) extends BranchResult

  sealed trait ProjectResult { def name: String }
  case class ProjectFailure(name: String, exception: Exception) extends ProjectResult
  case class ProjectSuccess(name: String, results: Seq[BranchResult]) extends ProjectResult

}
