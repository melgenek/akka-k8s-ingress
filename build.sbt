name := "lovegress"

version := "0.1"

scalaVersion := "2.13.1"

libraryDependencies += "io.fabric8" % "kubernetes-client" % "4.9.0"
libraryDependencies += "org.slf4j" % "slf4j-simple" % "1.7.30"

libraryDependencies += "io.kubernetes" % "client-java" % "5.0.0"

val circeVersion = "0.12.3"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies += "com.typesafe.akka" %% "akka-http" % "10.1.11"
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % "2.6.4"
libraryDependencies += "de.heikoseeberger" %% "akka-http-circe" % "1.32.0"

enablePlugins(AshScriptPlugin)
dockerBaseImage := "openjdk:8-jre-alpine"
dockerExposedPorts := Seq(8080)
dockerUpdateLatest := true
