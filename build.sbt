name := "alpakka-sqs-client"

version := "0.1"

scalaVersion := "2.13.6"

val akkaVersion = "2.5.31"
val akkaHttpVersion = "10.1.11"
val alpakkaAWSVersion = "2.0.2"
val circeVersion = "0.14.1"
val logbackVersion = "1.2.6"
val scalaLoggingVersion = "3.9.4"
val typesafeConfigurationVersion = "1.4.1"

val akka = Seq(
  "com.lightbend.akka" %% "akka-stream-alpakka-sns" % alpakkaAWSVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-sqs" % alpakkaAWSVersion
)

val circe = Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-parser",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-generic-extras",
  "io.circe" %% "circe-optics"
).map(_ % circeVersion withSources() withJavadoc())

val logging = Seq (
  "com.typesafe.scala-logging" %% "scala-logging" % scalaLoggingVersion,
  "ch.qos.logback" % "logback-classic" % logbackVersion
)

libraryDependencies ++= akka ++ circe ++ logging

scalacOptions ++= Seq(
  "-deprecation", // Emit warning and location for usages of deprecated APIs.
  "-encoding", "utf-8", // Specify character encoding used by source files.
  "-explaintypes", // Explain type errors in more detail.
  "-feature", // Emit warning and location for usages of features that should be imported explicitly.
  "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
  "-language:postfixOps",
  "-Ymacro-annotations", // Enable macro annotations
  "-Ybackend-parallelism", "8", // Enable paralellisation — change to desired number!
  "-Ycache-plugin-class-loader:last-modified", // Enables caching of classloaders for compiler plugins
  "-Ycache-macro-class-loader:last-modified" // and macro definitions. This can lead to performance improvements.
)