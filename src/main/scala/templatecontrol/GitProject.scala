package templatecontrol

import better.files._
import com.jcraft.jsch.Session
import org.eclipse.jgit.api._
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib.{Ref, StoredConfig}
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport.OpenSshConfig.Host
import org.eclipse.jgit.transport._
import org.kohsuke.github.{GHEvent, GHHook, GHRepository}

import scala.collection.JavaConverters._

class GithubClient(login: String, oauthToken: String, remote: String, upstream: String) {
  private val github = org.kohsuke.github.GitHub.connect(login, oauthToken)

  def clone(workingDir: File, template: String): GitProject = {
    val remoteRepo: GHRepository = github.getRepository(s"$remote/$template")
    val upstreamRepo = github.getRepository(s"$upstream/$template")
    new GitProject(workingDir, upstreamRepo, remoteRepo)
  }
}

class GitProject(workingDir: File, upstream: GHRepository, remote: GHRepository) {

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

  def addExampleWebHook(): GHHook = {
    val config = Map(
      "url" -> "https://example.lightbend.com/webhook",
      "content_type" -> "json",
      "secret" -> "h0z6a<E;meeYPs:ptueB7Hg09RCbzPG9j;]j>SROKTeV=9>W5i_2BPsiIXzCjIQO"
    )
    val events = Set[GHEvent]()
    upstream.createHook("example-webservice", config.asJava, events.asJava, true)
  }

  def hooks: Seq[GHHook] = {
    upstream.getHooks.asScala
  }

  def pullRequest(localBranchName: String, branchName: String, message: String): Unit = {
    val head = s"${remote.getOwnerName}:$localBranchName"
    val base = branchName

    logger.debug(s"pullRequest: head = $head, base = $base")
    val title = s"Upgrade branch $base using TemplateControl"
    val body = s"""```
                  |$message
                  |```
          """.stripMargin
    upstream.createPullRequest(title, head, base, body)
  }

  private val git: Git = {
    def transportConfigCallback = {
      val sshSessionFactory = new JschConfigSessionFactory() {
        override protected def configure(host: Host, session: Session) = {}
      }

      new TransportConfigCallback() {
        override def configure(transport: Transport) = {
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

  private val config: StoredConfig = git.getRepository.getConfig

  def fetch() = {
    val fetchResult = git.fetch().setRemote("upstream").call()
    fetchResult.getAdvertisedRefs.asScala.foreach { ref =>
      val refUpdate = fetchResult.getTrackingRefUpdate(ref.getName)
      if (refUpdate != null) {
        val result = refUpdate.getResult
        logger.debug(s"fetch: result = $result")
      }
    }
  }

  def pull() = {
    git.pull().setRebase(true).call()
  }

  def rebase(branchName: String): RebaseResult = {
    git.rebase.setUpstream(s"upstream/$branchName").call()
  }

  def branches(): Seq[Ref] = {
    val refs = git.branchList()
      .call()
      .asScala

    for (ref <- refs) {
      logger.debug(s"branches: ref = $ref")
    }
    refs
  }

  def createBranch(branchName: String, startPoint: String): Ref = {
    val ref = git.branchCreate()
      .setForce(true)
      .setName(branchName)
      .setStartPoint(startPoint)
      .call()

    logger.debug(s"createBranch: branchName = $branchName, ref = $ref")

    ref
  }

  // Check out an existing branch.
  def checkout(branchName: String): Ref = {
    val ref = git.checkout()
      .setName(branchName)
      .call()

    logger.debug(s"checkout: branchName = $branchName, ref = $ref")

    ref
  }

  def add(): DirCache = {
    logger.debug(s"add: ")

    git.add()
      .addFilepattern(".")
      .call()
  }

  def commit(message: String): RevCommit = {
    logger.debug(s"commit: $message")

    git.commit()
      .setAll(true)
      .setMessage(message)
      .call()
  }

  def branchCreate(branchName: String): Ref = {
    logger.debug(s"branchCreate: $branchName")

    git.branchCreate()
      .setName(branchName)
      .call()
  }

  def push(name: String): Iterable[PushResult] = {
    logger.debug(s"push: $remote")

    val spec = new RefSpec(s"refs/heads/$name:refs/heads/$name")
    val results = git.push()
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

  def status(): Status = {
    val status = git.status().call()
    logger.debug(s"status: status isClean = ${status.isClean}, hasUncommittedChanges = ${status.hasUncommittedChanges}")

    status
  }

  def log() = {
    val revCommits: Iterable[RevCommit] = git.log().call().asScala

    for (revCommit <- revCommits) {
      logger.info(s"status: message = ${revCommit.getShortMessage}")
    }

    revCommits
  }

  def close(): Unit = git.close()

  private def logging[T <: OperationResult](result: T): T = {
    val messages = result.getMessages
    logger.info(s"messages: $messages")
    result
  }
}
