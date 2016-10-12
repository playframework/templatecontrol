package templatecontrol

import java.time.Instant

import better.files._
import com.typesafe.config.ConfigFactory

object TemplateControl {

  private val logger = org.slf4j.LoggerFactory.getLogger(this.getClass)

  def main(args: Array[String]): Unit = {
    val fg = new FinderGenerator
    val config = TemplateControlConfig.fromTypesafeConfig(ConfigFactory.load())

    val githubClient = {
      val user = config.github.credentials.user
      val oauthToken = config.github.credentials.oauthToken
      val remote = config.github.remote
      val upstream = config.github.upstream
      new GithubClient(user, oauthToken, remote, upstream)
    }

    // Delete the existing templates directory, if any.
    config.baseDirectory.delete(swallowIOExceptions = true)

    // For each project ("play-streaming-java")
    config.templates.foreach { templateName =>
      try {
        // Set up a working directory called "templates/play-streaming-java"
        val templateDir = config.baseDirectory / templateName
        templateControl(templateDir, templateName) { gitProject =>
          gitProject.hooks.foreach { hook =>
            logger.info(s"$templateName has hook ${hook.getName}")
          }

          // For each branch in the template ("2.5.x")
          config.branchConfigs.foreach { bc =>
            logger.info(s"In template $templateName, updating branch ${bc.name}")
            try {
              branchControl(bc.name, gitProject) { () =>
                val findAndReplace = new FindAndReplace(templateDir, fg(bc.finders))
                findAndReplace()

                val sb = new StringBuilder(s"Updated with template-control on ${Instant.now()}")
                sb ++= "\n"
                bc.finders.foreach { finder =>
                  sb ++= s"  File-Pattern: ${finder.pattern}\n"
                  finder.conversions.foreach { case (k, v) =>
                    sb ++= s"    If-Found-In-Line: $k\n"
                    sb ++= s"      Replace-Line-With: $v\n"
                  }
                }
                sb.toString
              }
            } catch {
              case e: Exception =>
                logger.error(s"Cannot update template $templateName, branch ${bc.name}", e)
            }
          }
        }
      } catch {
        case e: Exception =>
          logger.error(s"Cannot clone template $templateName", e)
      }
    }

    def templateControl(templateDir: File, templateName: String)(branchFunction: GitProject => Unit): Unit = {
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
      } finally {
        // close the repo to stop file descriptors from leaking.
        gitRepo.close()
      }
    }

    def branchControl(branchName: String, gitRepo: GitProject)(replaceFunction: (() => String)): Unit = {
      // Create a local branch from the upstream template's branch.
      // i.e. "git branch templatecontrol-2.5.x upstream/2.5.x"
      val localBranchName = s"templatecontrol-$branchName"
      val startPoint = s"upstream/$branchName"
      gitRepo.createBranch(localBranchName, startPoint)

      // Checkout the branch we just created:
      // "git checkout templatecontrol-2.5.x"
      gitRepo.checkout(localBranchName)

      val message = replaceFunction()

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
    }
  }

  class FinderGenerator() {
    def apply(finderConfigs: Seq[FinderConfig]): Seq[Finder] = {
      finderConfigs.map(apply)
    }

    def apply(finderConfig: FinderConfig): Finder = {
      val pattern = finderConfig.pattern
      val replacers = finderConfig.conversions.map { case (k, v) =>
        (s: String) =>
          k.r.findFirstIn(s) match {
            case Some(_) => v
            case None => s
          }
      }.toSeq
      Finder(pattern, replacers)
    }
  }

  case class Finder(pattern: String, replacers: Seq[String => String])

  class FindAndReplace(workingDir: File, finders: Seq[Finder]) {
    def apply(): Unit = {
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
  }

}
