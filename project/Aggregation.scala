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
import Tier0._
import Tier1._
import Tier2._

object Aggregation extends Common {
  import Dependencies._
  lazy val root = project.in(file(".")).configure(common).libs(libraries.common)
    .aggregate(`reflect-core`, core, reflect, collection, json, `http-client`)
    .settings(publishArtifact := false)
    .settings(sbtunidoc.Plugin.unidocSettings: _*)
}
