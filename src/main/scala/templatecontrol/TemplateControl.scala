package templatecontrol

import better.files._
import org.scalactic._

class TemplateControl(config: TemplateControlConfig) {

  import TemplateControl._

  private val logger = org.slf4j.LoggerFactory.getLogger(this.getClass)

  private val githubClient: GithubClient = {
    val user = config.github.credentials.user
    val oauthToken = config.github.credentials.oauthToken
    val remote = config.github.remote
    val upstream = config.github.upstream
    new GithubClient(user, oauthToken, remote, upstream)
  }

  private val webhook = config.github.webhook

  def run(tempDirectory: File): Seq[TemplateResult] Or Every[ErrorMessage] = {
    import org.scalactic.Accumulation._
    config.templates.map { templateName =>
      val templateDir = tempDirectory / templateName
      templateControl(templateDir, templateName) { gitProject =>
        processWebHooks(gitProject, webhook)
        config.branchConfigs.map { branchConfig =>
          branchControl(branchConfig, gitProject) { finderConfigs =>
            val finders = generateFinders(finderConfigs)
            findAndReplace(templateDir, finders)
            generateMessage(branchConfig)
          }.accumulating
        }.combined
      }.map(results => TemplateResult(templateName, results))
    }.combined
  }

  private def findWebhook(gitProject: GitProject, webhook: Map[String, String]): Boolean = {
    // XXX eventually we'll want complete search and insertion of missing webhooks...
    val configKey = "url"
    gitProject.hooks.exists(_.getConfig.get(configKey) == webhook(configKey))
  }

  private def processWebHooks(gitProject: GitProject, webhook: GithubWebhookConfig) = {
    try {
      if (!findWebhook(gitProject, webhook.config)) {
        logger.error(s"Project does not contain $webhook!")
        throw new IllegalStateException(s"Cannot add webhook $webhook")
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

  private def generateMessage(branchConfig: BranchConfig): String = {
    import java.time.Instant

    val sb = new StringBuilder(s"Updated with template-control on ${Instant.now()}")
    sb ++= "\n"
    branchConfig.finders.foreach { finder =>
      sb ++= s"  File-Pattern: ${finder.pattern}\n"
      finder.conversions.foreach {
        case (k, v) =>
          sb ++= s"    If-Found-In-Line: $k\n"
          sb ++= s"      Replace-Line-With: $v\n"
      }
    }
    sb.toString
  }

  private def templateControl(templateDir: File, templateName: String)
                             (branchFunction: GitProject => Seq[BranchResult] Or Every[ErrorMessage]) = {
    logger.info(s"Cloning template $templateName into $templateDir")

    if (templateDir.exists) {
      throw new IllegalStateException(s"$templateDir already exists!")
    }

    // Clone a git repo for this template.
    val gitRepo = githubClient.clone(templateDir, templateName)
    try {
      // Make sure we have all the branches from remote.
      gitRepo.fetch()

      branchFunction(gitRepo)
    } catch {
      case e: Exception =>
        val msg = s"Template $templateName failed: ${e.getMessage}"
        logger.error(msg, e)
        Bad(One(msg))
    } finally {
      // close the repo to stop file descriptors from leaking.
      gitRepo.close()
    }
  }

  private def branchControl(branchConfig: BranchConfig, gitRepo: GitProject)
                           (replaceFunction: (Seq[FinderConfig] => String)) = {
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

      val message = replaceFunction(branchConfig.finders)

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
      Good(BranchResult(branchName, s"$branchName updated!"))
    } catch {
      case e: Exception =>
        val msg = s"Cannot update template, branch ${branchConfig.name}"
        logger.error(msg, e)
        Bad(msg)
    }
  }

  private def findAndReplace(workingDir: File, finders: Seq[Finder]): Unit = {
    finders.foreach { finder =>
      workingDir.glob(finder.pattern).foreach { file =>
        val tempFile = file.parent / s"${file.name}.tmp"
        file.lines.foreach { line =>
          finder.replacers.foldLeft(line)((acc, f) => f(acc)) >>: tempFile
        }
        file.delete()
        tempFile.renameTo(file.name)
      }
    }
  }

  private def generateFinders(finderConfigs: Seq[FinderConfig]): Seq[Finder] = {
    finderConfigs.map { finderConfig =>
      val pattern = finderConfig.pattern
      val replacers = finderConfig.conversions.map {
        case (k, v) =>
          (s: String) =>
            k.r.findFirstIn(s) match {
              case Some(_) => v
              case None => s
            }
      }.toSeq
      Finder(pattern, replacers)
    }
  }

  private case class Finder(pattern: String, replacers: Seq[String => String])

}

object TemplateControl {

  case class BranchResult(name: String, result: String)
  case class TemplateResult(name: String, results: Seq[BranchResult])

  def main(args: Array[String]): Unit = {
    import com.typesafe.config.ConfigFactory
    val config = TemplateControlConfig.fromTypesafeConfig(ConfigFactory.load())

    val control = new TemplateControl(config)

    // print out any errors accumulated.
    control.run(tempDirectory(config.baseDirectory)).badMap { everyMessage =>
      everyMessage.foreach { errorMessage =>
        println(errorMessage)
      }
    }
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
