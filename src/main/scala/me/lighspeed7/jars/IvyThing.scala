package me.lighspeed7.jars
// Taken from Ammonite - ammonite.runtime.tools.IvyThing

import org.apache.ivy.Ivy
import org.apache.ivy.core.module.descriptor.{ DefaultDependencyDescriptor, DefaultModuleDescriptor }
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.apache.ivy.core.resolve.{ IvyNode, ResolveOptions }
import org.apache.ivy.core.settings.IvySettings
import org.apache.ivy.plugins.repository.file.FileRepository
import org.apache.ivy.plugins.resolver.{ ChainResolver, DependencyResolver, FileSystemResolver, IBiblioResolver, RepositoryResolver }
import org.apache.ivy.util.{ AbstractMessageLogger, Message }

import IvyThing.ivyInstance

object IvyConstructor extends IvyConstructor
trait IvyConstructor {
  implicit class GroupIdExt(groupId: String) {
    def %(artifactId: String) = (groupId, artifactId)
    def %%(artifactId: String) = (groupId, artifactId + "_" + IvyThing.scalaBinaryVersion)
  }
  implicit class ArtifactIdExt(t: (String, String)) {
    def %(version: String) = (t._1, t._2, version)
  }
}

/**
 * Resolve artifacts from Ivy. Originally taken from
 *
 * http://makandracards.com/evgeny-goldin/5817-calling-ivy-from-groovy-or-java
 *
 * And transliterated into Scala. I have no idea how or why it works.
 */
case class IvyThing(resolvers: () => List[Resolver], printer: Printer, verboseOutput: Boolean) {

  case class IvyResolutionException(failed: Seq[String]) extends Exception(
    "failed to resolve ivy dependencies " + failed.mkString(", "))

  var maxLevel = 2
  var silentIvyLogs: String = ""
  Message.setDefaultLogger(new AbstractMessageLogger {
    def doEndProgress(msg: String) = Console.err.println("Done")
    def doProgress() = Console.err.print(".")
    def log(msg: String, level: Int) = if (level <= maxLevel) verboseOutput match {
      case true  => printer.info(msg)
      case false => silentIvyLogs += msg
    }
    def rawlog(msg: String, level: Int) = log(msg, level)
  })

  def resolveArtifacts(jar: JarRequest, verbosity: Int = 2): Seq[JarRequest] = synchronized {

    maxLevel = verbosity
    val ivy = ivyInstance(resolvers)

    val md = DefaultModuleDescriptor.newDefaultInstance(
      ModuleRevisionId.newInstance(
        jar.groupId,
        jar.artifactId + "-caller",
        "working"))

    md.addDependency {
      val desc = new DefaultDependencyDescriptor(
        md,
        ModuleRevisionId.newInstance(jar.groupId, jar.artifactId, jar.version),
        false,
        false,
        true)
      // No idea wtf this is
      desc.addDependencyConfiguration("*", "*")
      desc
    }

    val options = new ResolveOptions()
      .setConfs(Array("default"))
      .setRefresh(true)
      .setOutputReport(false)

    //init resolve report
    val report = ivy.resolve(md, options)
    //    val unresolved = report.getAllProblemMessages

    //    println("IVY THING DEBUGGING")
    //    println(report.getAllProblemMessages.toSeq.mkString("All Problems ", "\n", "\n"))
    //    println(report.getProblemMessages.toSeq.mkString("Problem Messages ", "\n", "\n"))
    //    println(report.getAllArtifactsReports.toSeq.mkString("All Artifacts ", "\n", "\n"))
    //    println(report.getFailedArtifactsReports.toSeq.mkString("Failed Artifacts ", "\n", "\n"))
    //    println(report.getArtifacts.toSeq.mkString("Artifacts ", "\n", "\n"))
    //    println(report.getUnresolvedDependencies.toSeq.mkString("Unresolved Deps ", "\n", "\n"))
    //    report.getUnresolvedDependencies
    //      .toSeq
    //      .map(_.getProblem)
    //      .foreach(_.printStackTrace())

    val unresolved = report.getUnresolvedDependencies.toSeq.map { un: IvyNode =>
      val id = un.getResolvedId()
      JarRequest(id.getOrganisation, id.getName, id.getRevision)
    }

    val resolved = report.getAllArtifactsReports.toSeq.map { un =>
      val id = un.getArtifact.getModuleRevisionId()
      JarRequest(id.getOrganisation, id.getName, id.getRevision)
    }

    resolved ++ unresolved
  }

}

object IvyThing {

  def ivyInstance(resolvers: () => List[Resolver]) = Ivy.newInstance {

    // create clear ivy settings
    val ivySettings = new IvySettings() {
      // Override getResolver to make it stop spewing up useless
      // `unknown resolver` errors:  lihaoyi/Ammonite/issues/144
      override def getResolver(resolverName: String): DependencyResolver = {
        if (!this.getResolverNames.contains(resolverName)) null
        else super.getResolver(resolverName)
      }
    }

    // add maven repo resolver
    val chainResolver = new ChainResolver

    // #433 changingPattern, changingMatcher and checkModified are required so that updates to
    // SNAPSHOT versions are pulled in
    // see: https://ant.apache.org/ivy/history/2.3.0/concept.html#change

    // look for changes to SNAPSHOT versions
    chainResolver.setChangingPattern(".*SNAPSHOT")
    // the above pattern is a regex
    chainResolver.setChangingMatcher("regexp")
    // check if ivy metadata has been modified (required for above to work)
    chainResolver.setCheckmodified(true)
    chainResolver.setName("chain-resolver")
    chainResolver.setReturnFirst(true)
    resolvers().map(_()).foreach(chainResolver.add)
    ivySettings.addResolver(chainResolver)

    // set to the default resolver
    ivySettings.setDefaultResolver(chainResolver.getName)

    // creates an Ivy instance with settings
    ivySettings
  }

  val scalaBinaryVersion =
    scala.util.Properties
      .versionString
      .stripPrefix("version ")
      .split('.')
      .take(2)
      .mkString(".")

}

object Resolvers {

  // this pattern comes from sbt.Resolver  
  val IvyPattern: String =
    "[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)" +
      "[revision]/[type]s/[artifact](-[classifier]).[ext]"

  // this pattern comes from IBiblioResolver  
  val MavenPattern: String =
    "[organisation]/[module]/" +
      "[revision]/[artifact]-[revision](-[classifier]).[ext]"

  // this pattern comes from IBiblioResolver  
  val DefaultPattern: String =
    "[module]/[type]s/[artifact]-[revision].[ext]"

  lazy val defaultResolvers: List[Resolver] = List(
    Resolver.File(
      "local",
      "/.ivy2/local",
      "/[organisation]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext]",
      m2 = false),
    Resolver.File(
      "m2",
      "/.m2/repository",
      "/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]",
      m2 = true),
    Resolver.Http(
      "central",
      "http://repo1.maven.org/maven2/",
      MavenPattern,
      m2 = true))
}

/**
 * A thin wrapper around [[RepositoryResolver]], which wraps them and provides
 * hashability in order to set the cache tags. This lets us invalidate the ivy
 * resolution cache if the set of resolvers changes
 */
sealed trait Resolver {
  def apply(): RepositoryResolver
}
object Resolver {
  case class File(name: String, root: String, pattern: String, m2: Boolean) extends Resolver {
    def apply() = {
      val testRepoDir = sys.props("user.home") + root
      val repo = new FileRepository(new java.io.File(testRepoDir))

      val res = new FileSystemResolver()
      res.addIvyPattern(testRepoDir + pattern)
      res.addArtifactPattern(testRepoDir + pattern)
      res.setRepository(repo)
      res.setM2compatible(m2)
      res.setName(name)
      // #433 required so that updates to SNAPSHOT versions are pulled in
      res.setChangingPattern(".*SNAPSHOT")
      res.setChangingMatcher("regexp")
      res.setCheckmodified(true)

      res

    }
  }
  case class Http(name: String, root: String, pattern: String, m2: Boolean) extends Resolver {
    def apply() = {
      val res = new IBiblioResolver()
      res.setUsepoms(true)
      res.setM2compatible(m2)
      res.setName(name)
      res.setRoot(root)
      res.setPattern(pattern)
      res
    }
  }
}