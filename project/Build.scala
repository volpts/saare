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

object Build extends Build {
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

  lazy val commonSettings = Seq(
    scalaSource in Compile := baseDirectory.value / "src",
    scalaSource in Test := baseDirectory.value / "test",
    javaSource in Compile := baseDirectory.value / "src",
    javaSource in Test := baseDirectory.value / "test",
    resourceDirectory in Compile := baseDirectory.value / "res",
    resourceDirectory in Test := baseDirectory.value / "res-test",
    javaOptions := Seq("-Xms1024m"),
    organization := "info.volpts",
    scalaVersion := "2.11.1",
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
      "-Yinfer-argument-types")) ++ scalariformSettings ++ releaseSettings

  val common = (p: Project) =>
    p.copy(id = s"saare-${p.id}")
      .settings(commonSettings: _*)

  implicit class ProjectW(val self: Project) extends AnyVal {
    def libs(xs: Seq[ModuleID]) = self.settings(libraryDependencies ++= xs)
  }

  import Dependencies._

  lazy val `core-macros` = project configure common libs libraries.macros

  lazy val core = project configure common libs libraries.core dependsOn `core-macros`

  lazy val hashing = project configure common libs libraries.hashing dependsOn core

  lazy val collection = project configure common libs libraries.collection dependsOn core

  lazy val json = project configure common libs libraries.json dependsOn core

  lazy val `http-client` = project configure common libs libraries.`http-client` dependsOn (core, json)

  lazy val `web-twitter` = project configure common libs libraries.`web-twitter` dependsOn(core)

  lazy val `datastore-hsqldb` = project configure common libs libraries.`datastore-hsqldb` dependsOn core

  lazy val `datastore-cassandra` = project configure common libs libraries.`datastore-cassandra` dependsOn core

  lazy val root = project.in(file(".")).configure(common).libs(libraries.common)
    .aggregate(`core-macros`, core, hashing, json, `http-client`, `web-twitter`, `datastore-hsqldb`, `datastore-cassandra`)
    .settings(publishArtifact := false)
    .settings(sbtunidoc.Plugin.unidocSettings: _*)
}
