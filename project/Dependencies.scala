import sbt._
import Keys._

object Dependencies {
  val logback = "ch.qos.logback" % "logback-classic" % "1.0.13"

  val scalatest = "org.scalatest" %% "scalatest" % "2.0"

  val slf4j = "org.slf4j" % "slf4j-api" % "1.7.5"

  object jackson {
    object constants {
      val version = "2.3.0"
      val name = "jackson"
      object group {
        val prefix = s"com.fasterxml.$name"
        val core = s"$prefix.core"
        val module = s"$prefix.module"
      }
      val module = s"$name-module"
    }
    import constants._
    val Seq(core, databind) = Seq("core", "databind").map(a => group.core % s"$name-$a" % version)
    val Seq(afterburner) = Seq("afterburner").map(a => group.module % s"$module-$a" % version)
  }
  object libraries {
    object constants {
      val test = "test"
    }
    import constants._
    private[this] def d = Dependencies
    val common = Seq(slf4j, scalatest % test, logback % test)
    val macros = common ++ Seq()
    val core = common ++ Seq()
    val json = common ++ Seq(jackson.core, jackson.databind, jackson.afterburner)
  }
}
