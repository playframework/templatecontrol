package templatecontrol.model

object Lagom {
  def lagom14 = Project("Lagom", "1.4.x", templates.diff(templates15).map(mkTemplate))
  def lagom15 = Project("Lagom", "1.5.x", templates.map(mkTemplate))

  private def mkTemplate(name: String) = Template(name, "lagom")

  // Keep the templates list in sync with ./scripts/templates-lagom.sh !

  // all templates
  private def templates = Seq(
    "lagom-java.g8",
    "lagom-scala.g8",
    "online-auction-java",
    "online-auction-scala",
    "lagom-java-maven-chirper-example",
    "lagom-java-sbt-chirper-example",
    "lagom-java-grpc-example",
    "lagom-scala-grpc-example",
    "lagom-scala-openshift-smoketests",
    "lagom-java-openshift-smoketests",
    "shopping-cart-scala",
    "shopping-cart-java",
  )

  // Lagom 1.5.x only templates
  private def templates15 = Seq(
    "lagom-java-grpc-example",
    "lagom-scala-grpc-example",
    "lagom-scala-openshift-smoketests",
    "lagom-java-openshift-smoketests",
  )
}
