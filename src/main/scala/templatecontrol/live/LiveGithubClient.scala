package templatecontrol.live

import better.files._
import org.kohsuke.github.GHRepository
import templatecontrol.{GitProject, GithubClient}

class LiveGithubClient(login: String, oauthToken: String, remote: String, upstream: String) extends GithubClient {
  private val github = org.kohsuke.github.GitHub.connect(login, oauthToken)

  def clone(workingDir: File, template: String): GitProject = {
    val remoteRepo: GHRepository = github.getRepository(s"$remote/$template")
    val upstreamRepo = github.getRepository(s"$upstream/$template")
    new LiveGitProject(workingDir, upstreamRepo, remoteRepo)
  }
}

