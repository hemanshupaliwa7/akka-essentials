val akkaVersion = "2.5.13"
val akkaTypedVersion = "2.6.18"
val scalaTestVersion = "3.0.5"
val scalaCompilerVersion = "2.12.7"

lazy val root = project
  .in(file("."))
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % akkaVersion,
      "com.typesafe.akka" %% "akka-testkit" % akkaVersion,
      "org.scalatest" %% "scalatest" % scalaTestVersion,
    ),
    scalaVersion := scalaCompilerVersion,
    version := "0.1",
    name := "akka-essentials"
  )

lazy val `akka-typed` = project
  .in(file("akka-typed"))
  .settings(
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % akkaTypedVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaTypedVersion,
      "org.scalatest" %% "scalatest" % "3.1.4"
    ),
    scalaVersion := scalaCompilerVersion,
    version := "0.1",
    name := "akka-typed-essentials"
  )