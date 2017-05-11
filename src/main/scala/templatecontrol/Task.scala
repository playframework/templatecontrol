package templatecontrol

import better.files._
import com.typesafe.config.Config

import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import net.ceedubs.ficus.readers.ValueReader

import scala.concurrent.blocking

trait Task {
  def execute(workingDir: File): Seq[OperationResult]
}

class CopyTask(c: Config) extends Task {

  /**
    * Sets up a path to template mapping
    *
    * @param path the file to search for
    * @param template the template contents to put into the file.
    */
  case class CopyConfig(path: String, template: String) extends OperationConfig

  private val copyConfigs: Seq[CopyConfig] = c.as[Seq[CopyConfig]]("copy")

  private def findTemplate(template: String): File = {
    import java.nio.file.Paths
    val file = File(Paths.get("")) / "templates" / template
    if (file.exists) {
      file
    } else {
      throw new IllegalStateException(s"Cannot find ${template} in ${file.path.toAbsolutePath}")
    }
  }

  def execute(workingDir: File): Seq[OperationResult] = {
    copyConfigs.flatMap { c =>
      val source: File = findTemplate(c.template)
      val dest: File = file"${workingDir.path.toAbsolutePath}${c.path}"
      blocking {
        if (source.isSameContentAs(dest)) {
          None
        } else {
          source.copyTo(dest, overwrite = true)
          val modified = s"wrote ${c.path}"
          Some(OperationResult(c, modified))
        }

      }
    }
  }
}

class FindReplaceTask(c: Config) extends Task {

  /**
    * Sets up a file finder glob to text search mappings.
    *
    * @param path     a glob pattern from http://docs.oracle.com/javase/tutorial/essential/io/fileOps.html#glob
    * @param conversions a mapping from a search substring to the text line conversion.
    */
  case class FinderConfig(path: String, conversions: Map[String, String]) extends OperationConfig

  implicit val finderConfigReader: ValueReader[FinderConfig] = ValueReader.relative { c =>
    FinderConfig(
      path = c.as[String]("path"),
      conversions = c.as[Map[String, String]]("conversions")
    )
  }

  private val finderConfigs: Seq[FinderConfig] = c.as[Seq[FinderConfig]]("finders")

   def execute(workingDir: File): Seq[OperationResult] = {
    finderConfigs.flatMap { finderConfig =>
      workingDir.glob(finderConfig.path).flatMap { file =>
        val tempFile = file.parent / s"${file.name}.tmp"
        val replaceFunctions = finderConfig.conversions.map {
          case (k, v) =>
            (s: String) =>
              k.r.findFirstIn(s) match {
                case Some(_) => v
                case None => s
              }
        }
        val results = file.lines.flatMap { line =>
          val modified = replaceFunctions.foldLeft(line)((acc, f) => f(acc))
          tempFile.append(modified)
          if (line.equals(modified)) {
            None
          } else {
            Some(OperationResult(finderConfig, modified))
          }
        }
        file.delete()
        tempFile.renameTo(file.name)
        results
      }
    }
  }
}