scalaVersion := "2.11.8"

// For Ficus
resolvers += Resolver.jcenterRepo

// https://github.com/typesafehub/config
libraryDependencies +="com.typesafe" % "config" % "1.3.1"

// https://github.com/iheartradio/ficus
libraryDependencies +="com.iheart" %% "ficus" % "1.2.7"

// https://github.com/qos-ch/logback
libraryDependencies +="ch.qos.logback" % "logback-classic" % "1.1.7"

// https://github.com/pathikrit/better-files
libraryDependencies +="com.github.pathikrit" %% "better-files"  % "2.16.0"

// https://eclipse.org/jgit/
libraryDependencies +=  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.5.0.201609210915-r"

 // https://github.com/kohsuke/github-api
libraryDependencies += "org.kohsuke" % "github-api" % "1.77"

// http://www.scalactic.org/user_guide
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test"

libraryDependencies += "com.typesafe.play" %% "play-ws" % "2.5.9"

// http://software.clapper.org/scalasti/
libraryDependencies += "org.clapper" %% "scalasti" % "2.1.2"