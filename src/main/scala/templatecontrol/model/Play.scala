package templatecontrol.model

// Keep the templates list in sync with ./scripts/templates-play.sh !

object Play {
  def play26 = Project("Play", "2.6.x", templates)
  def play27 = Project("Play", "2.7.x", templates)

  def templates = Seq("play-java-seed.g8", "play-scala-seed.g8", "play-webgoat").map(mkTemplate)

  private def mkTemplate(name: String) = Template(name, "playframework")
}
