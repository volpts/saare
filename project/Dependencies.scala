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

object Dependencies {
  val logback = "ch.qos.logback" % "logback-classic" % "1.1.2"

  val scalatest = "org.scalatest" %% "scalatest" % "2.2.1"

  val lz4 = "net.jpountz.lz4" % "lz4" % "1.2.0"

  val async_http_client = "com.ning" % "async-http-client" % "1.8.12"

  val shapeless = "com.chuusai" %% "shapeless" % "2.0.0"

  val hsqldb = "org.hsqldb" % "hsqldb" % "2.3.2"

  val guava = "com.google.guava" % "guava" % "17.0"

  val jsr305 = "com.google.code.findbugs" % "jsr305" % "3.0.0"

  val jsr310 = "org.threeten" % "threetenbp" % "1.0"

  object slf4j {
    object constants {
      val version = "1.7.7"
      val name = "slf4j"
      val group = "org.slf4j"
    }
    import constants._
    val api = group % s"$name-api" % version
    object over {
      val Seq(jcl, log4j) = Seq("jcl", "log4j").map(a => group % s"$a-over-$name" % version)
    }
    object to {
      val jul = group % s"jul-to-$name" % version
    }
  }
  object netty {
    object constants {
      val version = "4.0.23.Final"
      val name = "netty"
      val group = "io.netty"
    }
    import constants._
    val Seq(buffer, http) = Seq("buffer", "codec-http").map(a => group % s"$name-$a" % version)
  }
  object jackson {
    object constants {
      val version = "2.4.2"
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
  object commons {
    val io = "commons-io" % "commons-io" % "2.4"
    object constants {
      val name = "commons"
      val group = "org.apache.commons"
    }
    import constants._
    val Seq(collections) = Seq(("collections4", "4.0")).map { case (a, v) => group % s"$name-$a" % v }
  }
  object dispatch {
    object constants {
      val version = "0.11.2"
      val name = "dispatch"
      val group = "net.databinder.dispatch"
    }
    import constants._
    val Seq(core) = Seq("core").map(a => group %% s"$name-$a" % version)
  }
  object akka {
    object constants {
      val version = "2.3.5"
      val name = "akka"
      val group = "com.typesafe.akka"
    }
    import constants._
    val Seq(actor) = Seq("actor").map(a => group %% s"$name-$a" % version)
  }
  object twitter4j {
    object constants {
      val version = "4.0.2"
      val name = "twitter4j"
      val group = "org.twitter4j"
    }
    import constants._
    val Seq(core, stream, async) = Seq("core", "stream", "async").map(a => group % s"$name-$a" % version)
  }
  object scalaz {
    object constants {
      val version = "7.1.0"
      val name = "scalaz"
      val group = "org.scalaz"
    }
    import constants._
    val Seq(core, effect, typelevel) = Seq("core", "effect", "typelevel").map(a => group %% s"$name-$a" % version)
  }
  object cassandra {
    object driver {
      object constants {
        val version = "2.0.2"
        val name = "cassandra-driver"
        val group = "com.datastax.cassandra"
      }
      import constants._
      val Seq(core) = Seq("core").map(a => group % s"$name-$a" % version)
    }
  }
  object libraries {
    object constants {
      val test = "test"
    }
    import constants._
    private[this] def d = Dependencies
    val common = Seq(slf4j.api, commons.io, akka.actor, shapeless, guava, scalaz.core, scalaz.effect, scalaz.typelevel, jsr310, scalatest % test, logback % test, slf4j.to.jul % test, slf4j.over.jcl % test, slf4j.over.log4j % test, jsr305 % "provided")
    val reflect = common ++ Seq()
    val core = common ++ Seq(netty.buffer)
    val collection = common ++ Seq(commons.collections)
    val hashing = common ++ Seq(lz4 % test)
    val json = common ++ Seq(jackson.core, jackson.databind, jackson.afterburner)
    val `http-client` = common ++ Seq(async_http_client /* ensure minimum version */, dispatch.core)
    val crawler = common ++ Seq(twitter4j.core, twitter4j.stream, twitter4j.async)
  }
}
