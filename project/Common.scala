/*
Copyright 2013-2014 sumito3478 <sumito3478@gmail.com>

This file is part of the Saare Library.

This software is free software; you can redistribute it and/or modify it
under the terms of the GNU Lesser General Public License as published by the
Free Software Foundation; either version 3 of the License, or (at your
option) any later version.

This software is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
for more details.

You should have received a copy of the GNU Lesser General Public License
along with this software. If not, see http://www.gnu.org/licenses/.
*/
import sbt._
import Keys._
import java.io._

abstract class Common extends Build {
  import scalariform.formatter.preferences._
  import com.typesafe.sbt.SbtScalariform
  lazy val scalariformSettings = SbtScalariform.scalariformSettings ++ Seq(
    SbtScalariform.ScalariformKeys.preferences := FormattingPreferences()
      .setPreference(DoubleIndentClassDeclaration, true))

  lazy val releaseSettings = {
    import sbtrelease.ReleasePlugin.ReleaseKeys._
    sbtrelease.ReleasePlugin.releaseSettings ++ Seq(
      crossBuild := true,
      tagComment <<= (version in ThisBuild) map (v => s"Release $v"),
      commitMessage <<= (version in ThisBuild) map (v => s"Bump version number to $v"))
  }

  lazy val macroParadiseSettings = Seq(
    resolvers += Resolver.sonatypeRepo("releases"),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.0.1" cross CrossVersion.full)
  )

  lazy val commonSettings = Seq(
    scalaSource in Compile := baseDirectory.value / "src",
    scalaSource in Test := baseDirectory.value / "test",
    javaSource in Compile := baseDirectory.value / "src",
    javaSource in Test := baseDirectory.value / "test",
    resourceDirectory in Compile := baseDirectory.value / "res",
    resourceDirectory in Test := baseDirectory.value / "res-test",
    javaOptions := Seq("-Xms1024m"),
    organization := "info.volpts",
    scalaVersion := "2.11.4",
    fork := true,
    resolvers += Resolver.sonatypeRepo("releases"),
    libraryDependencies <+= scalaVersion("org.scala-lang" % "scala-reflect" % _),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    incOptions := incOptions.value.withNameHashing(true),
    scalacOptions ++= Seq(
      "-encoding", "utf-8",
      "-target:jvm-1.7",
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xexperimental",
      "-Xcheckinit",
      "-Xlint",
      "-Yinfer-argument-types")) ++ scalariformSettings ++ releaseSettings ++ macroParadiseSettings

  def common(p: Project) =
      p.copy(id = s"saare-${p.id}")
        .settings(commonSettings: _*)

  implicit class ProjectW(val self: Project) {
    def libs(xs: Seq[ModuleID]) = self.settings(libraryDependencies ++= xs)
  }
}
