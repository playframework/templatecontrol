package templatecontrol

import play.api.libs.json._
import play.api.libs.ws.ahc.StandaloneAhcWSClient
import play.api.libs.ws.JsonBodyReadables

/**
 * A WS client that downloads the list of available projects
 */
class ExampleCodeClient(exampleCodeServiceUrl: java.net.URL) extends JsonBodyReadables {
  import akka.actor.ActorSystem
  import akka.stream.ActorMaterializer
  import scala.concurrent.Future

  private implicit val system       = ActorSystem()
  private implicit val materializer = ActorMaterializer()

  private implicit val ec = system.dispatchers.defaultGlobalDispatcher
  private val wsClient    = StandaloneAhcWSClient()

  implicit val projectTemplateReads: Reads[ProjectTemplate] = Json.reads[ProjectTemplate]

  def call(keywords: Seq[String]): Future[Seq[ProjectTemplate]] = {
    val keywordParams = keywords.map("keyword" -> _)
    wsClient
      .url(exampleCodeServiceUrl.toString)
      .withQueryStringParameters(keywordParams: _*)
      .get()
      .map { response =>
        Json.fromJson[Seq[ProjectTemplate]](response.body[JsValue]) match {
          case JsSuccess(value, _) =>
            value
          case JsError(e) =>
            println(s"Cannot parse from code service: error $e")
            Seq.empty
        }
      }
      .map { templates =>
        templates.filter(_.keywords.contains("play"))
      }
  }

  def close(): Unit = {
    wsClient.close()
    system.terminate()
  }
}

case class ProjectTemplate(
    displayName: String,
    templateName: String,
    gitHubRepo: String,
    gitHubUrl: String,
    keywords: Seq[String],
)
