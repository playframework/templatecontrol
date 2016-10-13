import sbt.Keys._
import sbt._
import sbt.plugins.JvmPlugin

/**
 * Settings that are comment to all the SBT projects
 */
object Common extends AutoPlugin {
  override def trigger = allRequirements
  override def requires: sbt.Plugins = JvmPlugin

  override def projectSettings = Seq(
    javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
    scalacOptions ++= Seq(
      "-encoding",
      "UTF-8", // yes, this is 2 args
      "-target:jvm-1.8",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xlint",
      "-Yno-adapted-args",
      "-Ywarn-numeric-widen",
      "-Xfatal-warnings"
    ),
    scalacOptions in Test ++= Seq("-Yrangepos"),
    autoAPIMappings := true
  )
}