scalaVersion := "2.12.3"

// addSbtPlugin("org.bytedeco" % "sbt-javacv" % "1.16")

scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-language:postfixOps",
  "-Xfatal-warnings",
  "-Ypartial-unification"
  //other options
)

libraryDependencies ++= Seq(
  "com.pi4j" % "pi4j-core" % "1.2",
  "ch.qos.logback" % "logback-classic" % "1.2.3",
  "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2",
  "com.typesafe.akka" %% "akka-http" % "10.2.1", // % "10.1.10",
  "com.typesafe.akka" %% "akka-stream" % "2.6.10", // "2.5.26",
  "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.5", //  % "1.2.2",
  "com.typesafe.akka" %% "akka-http-spray-json" % "10.2.1" // "10.1.11"
)

mainClass in assembly := Some("de.robs.SmartHomeServer")


