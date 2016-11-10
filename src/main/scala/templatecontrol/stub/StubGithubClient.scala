package templatecontrol.stub

import better.files.File
import org.kohsuke.github.GHRepository
import templatecontrol.{GitProject, GithubClient}

class StubGithubClient(login: String, oauthToken: String, remote: String, upstream: String) extends GithubClient {
  private val github = org.kohsuke.github.GitHub.connect(login, oauthToken)

  def clone(workingDir: File, template: String): GitProject = {
    val remoteRepo: GHRepository = github.getRepository(s"$remote/$template")
    val upstreamRepo = github.getRepository(s"$upstream/$template")
    new StubGitProject(workingDir, upstreamRepo, remoteRepo)
  }
}

