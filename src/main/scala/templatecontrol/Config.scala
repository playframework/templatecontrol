package templatecontrol

import better.files.File
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader

trait OperationConfig {
  def path: String
}

/**
 * Sets up a map between the branch and the file search and replace operations specific to that branch.
 *
 * We assume the mapping is specific to the branch, and not to the individual template.
 *
 * @param name    the branch name ("2.5.x")
 */
case class BranchConfig(name: String, config: Config)

case class GithubConfig(upstream: String, remote: String, credentials: GithubCredentialsConfig, webhook: GithubWebhookConfig)

case class GithubWebhookConfig(name: String, config: Map[String, String])

case class GithubCredentialsConfig(user: String, oauthToken: String)

/**
 * Overall config containing all the templates and the branch mappings applicable to all.
 *
 * @param baseDirectory   the directory to clone templates in
 * @param github github config
 * @param templates set of templates
 * @param branchConfigs   set of branches
 */
case class TemplateControlConfig(baseDirectory: File,
                                 github: GithubConfig,
                                 templates: Seq[String],
                                 branchConfigs: Seq[BranchConfig])

object TemplateControlConfig {

  implicit val branchConfigReader: ValueReader[BranchConfig] = ValueReader.relative { c =>
    BranchConfig(
      name = c.as[String]("name"),
      config = c
    )
  }

  def fromTypesafeConfig(config: Config): TemplateControlConfig = {
    import better.files._

    val tc = config.as[Config]("templatecontrol")
    TemplateControlConfig(
      tc.as[String]("baseDirectory").toFile,
      tc.as[GithubConfig]("github"),
      tc.as[Seq[String]]("templates"),
      tc.as[Seq[BranchConfig]]("branches")
    )
  }

}
