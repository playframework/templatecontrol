package templatecontrol.live

import better.files.File
import com.jcraft.jsch.Session
import org.eclipse.jgit.api._
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.OpenSshConfig.Host
import org.eclipse.jgit.transport._
import org.kohsuke.github.{GHEvent, GHHook, GHRepository}
import templatecontrol.GitProject


class LiveGitProject(workingDir: File, upstream: GHRepository, remote: GHRepository) extends GitProject {
  import scala.collection.JavaConverters._

  private val logger = org.slf4j.LoggerFactory.getLogger(this.getClass)

  // https://github.com/sbt/sbt-git/blob/master/src/main/scala/com/typesafe/sbt/git/JGit.scala
  // https://github.com/centic9/jgit-cookbook/blob/master/src/main/java/org/dstadler/jgit/porcelain/CloneRemoteRepositoryWithAuthentication.java

  // see https://github.com/eclipse/jgit/tree/master/org.eclipse.jgit.test/tst/org/eclipse/jgit/api
  // https://dev.eclipse.org/mhonarc/lists/jgit-dev/maillist.html
  // http://www.codeaffine.com/2015/11/30/jgit-clone-repository/
  // http://www.codeaffine.com/2014/12/09/jgit-authentication/
  // https://www.nuxeo.com/blog/jgit-example/
  // https://github.com/centic9/jgit-cookbook
  // https://github.com/jenkinsci/github-plugin/blob/master/src/main/java/org/jenkinsci/plugins/github/webhook/WebhookManager.java

  private val upstreamUrl = upstream.gitHttpTransportUrl

  private val remoteUrl = remote.getSshUrl

  private val git: Git = {
    def transportConfigCallback = {
      val sshSessionFactory = new JschConfigSessionFactory() {
        override protected def configure(host: Host, session: Session) = {}
      }

      new TransportConfigCallback() {
        override def configure(transport: Transport): Unit = {
          val sshTransport = transport.asInstanceOf[SshTransport]
          sshTransport.setSshSessionFactory(sshSessionFactory)
        }
      }
    }

    val git = Git.cloneRepository()
      .setURI(remoteUrl)
      .setCloneAllBranches(true)
      .setDirectory(workingDir.toJava)
      .setTransportConfigCallback(transportConfigCallback)
      .call()

    val config = git.getRepository.getConfig
    config.setString("remote", "upstream", "url", upstreamUrl)
    config.setString("remote", "upstream", "fetch", "+refs/heads/*:refs/remotes/upstream/*")
    config.save()

    git
  }

  override def addWebhook(name: String, config: Map[String, String]): GHHook = {
    val events = Set(GHEvent.PUSH)
    // https://developer.github.com/v3/repos/hooks/#create-a-hook
    // FIXME this does not seem to work as it passes in secret directly instead of using
    // X-Hub-Signature in the header.
    // May have to move to https://github.com/eclipse/egit-github/tree/master/org.eclipse.egit.github.core
    upstream.createHook(name, config.asJava, events.asJava, true)
  }

  override def hooks: Seq[GHHook] = {
    upstream.getHooks.asScala
  }

  override def pullRequest(localBranchName: String, branchName: String, message: String): Unit = {
    val head = s"${remote.getOwnerName}:$localBranchName"
    val base = branchName

    logger.debug(s"pullRequest: head = $head, base = $base")
    val title = s"Upgrade branch $base using TemplateControl"
    val body = s"""```
                  |$message
                  |```
          """.stripMargin
    upstream
      .createPullRequest(title, head, base, body)
      .setLabels("merge-when-green", "template-control")
  }

  override def fetch(): Unit = {
    val fetchResult = git.fetch().setRemote("upstream").call()
    fetchResult.getAdvertisedRefs.asScala.foreach { ref =>
      val refUpdate = fetchResult.getTrackingRefUpdate(ref.getName)
      if (refUpdate != null) {
        val result = refUpdate.getResult
        logger.debug(s"fetch: result = $result")
      }
    }
  }

  override def pull(): PullResult = {
    git.pull().setRebase(true).call()
  }

  override def rebase(branchName: String): RebaseResult = {
    git.rebase.setUpstream(s"upstream/$branchName").call()
  }

  override def branches(): Seq[Ref] = {
    val refs = git.branchList()
      .call()
      .asScala

    for (ref <- refs) {
      logger.debug(s"branches: ref = $ref")
    }
    refs
  }

  override def createBranch(branchName: String, startPoint: String): Ref = {
    val ref = git.branchCreate()
      .setForce(true)
      .setName(branchName)
      .setStartPoint(startPoint)
      .call()

    logger.debug(s"createBranch: branchName = $branchName, ref = $ref")

    ref
  }

  // Check out an existing branch.
  override def checkout(branchName: String): Ref = {
    val ref = git.checkout()
      .setName(branchName)
      .call()

    logger.debug(s"checkout: branchName = $branchName, ref = $ref")

    ref
  }

  override def add(): DirCache = {
    logger.debug(s"add: ")

    git.add()
      .addFilepattern(".")
      .call()
  }

  override def commit(message: String): RevCommit = {
    logger.debug(s"commit: $message")

    git.commit()
      .setAll(true)
      .setMessage(message)
      .call()
  }

  override def branchCreate(branchName: String): Ref = {
    logger.debug(s"branchCreate: $branchName")

    git.branchCreate()
      .setName(branchName)
      .call()
  }

  override def push(name: String, force: Boolean): Iterable[PushResult] = {
    logger.debug(s"push: $remote")

    val spec = new RefSpec(s"refs/heads/$name:refs/heads/$name")
    val results = git.push()
      .setForce(force)
      .setRemote("origin")
      .setRefSpecs(spec)
      .call().asScala

    for (result <- results) {
      logger.debug(s"push: result = ${result.getURI}")
      result.getAdvertisedRefs.asScala.foreach { ref =>
        val remoteRefUpdate = result.getRemoteUpdate(ref.getName)
        if (remoteRefUpdate != null) {
          remoteRefUpdate.getStatus match {
            case RemoteRefUpdate.Status.OK =>
              logger.debug(s"push: remoteRefUpdate = $remoteRefUpdate")

            case status =>
              val msg = s"push failed with status $status with $remoteRefUpdate"
              throw new IllegalStateException(msg)
          }
        }
      }

      logger.debug(s"push: messages = ${result.getMessages}")
    }
    results
  }

  override def status(): Status = {
    val status = git.status().call()
    logger.debug(s"status: status isClean = ${status.isClean}, hasUncommittedChanges = ${status.hasUncommittedChanges}")

    status
  }

  override def log(): Iterable[RevCommit] = {
    val revCommits: Iterable[RevCommit] = git.log().call().asScala

    for (revCommit <- revCommits) {
      logger.info(s"status: message = ${revCommit.getShortMessage}")
    }

    revCommits
  }

  override def close(): Unit = git.close()

  //  private def logging[T <: OperationResult](result: T): T = {
  //    val messages = result.getMessages
  //    logger.info(s"messages: $messages")
  //    result
  //  }
}
