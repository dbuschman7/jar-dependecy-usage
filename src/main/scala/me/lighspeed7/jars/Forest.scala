package me.lighspeed7.jars

import java.io.File

import scala.collection.mutable
import scala.concurrent.duration._

import akka.actor.{ Actor, ActorRef, Props }
import akka.actor.ReceiveTimeout

case class IvyFetch(jar: JarRequest)
case class FetchCompleted(jar: JarRequest, dependencies: Int)
case object CacheRequest

//
class FetchIvyDepsActor(resolvers: List[Resolver]) extends Actor {

  def receive = {
    case IvyFetch(jar) =>
      val messages = new CollectionPrinter()
      val deps = IvyThing(() => resolvers, messages, true).resolveArtifacts(jar)
      deps.map { jar => sender ! jar }
      sender ! messages
      sender ! JarData(jar, 0, deps.toSet)
      sender ! FetchCompleted(jar, deps.size)
      context.stop(self)
  }
}
object FetchIvyDepsActor {
  def props(resolvers: List[Resolver]): Props = Props(new FetchIvyDepsActor(resolvers))
}

//
class FileSizeLookup extends Actor {

  def receive = {
    case jar: JarRequest => {
      val homeRaw = Option(sys.env("HOME")).getOrElse("~")
      val home = new File(homeRaw).getCanonicalFile
      val ivyCacheBase = new File(home, ".ivy2/cache/")
      val file = new File(ivyCacheBase, jar.ivyJarPath)
      val bundleFile = new File(ivyCacheBase, jar.ivyBundlePath)
      println(s"Looking up ${file.getCanonicalPath}")
      val size = file.exists() match {
        case true => file.length
        case false =>
          println(s"Looking up ${bundleFile.getCanonicalPath}")
          bundleFile.exists match {
            case true  => bundleFile.length
            case false => 0
          }
      }
      sender ! JarData(jar, size)
      context.stop(self)
    }
  }
}
object FileSizeLookup {
  def props: Props = Props(new FileSizeLookup)

}

//
class JarTraversalActor(resolvers: List[Resolver], topJar: JarRequest) extends Actor {

  // data 
  val cache = mutable.HashMap.empty[JarRequest, JarData]
  val messages = mutable.ListBuffer.empty[String]
  var children = Set.empty[ActorRef]

  // startup
  self ! IvyFetch(topJar)
  context.setReceiveTimeout(60 seconds)

  //
  // Helpers
  // ///////////////////////////////
  def updateCache(data: JarData): Boolean = {
    val result: Boolean = cache.get(data.jar) match {
      case Some(jData) =>
        cache += (data.jar -> data.merge(jData))
        false
      case None => {
        val versions = cache.keySet.filter(_.groupArtifact == data.jar.groupArtifact)
        val newerVersions = versions.filter(_.version > data.jar.version)
        newerVersions.size match {
          case 0 =>
            cache += (data.jar -> data) // this is the only version
            true
          case _ => { // remove older versions, add ourselves if no newer versions
            val removeVersions = (versions -- newerVersions)
            cache --= removeVersions
            if (newerVersions.size == 0) {
              cache += (data.jar -> data)
              true
            } else false
          }
        }
      }
    }
    //    println(s"Cache --- size = ${cache.size} - ${cache.mkString("\n    ", "\n    ", "\n    ")}")
    result
  }

  def doIvyFetch(jar: JarRequest) = {
    val child = context.actorOf(FetchIvyDepsActor.props(resolvers), name = s"fetch-${jar.toIdString}")
    children += child
    child ! IvyFetch(jar)
  }

  def doFileSizeLookup(jar: JarRequest) = {
    val child = context.actorOf(FileSizeLookup.props, name = s"file-${jar.toIdString}")
    children += child
    child ! jar
  }

  //  
  def receive = {

    case IvyFetch(jar) =>
      println(s"IvyFetch - ${jar}")
      cache.contains(jar) match {
        case true  => // ignore, already processed 
        case false => doIvyFetch(jar)
      }

    case FetchCompleted(jar, depCount) =>
      println(s"Fetched ${depCount} dependencies for jar ${jar.toIdString}")
      children -= sender

    case jar: JarRequest =>
      println(s"JarRequest - ${jar}")
      cache.contains(jar) match {
        case true => // ignore, already processed 
        case false =>
          if (updateCache(JarData(jar, 0, Set()))) {
            if (jar != topJar) doIvyFetch(jar)
            doFileSizeLookup(jar)
          }
      }

    case printer: CollectionPrinter => {
      messages ++= printer.messages
      children -= sender
    }

    case data: JarData =>
      println(s"JarData - ${data}")
      updateCache(data)
      children -= sender

    case CacheRequest => sender ! DependencyList(topJar, cache.values.toList.toSeq)

    case rt: ReceiveTimeout =>
      println(s"ReceiveTimout - ${rt}")
      context.stop(self)
  }
}
object JarTraversalActor {
  def props(resolvers: List[Resolver], jar: JarRequest): Props = Props[JarTraversalActor](new JarTraversalActor(resolvers, jar))
}
