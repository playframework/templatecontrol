package templatecontrol.model

// Keep the templates list in sync with ./scripts/templates-play.sh !

object Play {
  def play26 = Project("Play", "2.6.x", templates.diff(templates27).map(mkTemplate))
  def play27 = Project("Play", "2.7.x", templates.map(mkTemplate))

  private def mkTemplate(name: String) = Template(name, "playframework")

  private def templates = Seq(
    "play-java-seed.g8",
    "play-java-starter-example",
    "play-java-hello-world-tutorial",
    "play-java-chatroom-example",
    "play-java-compile-di-example",
    "play-java-dagger2-example",
    "play-java-ebean-example",
    "play-java-fileupload-example",
    "play-java-forms-example",
    "play-java-grpc-example",
    "play-java-jpa-example",
    "play-java-rest-api-example",
    "play-java-streaming-example",
    "play-java-websocket-example",
    "play-scala-seed.g8",
    "play-scala-starter-example",
    "play-scala-hello-world-tutorial",
    "play-scala-anorm-example",
    "play-scala-chatroom-example",
    "play-scala-compile-di-example",
    "play-scala-fileupload-example",
    "play-scala-forms-example",
    "play-scala-grpc-example",
    "play-scala-isolated-slick-example",
    "play-scala-log4j2-example",
    "play-scala-macwire-di-example",
    "play-scala-rest-api-example",
    "play-scala-secure-session-example",
    "play-scala-slick-example",
    "play-scala-streaming-example",
    "play-scala-tls-example",
    "play-scala-websocket-example",
    "play-webgoat",
  )

  private def templates27 = Seq(
    "play-scala-grpc-example",
    "play-java-grpc-example",
  )
}
