package templatecontrol.model

object Lagom {

  private val lagomOrg = "lagom"

  private val templates = Seq(
    Template("online-auction-scala", lagomOrg),
    Template("online-auction-java", lagomOrg),
    Template("lagom-java.g8", lagomOrg),
    Template("lagom-scala.g8", lagomOrg),
    Template("lagom-java-sbt-chirper-example", lagomOrg),
    Template("lagom-java-maven-chirper-example", lagomOrg)
  )

  private val templates15 = Seq(
    Template("lagom-scala-grpc-example", lagomOrg),
    Template("lagom-java-grpc-example", lagomOrg),
  )

  val lagom15 = Project("Lagom", "1.5.x", templates ++ templates15)
  val lagom14 = Project("Lagom", "1.4.x", templates)
}
