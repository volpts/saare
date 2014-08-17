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

object Tier1 extends Tier(1) {
  import Dependencies._
  lazy val core = project configure common libs libraries.core dependsOn Tier0.`reflect-core`

  lazy val reflect = project configure common libs libraries.core dependsOn (core, Tier0.`reflect-core`)

  lazy val collection = project configure common libs libraries.collection dependsOn core

  lazy val json = project configure common libs libraries.json dependsOn core

  lazy val `http-client` = project configure common libs libraries.`http-client` dependsOn (core, json)

  lazy val pickle = project configure common libs libraries.common dependsOn core
}
