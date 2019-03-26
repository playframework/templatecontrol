package templatecontrol

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.time.Instant

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.blocking
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.concurrent.Future

import better.files._
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory
import templatecontrol.live.LiveGithubClient
import templatecontrol.model.Lagom
import templatecontrol.model.Play
import templatecontrol.model.Project
import templatecontrol.stub.StubGithubClient

object RunPlayAll  extends App { TemplateControl.runFor(args, Play.play26, Play.play27)     }
object RunPlay26   extends App { TemplateControl.runFor(args, Play.play26)                  }
object RunPlay27   extends App { TemplateControl.runFor(args, Play.play27)                  }
object RunLagomAll extends App { TemplateControl.runFor(args, Lagom.lagom14, Lagom.lagom15) }
object RunLagom14  extends App { TemplateControl.runFor(args, Lagom.lagom14)                }
object RunLagom15  extends App { TemplateControl.runFor(args, Lagom.lagom15)                }

object RunMergify {
  def main(args: Array[String]): Unit = {
    val control0 = TemplateControl.create(args)
    val config = control0.config.copy(noPr = true)
    val control = new TemplateControl(config, control0.githubClient)

    val branchConfig = ConfigFactory.parseString(
      """{ copy = [{ path = "/.mergify.yml", template = ".mergify.yml" }], finders = [] }""",
    )

    // TODO: Add Lagom, when Mergify knows how to push to lagom
    // val projects = Seq(Play.play27, Lagom.lagom14, Project("Lagom", "1.5.x", Lagom.templates15))
    val projects = Seq(Play.play27)

    for (project <- projects) {
      val results = control.runBranch(project, BranchConfig(project.branchName, branchConfig))
      control.reportBlocking(results)
    }
  }
}

final class TemplateControl(val config: TemplateControlConfig, val githubClient: GithubClient) {
  import TemplateControl._

  def run(project: Project): Future[Seq[ProjectResult]] = {
    val branchConfig = config.branchConfigFor(project.branchName)
    runBranch(project, branchConfig)
  }

  def runBranch(project: Project, branchConfig: BranchConfig): Future[Seq[ProjectResult]] = {
    Future.sequence {
      project.templates.map { tpl =>
        runTemplate(branchConfig, tpl.name)
      }
    }
  }

  def reportBlocking(results: Future[Seq[ProjectResult]]) = {
    val future = results.map(results => TemplateControl.report(config.github.upstream, results))
    Await.result(future, Duration.Inf)
  }

  private def runTemplate(
      branchConfig: BranchConfig,
      templateName: String,
  ): Future[ProjectResult] = Future {
    val templateDir = tempDirectory(config.baseDirectory) / branchConfig.name / templateName
    blocking {
      projectControl(templateDir, templateName) { gitProject =>
        if (config.noPr) {
          push(branchConfig, gitProject) { tasks =>
            tasks.flatMap(_.execute(templateDir))
          }
        } else {
          pr(branchConfig, gitProject) { tasks =>
            tasks.flatMap(_.execute(templateDir))
          }
        }
      }
    }
  }

  private def projectControl(templateDir: File, templateName: String)(
      branchFunction: GitProject => BranchResult,
  ): ProjectResult = {
    logger.info(s"template dir: $templateDir")

    if (templateDir.exists) {
      templateDir.delete()
    }

    val gitRepo = githubClient.clone(templateDir, templateName) // Clone this template's repo.
    try {
      gitRepo.fetch() // Make sure we have all the branches from remote.
      ProjectSuccess(templateName, branchFunction(gitRepo))
    } catch {
      case e: Exception => ProjectFailure(templateName, e)
    } finally {
      gitRepo.close() // close the repo to stop file descriptors from leaking.
    }
  }

  private def push(branchConfig: BranchConfig, gitRepo: GitProject)(
      branchFunction: (Seq[Task]) => Seq[TaskResult],
  ): BranchResult = {
    val branchName: String = branchConfig.name
    try {
      // Checkout the target branch:
      // "git checkout 2.7.x"
      gitRepo.checkout(branchName)

      val results = branchFunction(configToTasks(branchConfig.config))

      // Did anything change?
      if (!gitRepo.status().isClean) {
        // If so, add the changes to the branch...
        // "git add ."
        gitRepo.add()

        val message = generateMessage(results)

        // Commit the added changes...
        // "git commit -m $message"
        gitRepo.commit(message)

        if (!config.noPush) {
          // Push the branch to the remote repository
          // "git push origin 2.7.x"
          gitRepo.push(branchName)
        }
      }
      BranchSuccess(branchName, results)
    } catch {
      case e: Exception => BranchFailure(branchName, e)
    }
  }

  private def pr(branchConfig: BranchConfig, gitRepo: GitProject)(
      branchFunction: (Seq[Task]) => Seq[TaskResult],
  ): BranchResult = {
    val branchName: String = branchConfig.name
    try {
      // Create a local branch from the upstream template's branch.
      // i.e. "git branch templatecontrol-2.7.x upstream/2.7.x"
      val localBranchName = s"templatecontrol-$branchName"
      val startPoint      = s"upstream/$branchName"
      gitRepo.createBranch(localBranchName, startPoint)

      // Checkout the branch we just created:
      // "git checkout templatecontrol-2.7.x"
      gitRepo.checkout(localBranchName)

      val results = branchFunction(configToTasks(branchConfig.config))

      // Did anything change?
      if (!gitRepo.status().isClean) {
        // If so, add the changes to the branch...
        // "git add ."
        gitRepo.add()

        val message = generateMessage(results)

        // Commit the added changes...
        // "git commit -m $message"
        gitRepo.commit(message)

        if (!config.noPush) {
          // Push this new branch to the remote repository
          // "git push -f origin templatecontrol-2.7.x"
          gitRepo.push(localBranchName, force = true)

          // And finally, create a pull request
          // from the remote github project ("wsargent/play-streaming-java")
          // to upstream github project ("playframework/play-streaming-java")
          // "hub pull-request \
          //   -h playframework/play-streaming-java:2.7.x \
          //   -b wsargent/play-streaming-java:templatecontrol-2.7.x"
          gitRepo.pullRequest(localBranchName, branchName, message)
        }
      }
      BranchSuccess(branchName, results)
    } catch {
      case e: Exception => BranchFailure(branchName, e)
    }
  }

  private def configToTasks(config: Config): Seq[Task] = {
    Seq(new CopyTask(config), new FindReplaceTask(config))
  }

  private def generateMessage(results: Seq[TaskResult]): String = {
    val sb = new StringBuilder(s"Updated with template-control on ${Instant.now()}\n")
    results.foreach { result =>
      sb ++= s"  ${result.config.path}:\n"
      sb ++= s"    ${result.modified}\n"
    }
    sb.result()
  }
}

object TemplateControl {
  val logger = LoggerFactory.getLogger(TemplateControl.getClass)

  def runFor(args: Array[String], projects: Project*): Unit = {
    val control = create(args)
    for (project <- projects) {
      val results = control.run(project)
      control.reportBlocking(results)
    }
  }

  def create(args: Array[String]) = {
    val config =
      TemplateControlConfig
        .fromTypesafeConfig(ConfigFactory.load())
        .copy(noPush = args.contains("--no-push") || args.contains("--dry-run"))

    logger.info("running dry-run: " + config.noPush)
    logger.info(s"base dir: ${config.baseDirectory}")

    val client = liveGithubClient(config.github)
    //val client = stubGithubClient(config.github)

    new TemplateControl(config, client)
  }

  private def liveGithubClient(github: GithubConfig): GithubClient = {
    val user       = github.credentials.user
    val oauthToken = github.credentials.oauthToken
    val remote     = github.remote
    val upstream   = github.upstream
    new LiveGithubClient(user, oauthToken, remote, upstream)
  }

  private def stubGithubClient(github: GithubConfig): GithubClient = {
    val user       = github.credentials.user
    val oauthToken = github.credentials.oauthToken
    val remote     = github.remote
    val upstream   = github.upstream
    new StubGithubClient(user, oauthToken, remote, upstream)
  }

  def report(upstream: String, results: Seq[ProjectResult]): Unit = {
    val s = results.map {
      case ProjectSuccess(name, branch) =>
        val sb = new StringBuilder
        branch match {
          case BranchSuccess(branch, replacements) =>
            if (replacements.nonEmpty) {
              sb ++= s"https://github.com/$upstream/$name/tree/$branch - replacements = ${replacements.length}\n"
              replacements.foreach {
                case TaskResult(finder, modified) =>
                  sb ++= s"    ${finder.path}:\n"
                  sb ++= s"       $modified\n"
              }
            }
          case BranchFailure(branch, e) =>
            sb ++= s"https://github.com/$upstream/$name/tree/$branch - FAILURE\n"
            exceptionToString(sb, e)
            sb += '\n'
        }
        sb.toString()

      case ProjectFailure(name, e) =>
        val sb = new StringBuilder(s"$name: FAILURE\n")
        exceptionToString(sb, e)
        sb += '\n'
        sb.result()

    }
    println(s.mkString(""))
  }

  def exceptionToString(sb: StringBuilder, e: Exception): Unit = {
    sb ++= s"    Exception: ${e.getMessage}\n"
    e.printStackTrace(new java.io.PrintWriter(new java.io.Writer() {
      override def write(str: String): Unit                  = sb ++= str
      def write(cbuf: Array[Char], off: Int, len: Int): Unit = write(new String(cbuf, off, len))
      def flush(): Unit                                      = ()
      def close(): Unit                                      = ()
    }))
  }

  def tempDirectory(baseDirectory: File): File = {
    val tempFile: File =
      if (baseDirectory.isDirectory) baseDirectory
      else {
        val perms = PosixFilePermissions.fromString("rwx------")
        val attrs = PosixFilePermissions.asFileAttribute(perms)
        Files.createDirectory(baseDirectory.toJava.toPath, attrs)
      }

    // Note this only happens if you don't interrupt or crash the JVM in some way.
    tempFile.toJava.deleteOnExit()

    tempFile
  }

  sealed trait BranchResult { def name: String }
  final case class BranchSuccess(name: String, results: Seq[TaskResult]) extends BranchResult
  final case class BranchFailure(name: String, exception: Exception)     extends BranchResult

  sealed trait ProjectResult { def name: String }
  final case class ProjectFailure(name: String, exception: Exception)  extends ProjectResult
  final case class ProjectSuccess(name: String, results: BranchResult) extends ProjectResult

}
