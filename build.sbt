scalaVersion := "2.11.8"

// For Ficus
resolvers += Resolver.jcenterRepo

libraryDependencies ++= Seq(
  // https://github.com/typesafehub/config
  "com.typesafe" % "config" % "1.3.1",

  // https://github.com/iheartradio/ficus
  "com.iheart" %% "ficus" % "1.2.7",

  // https://github.com/qos-ch/logback
  "ch.qos.logback" % "logback-classic" % "1.1.7",

  // https://github.com/pathikrit/better-files
  "com.github.pathikrit" %% "better-files"  % "2.16.0",

  // https://eclipse.org/jgit/
  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.5.0.201609210915-r",

  // https://github.com/kohsuke/github-api
  "org.kohsuke" % "github-api" % "1.77"
)

