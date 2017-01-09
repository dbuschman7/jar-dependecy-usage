package me.lighspeed7.jars

import java.io.File

import scala.collection.mutable
import scala.concurrent.duration._

import akka.actor.{ Actor, ActorRef, Props }
import akka.actor.ReceiveTimeout
import scala.collection.mutable.TreeSet

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
      //      println(s"Looking up ${file.getCanonicalPath}")
      val size = file.exists() match {
        case true => file.length
        case false =>
          //          println(s"Looking up ${bundleFile.getCanonicalPath}")
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
  val myOrdering = Ordering.fromLessThan[JarData] {
    case (f, s) =>
      f.jar.toKey > s.jar.toKey
  }

  val cache = mutable.HashMap.empty[String, mutable.HashMap[String, JarData]]
  val messages = mutable.ListBuffer.empty[String]
  var children = Set.empty[ActorRef]

  // startup
  self ! IvyFetch(topJar)
  context.setReceiveTimeout(60 seconds)

  //
  // Helpers
  // ///////////////////////////////
  def updateCache(data: JarData): Unit = {
    cache.get(data.jar.groupArtifact) match {
      case Some(jDataSet) =>
        //        println(s"Found jar group - ${jDataSet}")
        jDataSet.contains(data.jar.sortableVersion) match {
          case true =>
            //            println(s"Found version - ${data.jar.sortableVersion}")
            jDataSet += (data.jar.sortableVersion -> jDataSet(data.jar.sortableVersion).merge(data))
          case false =>
            //            println(s"Not found - ${data.jar.sortableVersion}")
            jDataSet += (data.jar.sortableVersion -> data)
        }
      case None => {
        //        println(s"Adding new jar group - ${data.jar.groupArtifact}")
        val inner = mutable.HashMap.empty[String, JarData] += (data.jar.sortableVersion -> data)
        cache += (data.jar.groupArtifact -> inner)
      }
    }
    //    dumpCache
  }

  def dumpCache: Unit = {
    val sortedCache = cache.toSeq.sortBy(_._1)
    println(s"Cache --- size = ${sortedCache.size} - ${sortedCache.mkString("\n    ", "\n    ", "\n    ")}")
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
      //      println(s"IvyFetch - ${jar}")
      cache.contains(jar.groupArtifact) match {
        case true  => // ignore, already processed 
        case false => doIvyFetch(jar)
      }

    case FetchCompleted(jar, depCount) =>
      //      println(s"Fetched ${depCount} dependencies for jar ${jar.toIdString}")
      children -= sender

    case jar: JarRequest =>
      //      println(s"JarRequest - ${jar}")
      cache.contains(jar.groupArtifact) match {
        case true => // ignore, already processed 
        case false =>
          updateCache(JarData(jar, 0, Set()))
          if (jar != topJar) doIvyFetch(jar)
          doFileSizeLookup(jar)
      }

    case printer: CollectionPrinter => {
      messages ++= printer.messages
      children -= sender
    }

    case data: JarData =>
      //      println(s"JarData - ${data}")
      updateCache(data)
      children -= sender

    case CacheRequest => {
      val deps: Seq[JarData] = cache.values.map(_.values).map(_.toSeq.sortBy(_.jar.sortableVersion).reverseIterator.toSeq.head).toSeq
      sender ! DependencyList(topJar, deps)
      context.stop(self)
    }

    case rt: ReceiveTimeout =>
      println(s"ReceiveTimout - ${rt}")
      context.stop(self)
  }
}
object JarTraversalActor {
  def props(resolvers: List[Resolver], jar: JarRequest): Props = Props[JarTraversalActor](new JarTraversalActor(resolvers, jar))
}
