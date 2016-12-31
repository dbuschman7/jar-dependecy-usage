name := "validator"
version := "0.1"
scalaVersion := "2.12.1"
resolvers += Resolver.typesafeRepo("releases")

lazy val akkaVersion = "2.4.14"
lazy val akkaStreams = "com.typesafe.akka" %% "akka-stream" % akkaVersion

libraryDependencies += akkaStreams




