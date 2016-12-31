name := "jar-dependency-usage"
version := "1.0"
scalaVersion := "2.12.1"

resolvers += Resolver.typesafeRepo("releases")


lazy val akkaVersion = "2.4.14"

lazy val akkaStreams = "com.typesafe.akka" %% "akka-stream" % akkaVersion
lazy val akkaTestKit =  "com.typesafe.akka" %% "akka-testkit" % akkaVersion

lazy val apacheIvy   =  "org.apache.ivy" % "ivy" % "2.4.0"
lazy val jodaTime    = "joda-time" % "joda-time" % "2.9.6"

lazy val scalaXml    = "org.scala-lang.modules" %% "scala-xml" % "1.0.6"
lazy val scalaTest   = "org.scalatest" %% "scalatest" % "3.0.1" 

libraryDependencies ++= Seq(akkaStreams, akkaTestKit % "test", apacheIvy, jodaTime, scalaTest % "test", scalaXml)

