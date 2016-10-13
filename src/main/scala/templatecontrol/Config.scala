package templatecontrol

import better.files.File
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader

/**
 * Sets up a file finder glob to text search mappings.
 *
 * @param pattern     a glob pattern from http://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob
 * @param conversions a mapping from a search substring to the text line conversion.
 */
case class FinderConfig(pattern: String, conversions: Map[String, String])

/**
 * Sets up a map between the branch and the file search and replace operations specific to that branch.
 *
 * We assume the mapping is specific to the branch, and not to the individual template.
 *
 * @param name    the branch name ("2.5.x")
 * @param finders the set of file finders
 */
case class BranchConfig(name: String, finders: Seq[FinderConfig])

case class GithubConfig(upstream: String, remote: String, credentials: GithubCredentialsConfig, webhook: Map[String, String])

case class GithubCredentialsConfig(user: String, oauthToken: String)

/**
 * Overall config containing all the templates and the branch mappings applicable to all.
 *
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
      finders = c.as[Seq[FinderConfig]]("finders")
    )
  }

  implicit val finderConfigReader: ValueReader[FinderConfig] = ValueReader.relative { c =>
    FinderConfig(
      pattern = c.as[String]("pattern"),
      conversions = c.as[Map[String, String]]("conversions")
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
