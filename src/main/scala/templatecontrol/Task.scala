package templatecontrol

import scala.concurrent.blocking

import better.files._
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader
import org.slf4j.LoggerFactory

trait TaskConfig {
  def path: String
}

final case class TaskResult(config: TaskConfig, modified: String)

trait Task {
  def execute(path: File): Seq[TaskResult]
}

object Task {
  def apply(f: File => Seq[TaskResult]): Task = (path: File) => f.apply(path)
}

/**
 * Sets up a path to template mapping
 *
 * @param path the file to search for
 * @param template the template contents to put into the file.
 */
final case class CopyConfig(path: String, template: String) extends TaskConfig

final class CopyTask(c: Config) extends Task {
  private val copyConfigs: Seq[CopyConfig] = c.as[Seq[CopyConfig]]("copy")

  private def findTemplate(template: String): File = {
    import java.nio.file.Paths
    val file = File(Paths.get("")) / "templates" / template
    if (file.exists) {
      file
    } else {
      throw new IllegalStateException(s"Cannot find $template in ${file.path.toAbsolutePath}")
    }
  }

  def execute(workingDir: File): Seq[TaskResult] = {
    copyConfigs.flatMap { c =>
      val source: File = findTemplate(c.template)
      val dest: File = file"${workingDir.path.toAbsolutePath}${c.path}"
      blocking {

        // create parent directory if non-existent
        dest.parent.createDirectories()

        // create file if non-existent
        if (dest.notExists) dest.createFile()

        if (source.isSameContentAs(dest)) {
          None
        } else {
          source.copyTo(dest, overwrite = true)
          val modified = s"wrote ${c.path}"
          Some(TaskResult(c, modified))
        }
      }
    }
  }
}

/**
 * Sets up a file finder glob to text search mappings.
 *
 * @param path     a glob pattern from http://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob
 * @param conversions a mapping from a search substring to the text line conversion.
 */
final case class FinderConfig(path: String, conversions: Map[String, String]) extends TaskConfig

final class FindReplaceTask(c: Config) extends Task {
  private val logger = LoggerFactory.getLogger(getClass)

  implicit val finderConfigReader: ValueReader[FinderConfig] = ValueReader.relative { c =>
    FinderConfig(
      path = c.as[String]("path"),
      conversions = c.as[Map[String, String]]("conversions")
    )
  }

  private val finderConfigs: Seq[FinderConfig] = c.as[Seq[FinderConfig]]("finders")

   def execute(workingDir: File): Seq[TaskResult] = {
    finderConfigs.flatMap { finderConfig =>
      workingDir.glob(finderConfig.path).flatMap { file =>
        val tempFile = file.parent / s"${file.name}.tmp"

        logger.info(s"The following substitutions will be made: ${finderConfig.conversions} for file $file")
        val replaceFunctions = finderConfig.conversions.map {
          case (k, v) =>
            (s: String) =>
              k.r.findFirstIn(s) match {
                // keep line as is if it contains tc-skip (template control skip)
                // that's particularly useful for giter8 templates that contains variables
                case Some(_) if s.contains("tc-skip") => s
                case Some(_) => v
                case None => s
              }
        }
        val results = file.lines.flatMap { line =>
          import better.files.Dsl.SymbolicOperations

          val modified = replaceFunctions.foldLeft(line)((acc, f) => f(acc))
          modified >>: tempFile
          if (line.equals(modified)) {
            None
          } else {
            Some(TaskResult(finderConfig, modified))
          }
        }
        // Preserve file permissions
        tempFile.setPermissions(file.permissions)

        file.delete()
        tempFile.renameTo(file.name)
        results
      }
    }
  }
}
