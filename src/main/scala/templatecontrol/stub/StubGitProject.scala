package templatecontrol.stub

import better.files.File
import com.jcraft.jsch.Session
import org.eclipse.jgit.api._
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.OpenSshConfig.Host
import org.eclipse.jgit.transport._
import org.kohsuke.github.GHHook
import org.kohsuke.github.GHRepository
import templatecontrol.GitProject

class StubGitProject(workingDir: File, upstream: GHRepository, remote: GHRepository) extends GitProject {
  import scala.collection.JavaConverters._

  private val logger = org.slf4j.LoggerFactory.getLogger(this.getClass)

  private val upstreamUrl = upstream.getSshUrl

  private val remoteUrl = remote.getSshUrl

  private val git: Git = {
    def transportConfigCallback = {
      val sshSessionFactory = new JschConfigSessionFactory() {
        protected override def configure(host: Host, session: Session) = {}
      }

      new TransportConfigCallback() {
        override def configure(transport: Transport): Unit = {
          val sshTransport = transport.asInstanceOf[SshTransport]
          sshTransport.setSshSessionFactory(sshSessionFactory)
        }
      }
    }

    val git = Git
      .cloneRepository()
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
    ???
  }

  override def hooks: Seq[GHHook] = {
    upstream.getHooks.asScala
  }

  override def pullRequest(localBranchName: String, branchName: String, message: String): Unit = {
    val head = s"${remote.getOwnerName}:$localBranchName"
    val base = branchName

    logger.info(s"pullRequest: head = $head, base = $base")
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
    val refs = git
      .branchList()
      .call()
      .asScala

    for (ref <- refs) {
      logger.debug(s"branches: ref = $ref")
    }
    refs
  }

  override def createBranch(branchName: String, startPoint: String): Ref = {
    val ref = git
      .branchCreate()
      .setForce(true)
      .setName(branchName)
      .setStartPoint(startPoint)
      .call()

    logger.debug(s"createBranch: branchName = $branchName, ref = $ref")

    ref
  }

  // Check out an existing branch.
  override def checkout(branchName: String): Ref = {
    val ref = git
      .checkout()
      .setName(branchName)
      .call()

    logger.debug(s"checkout: branchName = $branchName, ref = $ref")

    ref
  }

  override def fastForward(remote: String, branchName: String): MergeResult = {
    val ref = git.getRepository.findRef(s"$remote/$branchName")

    val res = git
        .merge()
        .include(ref)
        .setFastForward(MergeCommand.FastForwardMode.FF_ONLY)
        .call()

    logger.debug(s"fast-forward: remote = $remote, branchName = $branchName, res = $res")

    res
  }

  override def add(): DirCache = {
    logger.debug(s"add: ")

    git
      .add()
      .addFilepattern(".")
      .call()
  }

  override def commit(message: String): RevCommit = {
    logger.debug(s"commit: $message")

    git
      .commit()
      .setAll(true)
      .setMessage(message)
      .call()
  }

  override def branchCreate(branchName: String): Ref = {
    logger.debug(s"branchCreate: $branchName")

    git
      .branchCreate()
      .setName(branchName)
      .call()
  }

  override def push(remote: String, name: String, force: Boolean): Iterable[PushResult] = {
    logger.info(s"push: ${this.remote}")

    Seq.empty
  }

  override def status(): Status = {
    val status = git.status().call()
    logger.debug(
      s"status: status isClean = ${status.isClean}, hasUncommittedChanges = ${status.hasUncommittedChanges}",
    )

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
//
//  private def logging[T <: OperationResult](result: T): T = {
//    val messages = result.getMessages
//    logger.info(s"messages: $messages")
//    result
//  }
}
