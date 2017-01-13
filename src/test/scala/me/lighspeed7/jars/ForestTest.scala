package me.lighspeed7.jars

import org.scalatest.FunSuite
import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import org.scalatest.BeforeAndAfterAll
import scala.concurrent.Await
import akka.actor.Props
import akka.testkit._
import org.scalatest.Matchers
import org.scalatest.FunSuiteLike

class ForestTest extends TestKit(ActorSystem("MySpec")) with ImplicitSender
    with Matchers with FunSuiteLike with BeforeAndAfterAll {

  override def afterAll = TestKit.shutdownActorSystem(system)

  def computeDependencyList(jar: JarRequest): DependencyList = {
    val forest = TestActorRef[JarTraversalActor](JarTraversalActor.props(Resolvers.defaultResolvers, jar), name = s"Traversal-${jar.toIdString}")
    val actor = forest.underlyingActor

    do { // wait for completion
      Thread.sleep(200)
      val children = actor.children.map(_.path.name).mkString(",")
      //      println(s"Child thread count - ${actor.children.size} - ${children}")
    } while (actor.children.size > 0)

    forest ! CacheRequest

    expectMsgType[DependencyList](60 seconds)
  }

  test("Lookup Jar") {

    val akkaStream = JarRequest("com.typesafe.akka", "akka-stream_2.12", "2.4.14")
    val play25 = JarRequest("com.typesafe.play", "play_2.12", "2.5.10")
    val jackson = JarRequest("com.fasterxml.jackson.datatype", "jackson-datatype-jdk8", "2.7.8")
    val scalaz = JarRequest("org.scalaz", "scalaz-core_2.12", "7.2.8")
    val cats = JarRequest("org.typelevel", "cats_2.12", "0.8.1")
    val jsoniter = JarRequest("com.jsoniter", "jsoniter", "0.9.4")

    val playJson = JarRequest("com.typesafe.play", "play-json_2.11", "2.5.10")
    val sprayJson = JarRequest("io.spray", "spray-json_2.12", "1.3.2")
    val akkaHttpSprayJson = JarRequest("com.typesafe.akka", "akka-http-spray-json_2.12", "10.0.1")

    //    Seq(akkaStream, play25, jackson, scalaz, playJson, sprayJson, akkaHttpSprayJson)
    //      .map { jar =>
    //        val deps = computeDependencyList(jar)
    //        println(deps.dumpFormatted().mkString("\n", "\n", "\n"))
    //      }

    Seq(cats, jsoniter)
      .map { jar =>
        val deps = computeDependencyList(jar)
        println(deps.dumpFormatted().mkString("\n", "\n", "\n"))
      }
  }
}