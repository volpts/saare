import sbt._
import Keys._

object Dependencies {
  val logback = "ch.qos.logback" % "logback-classic" % "1.0.13"

  val scalatest = "org.scalatest" %% "scalatest" % "2.0"

  val slf4j = "org.slf4j" % "slf4j-api" % "1.7.5"

  object libraries {
    object constants {
      val test = "test"
    }
    import constants._
    private[this] def d = Dependencies
    val common = Seq(slf4j, scalatest % test, logback % test)
    val macros = common ++ Seq()
    val core = common ++ Seq()
    val json = common ++ Seq()
  }
}
