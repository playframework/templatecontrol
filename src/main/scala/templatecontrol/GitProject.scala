package templatecontrol

import better.files._
import org.eclipse.jgit.api._
import org.eclipse.jgit.dircache.DirCache
import org.eclipse.jgit.lib.Ref
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.transport._
import org.kohsuke.github.GHHook


trait GithubClient {
  def clone(workingDir: File, template: String): GitProject
}

trait GitProject {
  def addWebhook(name: String, config: Map[String, String]): GHHook
  def hooks: Seq[GHHook]
  def pullRequest(localBranchName: String, branchName: String, message: String): Unit
  def fetch(): Unit
  def pull(): PullResult
  def rebase(branchName: String): RebaseResult
  def branches(): Seq[Ref]
  def createBranch(branchName: String, startPoint: String): Ref
  def checkout(branchName: String): Ref
  def add(): DirCache
  def commit(message: String): RevCommit
  def branchCreate(branchName: String): Ref
  def push(name: String, force: Boolean = false): Iterable[PushResult]
  def status(): Status
  def log(): Iterable[RevCommit]
  def close(): Unit
}
