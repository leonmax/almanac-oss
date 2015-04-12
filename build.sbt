import sbt._
import Keys._

organization := "com.adcade"
name := "almanac-spark"
version := "0.0.1-SNAPSHOT"
scalaVersion := "2.11.5"

libraryDependencies ++= {
  val configVersion    = "1.2.1"
  val akkaVersion      = "2.3.9"
  val logbackVersion   = "1.0.13"
  val sprayVersion     = "1.3.2"

  Seq(
    "com.typesafe"      %  "config"                   % configVersion,
    "com.typesafe.akka" %% "akka-actor"               % akkaVersion exclude ("org.scala-lang" , "scala-library"),
    "com.typesafe.akka" %% "akka-slf4j"               % akkaVersion exclude ("org.slf4j", "slf4j-api") exclude ("org.scala-lang" , "scala-library"),
    "io.spray"          %% "spray-can"                % sprayVersion,
    "io.spray"          %% "spray-json"               % sprayVersion,
    "io.spray"          %% "spray-routing"            % sprayVersion,
    "ch.qos.logback"    %  "logback-classic"          % logbackVersion,
    "io.spray"          %% "spray-testkit"            % sprayVersion % Test,
    "com.typesafe.akka" %% "akka-testkit"             % akkaVersion  % Test,
    "org.specs2"        %% "specs2-core"              % "2.4.15"     % Test,
    "org.scalamock"     %% "scalamock-specs2-support" % "3.2.1"      % Test exclude("org.specs2", "specs2"),
  )
}

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Xlint",
  "-Ywarn-dead-code",
  "-language:_",
  "-target:jvm-1.7",
  "-encoding", "UTF-8"
)

crossPaths := false

parallelExecution in Test := false

assemblyJarName in assembly := "almanac-spark.jar"

assemblyMergeStrategy in assembly := {
  case x if x.contains("default.properties") => MergeStrategy.concat
  case x if x.contains("adcade.properties") => MergeStrategy.discard
  case x if x.contains("pom.properties") => MergeStrategy.discard
  case x if x.contains("pom.xml") => MergeStrategy.discard
  case x if x.contains("logback.xml")        => MergeStrategy.discard
  case x if x.contains("META-INF/javamail") => MergeStrategy.last
  case PathList("META-INF", "MANIFEST.MF") => MergeStrategy.discard
  case PathList("META-INF", "mailcap") => MergeStrategy.last
  case x if x.endsWith("spring.tooling") => MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}