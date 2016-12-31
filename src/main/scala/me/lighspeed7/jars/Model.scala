package me.lighspeed7.jars

import scala.collection.mutable.ArrayBuffer

object Constants {

  //  val megabyte = 1024 * 1024
  val megabyte = 1000 * 1000

}

case class JarRequest(
    groupId: String,
    artifactId: String,
    version: String //
    ) {

  def groupArtifact: String = s"${groupId}:${artifactId}"

  def ivyJarPath: String = s"${groupId}/${artifactId}/jars/${artifactId}-${version}.jar"
  def ivyBundlePath: String = s"${groupId}/${artifactId}/bundles/${artifactId}-${version}.jar"
  def ivySrcPath: String = s"${groupId}/${artifactId}/jars/${artifactId}-${version}-sources.jar"
  def ivyDocPath: String = s"${groupId}/${artifactId}/jars/${artifactId}-${version}-javadoc.jar"

  def toKey: String = s"${groupId}#${artifactId};${version}"
  def toIdString: String = s"${groupId}:${artifactId}:${version}"

}

case class JarData(
    jar: JarRequest,
    size: Long,
    dependencies: Set[JarRequest] = Set() //
    ) {

  def merge(in: JarData): JarData = this.copy(size = Math.max(in.size, size), dependencies = (dependencies ++ in.dependencies))

  def formatTotalSize: String = f"${size.toDouble / Constants.megabyte}%3.2f MB"

}

case class DependencyList(mainJar: JarRequest, deps: Seq[JarData]) {
  def withoutScalaLibrary: DependencyList = DependencyList(mainJar, deps.filterNot { dep => dep.jar.artifactId == "scala-library" && dep.jar.groupId == "org.scala-lang" })

  def computerTotalSize: Long = deps.map(_.size).sum
  def formatTotalSize: String = f"${computerTotalSize.toDouble / Constants.megabyte}%3.2f MB"

  val formatter = java.text.NumberFormat.getIntegerInstance

  def dumpFormatted(includeScalaLib: Boolean = false): Seq[String] = {
    val list = includeScalaLib match {
      case true  => this
      case false => withoutScalaLibrary
    }

    val main = "  " + mainJar.toIdString
    val meat = list.deps.sortBy(_.size).map { jar =>
      f"${jar.formatTotalSize}%10s (${formatter.format(jar.size)}%10s) - ${jar.jar.toIdString}"
    }
    val total = f"${list.formatTotalSize}%10s (${formatter.format(list.computerTotalSize)}%10s) - Total size"

    main +: "  ======== ============ - ==========================" +: meat :+ "  ======== ============ - ==========================" :+ total
  }

}

trait Printer {
  def info(in: String)
}

class PrintlnPrinter extends Printer {
  def info(in: String) = println(in)
}

class CollectionPrinter extends Printer {
  var messages = ArrayBuffer.empty[String]
  def info(in: String) = messages += in
}