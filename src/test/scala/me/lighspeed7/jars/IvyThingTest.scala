package me.lighspeed7.jars

import org.scalatest.FunSuite

class IvyThingTest extends FunSuite {

  test("Ivy Resolver - Scala Jar") {
    val jar = JarRequest("com.typesafe.akka", "akka-stream_2.12", "2.4.14")
    val data = JarData(jar, 0, Set(jar))
    val deps = fetchDeps(jar)
    val allDeps = collectDeps(Seq(data, data, data))
    print(deps, allDeps)

  }

  test("IvyResolver - Java Jar - No Deps") {
    val jar = JarRequest("com.typesafe", "config", "1.3.0")
    val data = JarData(jar, 0, Set(jar))
    val deps = fetchDeps(jar)
    val allDeps = collectDeps(Seq(data, data, data))
    print(deps, allDeps)

  }

  def fetchDeps(jar: JarRequest) = IvyThing(() => Resolvers.defaultResolvers, new PrintlnPrinter, true).resolveArtifacts(jar)

  def collectDeps(jars: Seq[JarData]) = jars.foldLeft(Set[JarRequest]()) {
    case (p, n) => p ++ n.dependencies
  }

  def print(jars: Seq[JarRequest], deps: Set[JarRequest]) = {
    println(s"JarData    - ${jars.mkString("\n")}")
    println("")
    println(s"AllDeps    - ${deps.mkString("\n")}")
    println("")
  }
}