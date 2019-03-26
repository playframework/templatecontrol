package templatecontrol.live

import better.files._
import org.kohsuke.github.GHRepository
import org.kohsuke.github.extras.OkHttpConnector
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.OkUrlFactory
import templatecontrol.GitProject
import templatecontrol.GithubClient

class LiveGithubClient(login: String, oauthToken: String, remote: String, upstream: String)
    extends GithubClient {
  private val cacheDirectory = new java.io.File("target/github_cache")
  private val cache          = new com.squareup.okhttp.Cache(cacheDirectory, 10 * 1024 * 1024); // 10MB cache
  private val github = org.kohsuke.github.GitHubBuilder
    .fromEnvironment()
    .withOAuthToken(oauthToken, login)
    .withConnector(new OkHttpConnector(new OkUrlFactory(new OkHttpClient().setCache(cache))))
    .build()

  def clone(workingDir: File, template: String): GitProject = {
    val remoteRepo: GHRepository = github.getRepository(s"$remote/$template")
    val upstreamRepo             = github.getRepository(s"$upstream/$template")
    new LiveGitProject(workingDir, upstreamRepo, remoteRepo)
  }
}
