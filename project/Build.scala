import sbt._
import Keys._

object Build extends Build {
  import scalariform.formatter.preferences._
  import com.typesafe.sbt.SbtScalariform
  lazy val scalariformSettings = SbtScalariform.scalariformSettings ++ Seq(
    SbtScalariform.ScalariformKeys.preferences := FormattingPreferences()
      .setPreference(DoubleIndentClassDeclaration, true))

  lazy val commonSettings = Seq(
    version := "0.0.1-SNAPSHOT",
    javaOptions := Seq("-Xms1024m"),
    organization := "info.sumito3478",
    scalaVersion := "2.10.3",
    crossScalaVersions := Seq("2.10.3"),
    fork := true,
    scalacOptions ++= Seq(
      "-encoding", "utf-8",
      "-target:jvm-1.7",
      "-deprecation",
      "-feature",
      "-unchecked")) ++ scalariformSettings

  val common = (p: Project) =>
    p.copy(id = s"saare-${p.id}")
      .settings(commonSettings: _*)

  implicit class ProjectW(val self: Project) extends AnyVal {
    def libs(xs: Seq[ModuleID]) = self.settings(libraryDependencies ++= xs)
  }

  import Dependencies._

  lazy val macros = project configure common libs libraries.macros

  lazy val core = project configure common libs libraries.core

  lazy val json = project configure common libs libraries.json

  lazy val root = project in file(".") configure common libs libraries.common aggregate (macros, core, json) settings (publishArtifact := false)
}
