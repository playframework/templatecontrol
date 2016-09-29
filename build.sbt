scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  // https://mvnrepository.com/artifact/org.eclipse.jgit/org.eclipse.jgit
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.5.0.201609210915-r",
  // https://mvnrepository.com/artifact/ch.qos.logback/logback-classic
  "ch.qos.logback" % "logback-classic" % "1.1.7",

  "com.github.pathikrit" %% "better-files"  % "2.16.0",

  "com.lihaoyi" % "ammonite" % "0.7.7" cross CrossVersion.full
)
