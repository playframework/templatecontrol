package templatecontrol

import java.time.Instant

import better.files._
import com.typesafe.config.ConfigFactory

object TemplateControl {

  def main(args: Array[String]): Unit = {
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

      // Set up a working directory called "templates/play-streaming-java"
      val templateDir = config.baseDirectory / templateName

      if (templateDir.exists) {
        throw new IllegalStateException(s"$templateDir already exists!")
      }

      // Clone a git repo for this template.
      val gitProject = githubClient.clone(templateDir, templateName)

      // Make sure we have all the branches from remote.
      gitProject.fetch()

      // For each branch in the template ("2.5.x")
      config.branchConfigs.foreach { branchConfig =>
        branchControl(branchConfig.name, gitProject) { () =>
          val textReplacer = new TextReplacer(templateDir)
          textReplacer.process(branchConfig.finders)
        }
      }

      // close the repo to stop file descriptors from leaking.
      gitProject.close()
    }

    def branchControl(branchName: String, gitProject: GitProject)(replaceFunction: (() => String)): Unit = {
      // Create a local branch from the upstream template's branch.
      // i.e. "git branch templatecontrol-2.5.x upstream/2.5.x"
      val localBranchName = s"templatecontrol-$branchName"
      val startPoint = s"upstream/$branchName"
      gitProject.createBranch(localBranchName, startPoint)

      // Checkout the branch we just created:
      // "git checkout templatecontrol-2.5.x"
      gitProject.checkout(localBranchName)

      val message = replaceFunction()

      // Did anything change?
      if (!gitProject.status().isClean) {
        // If so, add the changes to the branch...
        // "git add ."
        gitProject.add()

        // Commit the added changes...
        // "git commit -m $message"
        gitProject.commit(message)

        // Push this new branch to the remote repository
        // "git push origin templatecontrol-2.5.x"
        gitProject.push(localBranchName)

        // And finally, create a pull request
        // from the remote github project ("wsargent/play-streaming-java")
        // to upstream github project ("playframework/play-streaming-java")
        // "hub pull-request \
        //   -h playframework/play-streaming-java:2.5.x \
        //   -b wsargent/play-streaming-java:templatecontrol-2.5.x"
        gitProject.pullRequest(localBranchName, branchName, message)
      }
    }
  }

  class TextReplacer(workingDir: File) {

    def process(finders: Seq[FinderConfig]): String = {
      finders.foreach(findAndReplace)
      commitMessage(finders)
    }

    private def findAndReplace(finder: FinderConfig): Unit = {
      val conversions = conversionFunctions(finder)
      workingDir.glob(finder.pattern).foreach { file =>
        val tempFile = file.parent / s"${file.name}.tmp"
        file.lines.foreach { line =>
          conversions.foldLeft(line)((acc, f) => f(acc)) >>: tempFile
        }
        file.delete()
        tempFile.renameTo(file.name)
      }
    }

    private def conversionFunctions(finder: FinderConfig): Seq[String => String] = {
      finder.conversions.map { case (k, v) =>
        (s: String) =>
          k.r.findFirstIn(s) match {
            case Some(_) => v
            case None => s
          }
      }.toSeq
    }

    private def commitMessage(finders: Seq[FinderConfig]): String = {
      val sb = new StringBuilder(s"Updated with template-control on ${Instant.now()}")
      sb ++= "\n"
      finders.foreach { finder =>
        sb ++= s"  File-Pattern: ${finder.pattern}\n"
        finder.conversions.foreach { case (k, v) =>
          sb ++= s"    If-Found-In-Line: $k\n"
          sb ++= s"      Replace-Line-With: $v\n"
        }
      }
      sb.toString
    }
  }

}
