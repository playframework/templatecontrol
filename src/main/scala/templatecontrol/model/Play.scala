package templatecontrol.model

object Play {

  private val playOrg = "playframework"

  private val templates = Seq(

    Template("play-java-hello-world-tutorial", playOrg),
    Template("play-java-starter-example", playOrg),
    Template("play-java-chatroom-example", playOrg),
    Template("play-java-compile-di-example", playOrg),
    Template("play-java-dagger2-example", playOrg),
    Template("play-java-ebean-example", playOrg),
    Template("play-java-fileupload-example", playOrg),
    Template("play-java-forms-example", playOrg),
    Template("play-java-jpa-example", playOrg),
    Template("play-java-rest-api-example", playOrg),
    Template("play-java-streaming-example", playOrg),
    Template("play-java-websocket-example", playOrg),

    Template("play-scala-starter-example", playOrg),
    Template("play-scala-hello-world-tutorial", playOrg),
    Template("play-scala-anorm-example", playOrg),
    Template("play-scala-chatroom-example", playOrg),
    Template("play-scala-compile-di-example", playOrg),
    Template("play-scala-fileupload-example", playOrg),
    Template("play-scala-forms-example", playOrg),
    Template("play-scala-isolated-slick-example", playOrg),
    Template("play-scala-log4j2-example", playOrg),
    Template("play-scala-macwire-di-example", playOrg),
    Template("play-scala-rest-api-example", playOrg),
    Template("play-scala-secure-session-example", playOrg),
    Template("play-scala-slick-example", playOrg),
    Template("play-scala-streaming-example", playOrg),
    Template("play-scala-tls-example", playOrg),
    Template("play-scala-websocket-example", playOrg),

    Template("play-java-seed.g8", playOrg),
    Template("play-scala-seed.g8", playOrg),

    Template("play-webgoat", playOrg),
  )

  private val templates27Only = Seq(
    Template("play-scala-grpc-example", playOrg),
    Template("play-java-grpc-example", playOrg),
  )

  val play27 = Project("Play", "2.7.x", templates ++ templates27Only)
  val play26 = Project("Play", "2.6.x", templates)
}
