package me.lighspeed7.jars

import org.scalatest.FunSuite
import org.scalatest.Matchers.{ be, convertToAnyShouldWrapper, convertToStringShouldWrapper }

class ModelTest extends FunSuite {
  test("Jar ivy generation") {

    val jar = JarRequest("com.typesafe.akka", "akka-stream_2.12", "2.4.14")
    val data = JarData(jar, 0)

    jar.toKey should be("com.typesafe.akka#akka-stream_2.12;2.4.14")
    jar.ivyJarPath should be("com.typesafe.akka/akka-stream_2.12/jars/akka-stream_2.12-2.4.14.jar")
    jar.ivySrcPath should be("com.typesafe.akka/akka-stream_2.12/jars/akka-stream_2.12-2.4.14-sources.jar")
    jar.ivyDocPath should be("com.typesafe.akka/akka-stream_2.12/jars/akka-stream_2.12-2.4.14-javadoc.jar")

  }

  test("Jar merging") {
    val jar = JarRequest("com.typesafe.akka", "akka-stream_2.12", "2.4.14")
    val data = JarData(jar, 2, Set(jar))

    data.merge(data) should be(data)
  }

}