scalaVersion := "2.12.8"

scalacOptions ++= Seq(
  "-encoding",
  "UTF-8",
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint:-unused,_",
  "-Yno-adapted-args",
  "-Ywarn-numeric-widen",
)

libraryDependencies += "com.github.pathikrit" %% "better-files"            % "3.4.0"
libraryDependencies += "com.iheart"           %% "ficus"                   % "1.4.0"
libraryDependencies += "com.jcraft"            % "jsch"                    % "0.1.53"
libraryDependencies += "com.typesafe"          % "config"                  % "1.3.1"
libraryDependencies += "com.typesafe.akka"    %% "akka-actor"              % "2.5.3"
libraryDependencies += "com.typesafe.akka"    %% "akka-stream"             % "2.5.3"
libraryDependencies += "com.typesafe.play"    %% "play-ahc-ws-standalone"  % "1.0.0"
libraryDependencies += "com.typesafe.play"    %% "play-ws-standalone-json" % "1.0.0"
libraryDependencies += "com.typesafe.play"    %% "play-functional"         % "2.6.0"
libraryDependencies += "com.typesafe.play"    %% "play-json"               % "2.6.0"
libraryDependencies += "com.typesafe.play"    %% "play-ws-standalone"      % "1.0.0"
libraryDependencies += "org.eclipse.jgit"      % "org.eclipse.jgit"        % "4.5.0.201609210915-r"
libraryDependencies += "org.kohsuke"           % "github-api"              % "1.77"
libraryDependencies += "org.slf4j"             % "slf4j-api"               % "1.7.25"
