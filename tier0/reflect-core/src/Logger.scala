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

object Logger {
  trait LoggerImpl[Tag] {
    def isTraceEnabled: Boolean
    def trace(name: String, msg: String)
    def trace(name: String, msg: String, e: Throwable)
    def isDebugEnabled: Boolean
    def debug(name: String, msg: String)
    def debug(name: String, msg: String, e: Throwable)
    def isInfoEnabled: Boolean
    def info(name: String, msg: String)
    def info(name: String, msg: String, e: Throwable)
    def isWarnEnabled: Boolean
    def warn(name: String, msg: String)
    def warn(name: String, msg: String, e: Throwable)
    def isErrorEnabled: Boolean
    def error(name: String, msg: String)
    def error(name: String, msg: String, e: Throwable)
  }
  class Slf4jLogger(name: String) extends LoggerImpl[Slf4jLogger.Tag] {
    import org.slf4j._
    private[this] val underlying = LoggerFactory.getLogger(name)
    def isTraceEnabled = underlying.isTraceEnabled
    def trace(name: String, msg: String) = underlying.trace(MarkerFactory.getMarker(name), msg)
    def trace(name: String, msg: String, e: Throwable) = underlying.trace(MarkerFactory.getMarker(name), msg, e)
    def isDebugEnabled = underlying.isDebugEnabled
    def debug(name: String, msg: String) = underlying.debug(MarkerFactory.getMarker(name), msg)
    def debug(name: String, msg: String, e: Throwable) = underlying.debug(MarkerFactory.getMarker(name), msg, e)
    def isInfoEnabled = underlying.isInfoEnabled
    def info(name: String, msg: String) = underlying.info(MarkerFactory.getMarker(name), msg)
    def info(name: String, msg: String, e: Throwable) = underlying.info(MarkerFactory.getMarker(name), msg, e)
    def isWarnEnabled = underlying.isWarnEnabled
    def warn(name: String, msg: String) = underlying.warn(MarkerFactory.getMarker(name), msg)
    def warn(name: String, msg: String, e: Throwable) = underlying.warn(MarkerFactory.getMarker(name), msg, e)
    def isErrorEnabled: Boolean = underlying.isErrorEnabled
    def error(name: String, msg: String) = underlying.error(MarkerFactory.getMarker(name), msg)
    def error(name: String, msg: String, e: Throwable) = underlying.error(MarkerFactory.getMarker(name), msg, e)
  }
  object Slf4jLogger {
    sealed trait Tag
  }
  class Macro(val c: Context) extends ReflectCore.Reflect {
    import c.universe._
    lazy val logName = {
      val p = c.enclosingPosition
      s"${p.source.file.name}:${p.line}:${p.column} "
    }
    def error(msg: c.Tree)(impl: c.Tree): c.Tree = checking(weakTypeOf[Unit])(q"if ($impl.isErrorEnabled) $impl.error($logName, $msg)")
    def errorThrowable(msg: c.Tree, e: c.Tree)(impl: c.Tree): c.Tree = checking(weakTypeOf[Unit])(q"if ($impl.isErrorEnabled) $impl.error($logName, $msg, $e)")
    def warn(msg: c.Tree)(impl: c.Tree): c.Tree = checking(weakTypeOf[Unit])(q"if ($impl.isWarnEnabled) $impl.warn($logName, $msg)")
    def warnThrowable(msg: c.Tree, e: c.Tree)(impl: c.Tree): c.Tree = checking(weakTypeOf[Unit])(q"if ($impl.isWarnEnabled) $impl.warn($logName, $msg, $e)")
    def info(msg: c.Tree)(impl: c.Tree): c.Tree = checking(weakTypeOf[Unit])(q"if ($impl.isInfoEnabled) $impl.info($logName, $msg)")
    def infoThrowable(msg: c.Tree, e: c.Tree)(impl: c.Tree): c.Tree = checking(weakTypeOf[Unit])(q"if ($impl.isInfoEnabled) $impl.info($logName, $msg, $e)")
    def debug(msg: c.Tree)(impl: c.Tree): c.Tree = checking(weakTypeOf[Unit])(q"if ($impl.isDebugEnabled) $impl.debug($logName, $msg)")
    def debugThrowable(msg: c.Tree, e: c.Tree)(impl: c.Tree): c.Tree = checking(weakTypeOf[Unit])(q"if ($impl.isDebugEnabled) $impl.debug($logName, $msg, $e)")
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
            val $ret = $rhs
            $loggerObject.$method(${name.toString} + " => " + $ret)
            $ret
          """
          else q"$rhs"
          DefDef(mods, name, tparams, vparams, tpt, q"""
            {
              $loggerObject.$method($msg)
              $showRetExp
            }
          """)
        case _ =>
          warnAndBail(s"annotation saare.Logger.logging cannot applied to ${show(annottees)}")
      }
    }
  }
  class logging(level: String = "debug", showRet: Boolean = false) extends StaticAnnotation {
    def macroTransform(annottees: Any*): Any = macro Macro.logging
  }
  def error[L: LoggerImpl](msg: String): Unit = macro Macro.error
  def error[L: LoggerImpl](msg: String, e: Throwable): Unit = macro Macro.errorThrowable
  def warn[L: LoggerImpl](msg: String): Unit = macro Macro.warn
  def warn[L: LoggerImpl](msg: String, e: Throwable): Unit = macro Macro.warnThrowable
  def info[L: LoggerImpl](msg: String): Unit = macro Macro.info
  def info[L: LoggerImpl](msg: String, e: Throwable): Unit = macro Macro.infoThrowable
  def debug[L: LoggerImpl](msg: String): Unit = macro Macro.debug
  def debug[L: LoggerImpl](msg: String, e: Throwable): Unit = macro Macro.debugThrowable
}

