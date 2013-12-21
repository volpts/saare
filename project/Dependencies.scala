import sbt._
import Keys._

object Dependencies {
  object libraries {
    object constants {
      val test = "test"
    }
    import constants._
    private[this] def d = Dependencies
    val common = Seq()
    val macros = common ++ Seq()
    val core = common ++ Seq()
    val json = common ++ Seq()
  }
}
