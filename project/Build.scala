/*Copyright 2013-2014 sumito3478 <sumito3478@gmail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
import sbt._
import Keys._

object Build extends Build {
  import scalariform.formatter.preferences._
  import com.typesafe.sbt.SbtScalariform
  lazy val scalariformSettings = SbtScalariform.scalariformSettings ++ Seq(
    SbtScalariform.ScalariformKeys.preferences := FormattingPreferences()
      .setPreference(DoubleIndentClassDeclaration, true))

  import fmpp.FmppPlugin._

  lazy val releaseSettings = {
    import sbtrelease.ReleasePlugin.ReleaseKeys._
    sbtrelease.ReleasePlugin.releaseSettings ++ Seq(
      crossBuild := true,
      tagComment <<= (version in ThisBuild) map (v => s"Release $v"),
      commitMessage <<= (version in ThisBuild) map (v => s"Bump version number to $v"))
  }

  lazy val commonSettings = Seq(
    javaOptions := Seq("-Xms1024m"),
    organization := "info.volpts",
    scalaVersion := "2.11.0",
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
      "-Yinfer-argument-types")) ++ scalariformSettings ++ fmppSettings ++ releaseSettings

  val common = (p: Project) =>
    p.copy(id = s"saare-${p.id}")
      .settings(commonSettings: _*)
      .configs(Fmpp)

  implicit class ProjectW(val self: Project) extends AnyVal {
    def libs(xs: Seq[ModuleID]) = self.settings(libraryDependencies ++= xs)
  }

  import Dependencies._

  lazy val `core-macros` = project configure common libs libraries.macros

  lazy val core = project configure common libs libraries.core dependsOn `core-macros`

  lazy val hashing = project configure common libs libraries.hashing dependsOn core

  lazy val json = project configure common libs libraries.json dependsOn core

  lazy val `http-client` = project configure common libs libraries.`http-client` dependsOn (core, json)

  lazy val `web-twitter` = project configure common libs libraries.`web-twitter` dependsOn(core)

  lazy val `datastore-hsqldb` = project configure common libs libraries.`datastore-hsqldb` dependsOn core

  lazy val root = project.in(file(".")).configure(common).libs(libraries.common)
    .aggregate(`core-macros`, core, hashing, json, `http-client`, `web-twitter`, `datastore-hsqldb`)
    .settings(publishArtifact := false)
    .settings(sbtunidoc.Plugin.unidocSettings: _*)
}
