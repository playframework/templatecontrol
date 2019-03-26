package templatecontrol

import better.files.File
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader

/**
 * Sets up a map between the branch and the file search and replace operations specific to that branch.
 *
 * We assume the mapping is specific to the branch, and not to the individual template.
 *
 * @param name    the branch name ("2.7.x")
 */
final case class BranchConfig(name: String, config: Config)

final case class GithubConfig(
    upstream: String,
    remote: String,
    credentials: GithubCredentialsConfig,
)

final case class GithubCredentialsConfig(user: String, oauthToken: String)

/**
 * Overall config containing all the templates and the branch mappings applicable to all.
 *
 * @param baseDirectory   the directory to clone templates in
 * @param github          github config
 * @param branchConfigs   set of branches
 */
final case class TemplateControlConfig(
    baseDirectory: File,
    github: GithubConfig,
    branchConfigs: Seq[BranchConfig],
    noPush: Boolean = false,
) {
  def branchConfigFor(branchName: String): BranchConfig = {
    branchConfigs
      .find(_.name == branchName)
      .getOrElse(BranchConfig(branchName, ConfigFactory.parseString("{ copy = [], finders = [] }")))
  }
}

object TemplateControlConfig {

  implicit val branchConfigReader: ValueReader[BranchConfig] = ValueReader.relative { c =>
    BranchConfig(
      name = c.as[String]("name"),
      config = c,
    )
  }

  def fromTypesafeConfig(config: Config): TemplateControlConfig = {
    import better.files._

    val tc = config.as[Config]("templatecontrol")
    TemplateControlConfig(
      tc.as[String]("baseDirectory").toFile,
      tc.as[GithubConfig]("github"),
      tc.as[Seq[BranchConfig]]("branches"),
    )
  }

}
