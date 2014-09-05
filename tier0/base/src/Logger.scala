/*
Copyright 2014 sumito3478 <sumito3478@gmail.com>

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
package saare

import scala.annotation.StaticAnnotation
import scala.language.experimental.macros
import scala.reflect.macros._
import scala.collection.immutable._
import akka.util._
import scala.reflect.macros.whitebox.Context
import scalaz.effect.IO

object Logger {
  trait LoggerImpl[Tag] {
    def isTraceEnabled: IO[Boolean]
    def trace(name: String, msg: String): IO[Unit]
    def trace(name: String, msg: String, e: Throwable): IO[Unit]
    def isDebugEnabled: IO[Boolean]
    def debug(name: String, msg: String): IO[Unit]
    def debug(name: String, msg: String, e: Throwable): IO[Unit]
    def isInfoEnabled: IO[Boolean]
    def info(name: String, msg: String): IO[Unit]
    def info(name: String, msg: String, e: Throwable): IO[Unit]
    def isWarnEnabled: IO[Boolean]
    def warn(name: String, msg: String): IO[Unit]
    def warn(name: String, msg: String, e: Throwable): IO[Unit]
    def isErrorEnabled: IO[Boolean]
    def error(name: String, msg: String): IO[Unit]
    def error(name: String, msg: String, e: Throwable): IO[Unit]
  }
  class Slf4jLogger(name: String) extends LoggerImpl[Slf4jLogger.Tag] {
    import org.slf4j._
    private[this] val underlying = LoggerFactory.getLogger(name)
    def isTraceEnabled = IO(underlying.isTraceEnabled)
    def trace(name: String, msg: String) = IO(underlying.trace(MarkerFactory.getMarker(name), msg))
    def trace(name: String, msg: String, e: Throwable) = IO(underlying.trace(MarkerFactory.getMarker(name), msg, e))
    def isDebugEnabled = IO(underlying.isDebugEnabled)
    def debug(name: String, msg: String) = IO(underlying.debug(MarkerFactory.getMarker(name), msg))
    def debug(name: String, msg: String, e: Throwable) = IO(underlying.debug(MarkerFactory.getMarker(name), msg, e))
    def isInfoEnabled = IO(underlying.isInfoEnabled)
    def info(name: String, msg: String) = IO(underlying.info(MarkerFactory.getMarker(name), msg))
    def info(name: String, msg: String, e: Throwable) = IO(underlying.info(MarkerFactory.getMarker(name), msg, e))
    def isWarnEnabled = IO(underlying.isWarnEnabled)
    def warn(name: String, msg: String) = IO(underlying.warn(MarkerFactory.getMarker(name), msg))
    def warn(name: String, msg: String, e: Throwable) = IO(underlying.warn(MarkerFactory.getMarker(name), msg, e))
    def isErrorEnabled = IO(underlying.isErrorEnabled)
    def error(name: String, msg: String) = IO(underlying.error(MarkerFactory.getMarker(name), msg))
    def error(name: String, msg: String, e: Throwable) = IO(underlying.error(MarkerFactory.getMarker(name), msg, e))
  }
  object Slf4jLogger {
    sealed trait Tag
  }
  class Macro(val c: Context) extends Reflect {
    import c.universe._
    lazy val logName = {
      val p = c.enclosingPosition
      s"${p.source.file.name}:${p.line}:${p.column} "
    }
    val IOObject = companion[IO[_]]
    def log(msg: c.Tree, impl: c.Tree, level: String) = checking(weakTypeOf[IO[Unit]]) {
      val enabledMethod = TermName("is" ++ level.take(1).toUpperCase(java.util.Locale.ENGLISH) ++ level.drop(1) ++ "Enabled")
      val logMethod = TermName(level)
      q"""
        for {
          enabled <- $impl.$enabledMethod
          _ <- if (enabled) $impl.$logMethod($logName, $msg) else $IOObject.ioUnit
        } yield ()
      """
    }
    def logThrowable(msg: c.Tree, e: c.Tree, impl: c.Tree, level: String) = checking(weakTypeOf[IO[Unit]]) {
      val enabledMethod = TermName("is" ++ level.take(1).toUpperCase(java.util.Locale.ENGLISH) ++ level.drop(1) ++ "Enabled")
      val logMethod = TermName(level ++ "Throwable")
      q"""
        for {
          enabled <- $impl.$enabledMethod
          _ <- if (enabled) $impl.$logMethod($logName, $msg, $e) else $IOObject.ioUnit
        } yield ()
      """
    }
    def error(msg: c.Tree)(impl: c.Tree): c.Tree = log(msg, impl, "error")
    def errorThrowable(msg: c.Tree, e: c.Tree)(impl: c.Tree): c.Tree = logThrowable(msg, e, impl, "error")
    def warn(msg: c.Tree)(impl: c.Tree): c.Tree = log(msg, impl, "warn")
    def warnThrowable(msg: c.Tree, e: c.Tree)(impl: c.Tree): c.Tree = logThrowable(msg, e, impl, "warn")
    def info(msg: c.Tree)(impl: c.Tree): c.Tree = log(msg, impl, "info")
    def infoThrowable(msg: c.Tree, e: c.Tree)(impl: c.Tree): c.Tree = logThrowable(msg, e, impl, "info")
    def debug(msg: c.Tree)(impl: c.Tree): c.Tree = log(msg, impl, "debug")
    def debugThrowable(msg: c.Tree, e: c.Tree)(impl: c.Tree): c.Tree = logThrowable(msg, e, impl, "debug")

    lazy val loggerObject = q"_root_.saare.Logger"
    def logging(annottees: c.Tree*): c.Tree = {
      annottees match {
        case DefDef(mods, name, tparams, vparams, tpt, rhs) :: Nil =>
          val msg = c.parse(vparams.flatten.map(_.name).map(name => s"$name = $$$name").mkString("s\"" + name + "(", ", ", ")\""))
          val level = macroAnnotationParam[String]("level").getOrElse("debug")
          val method = TermName(level)
          val showRet = macroAnnotationParam[Boolean]("showRet").getOrElse(false)
          val ret = TermName(c.freshName("ret"))
          val showRetExp = if (showRet) q"""
            for {
              $ret <- $rhs
              _ <- $loggerObject.$method(${name.toString} + " => " + $ret)
            } yield $ret
          """
          else q"$rhs"
          DefDef(mods, name, tparams, vparams, tpt, q"""
            for {
              _ <- $loggerObject.$method($msg)
              $ret <- $showRetExp
            } yield $ret
          """)
        case _ =>
          warnAndBail(s"annotation saare.Logger.logging cannot applied to ${show(annottees)}")
      }
    }
  }
  class logging(level: String = "debug", showRet: Boolean = false) extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro Macro.logging
  }
  def error[L: LoggerImpl](msg: String): IO[Unit] = macro Macro.error
  def error[L: LoggerImpl](msg: String, e: Throwable): IO[Unit] = macro Macro.errorThrowable
  def warn[L: LoggerImpl](msg: String): IO[Unit] = macro Macro.warn
  def warn[L: LoggerImpl](msg: String, e: Throwable): IO[Unit] = macro Macro.warnThrowable
  def info[L: LoggerImpl](msg: String): IO[Unit] = macro Macro.info
  def info[L: LoggerImpl](msg: String, e: Throwable): IO[Unit] = macro Macro.infoThrowable
  def debug[L: LoggerImpl](msg: String): IO[Unit] = macro Macro.debug
  def debug[L: LoggerImpl](msg: String, e: Throwable): IO[Unit] = macro Macro.debugThrowable
}

