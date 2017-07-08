scalaVersion := "2.12.2"

// (╯°□°）╯︵ ┻━┻ https://github.com/scala/bug/issues/10270#issuecomment-295338382
scalacOptions := {
  val orig = scalacOptions.value
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, 12)) => orig.map {
      case "-Xlint"               => "-Xlint:-unused,_"
      case "-Ywarn-unused-import" => "-Ywarn-unused:imports,-patvars,-privates,-locals,-params,-implicits"
      case other                  => other
    }
    case _             => orig
  }
}

// For Ficus
resolvers += Resolver.jcenterRepo

// https://github.com/typesafehub/config
libraryDependencies +="com.typesafe" % "config" % "1.3.1"

// https://github.com/iheartradio/ficus
libraryDependencies +="com.iheart" %% "ficus" % "1.4.0"

// https://github.com/qos-ch/logback
libraryDependencies +="ch.qos.logback" % "logback-classic" % "1.2.3"

// https://github.com/pathikrit/better-files
libraryDependencies +="com.github.pathikrit" %% "better-files"  % "3.0.0"

// https://eclipse.org/jgit/
libraryDependencies +=  "org.eclipse.jgit" % "org.eclipse.jgit" % "4.7.0.201704051617-r"

// hook into ssh-agent
// https://github.com/ymnk/jsch-agent-proxy
libraryDependencies += "com.jcraft" % "jsch.agentproxy.sshagent" % "0.0.9"
libraryDependencies += "com.jcraft" % "jsch.agentproxy.usocket-jna" % "0.0.9"
libraryDependencies += "com.jcraft" % "jsch.agentproxy.jsch" % "0.0.9"

// https://github.com/kohsuke/github-api
libraryDependencies += "org.kohsuke" % "github-api" % "1.85"

// http://www.scalactic.org/user_guide
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test"

libraryDependencies += "com.typesafe.play" %% "play-ahc-ws-standalone" % "1.0.0"

libraryDependencies += "com.typesafe.play" %% "play-ws-standalone-json" % "1.0.0"
