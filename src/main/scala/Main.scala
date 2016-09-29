
import org.eclipse.jgit.api.CreateBranchCommand.SetupUpstreamMode
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.merge.MergeStrategy
import org.eclipse.jgit.transport.{CredentialsProvider, PushResult, RefSpec, UsernamePasswordCredentialsProvider}
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.revwalk.RevCommit
import better.files._
import java.io.{IOException, File => JFile}

import scala.collection.JavaConverters._

object Main {

  def main(args: Array[String]): Unit = {
    val user = "wsargent"
    val repo = "sample-template"

    implicit lazy val creds = new UsernamePasswordCredentialsProvider(
      System.console.readLine("username>"),
      System.console.readPassword("password>")
    )

    def cloneProject(): Project = {
      val localPath = Temp.newDirectory()

      val ref = new ProjectRef(user, repo, localPath)
      ref.cloneProject()
    }

    ammonite.Main().run(
      "cloneProject" -> cloneProject(),
    )
  }
}

object Temp {
  def newDirectory(): File = {
    val localPath = File.newTemporaryDirectory()
    localPath.toJava.deleteOnExit()
    localPath
  }
}

class ProjectRef(user: String, repo: String, localPath: File) {

  def cloneProject(): Project = {
    val directory: File = localPath / repo
    val git = Git.cloneRepository()
      .setURI(s"https://github.com/$user/$repo")
      .setDirectory(directory.toJava)
      .call()
    new Project(git)
  }

}

class Project(git: Git) {

  def add(): DirCache = {
    git.add()
      .addFilepattern(".")
      .call()
  }

  def commit(): RevCommit = {
    git.commit()
      .setAll(true)
      .setMessage(".")
      .call()
  }

  def push(refSpec: RefSpec, remoteUrl: String)(implicit creds: CredentialsProvider): Iterable[PushResult] = {
    git.push()
      .setRemote(remoteUrl)
      .setCredentialsProvider(creds)
      .setRefSpecs(refSpec)
      .call().asScala
  }

}