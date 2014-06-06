/*
Copyright 2013 sumito3478 <sumito3478@gmail.com>

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

import scala.language.experimental.macros
import scala.reflect.macros._

object Macros {
  trait TypeNameable[A] {
    def fullName: String
    def name: String
  }
  def materializeTypeNameableImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[Macros.TypeNameable[A]] = {
    import c.universe._
    val a = weakTypeOf[A]
    val typeNameable = weakTypeOf[TypeNameable[A]]
    c.Expr[Macros.TypeNameable[A]](q"new $typeNameable { def fullName = ${a.typeSymbol.fullName} ; def name = ${a.typeSymbol.name.decodedName.toString} }")
  }
  implicit def materializeTypeNameable[A]: Macros.TypeNameable[A] = macro materializeTypeNameableImpl[A]

  trait Logger {
    val underlying: org.slf4j.Logger
    def error(msg: String): Unit = macro Macros.Logger.errorImpl
    def error(msg: String, e: Throwable): Unit = macro Macros.Logger.errorThrowableImpl
    def warn(msg: String): Unit = macro Macros.Logger.warnImpl
    def warn(msg: String, e: Throwable): Unit = macro Macros.Logger.warnThrowableImpl
    def info(msg: String): Unit = macro Macros.Logger.infoImpl
    def info(msg: String, e: Throwable): Unit = macro Macros.Logger.infoThrowableImpl
    def debug(msg: String): Unit = macro Macros.Logger.debugImpl
    def debug(msg: String, e: Throwable): Unit = macro Macros.Logger.debugThrowableImpl
    def trace(msg: String): Unit = macro Macros.Logger.traceImpl
    def trace(msg: String, e: Throwable): Unit = macro Macros.Logger.traceThrowableImpl
  }
  object Logger {
    type Context = blackbox.Context {
      type PrefixType = Logger
    }
    def errorImpl(c: Context)(msg: c.Expr[String]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isErrorEnabled) $prefix.underlying.error($msg)")
    }
    def errorThrowableImpl(c: Context)(msg: c.Expr[String], e: c.Expr[Throwable]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isErrorEnabled) $prefix.underlying.error($msg, $e)")
    }
    def warnImpl(c: Context)(msg: c.Expr[String]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isWarnEnabled) $prefix.underlying.warn($msg)")
    }
    def warnThrowableImpl(c: Context)(msg: c.Expr[String], e: c.Expr[Throwable]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isWarnEnabled) $prefix.underlying.warn($msg, $e)")
    }
    def infoImpl(c: Context)(msg: c.Expr[String]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isInfoEnabled) $prefix.underlying.info($msg)")
    }
    def infoThrowableImpl(c: Context)(msg: c.Expr[String], e: c.Expr[Throwable]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isInfoEnabled) $prefix.underlying.info($msg, $e)")
    }
    def debugImpl(c: Context)(msg: c.Expr[String]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isDebugEnabled) $prefix.underlying.debug($msg)")
    }
    def debugThrowableImpl(c: Context)(msg: c.Expr[String], e: c.Expr[Throwable]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isDebugEnabled) $prefix.underlying.debug($msg, $e)")
    }
    def traceImpl(c: Context)(msg: c.Expr[String]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isTraceEnabled) $prefix.underlying.trace($msg)")
    }
    def traceThrowableImpl(c: Context)(msg: c.Expr[String], e: c.Expr[Throwable]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isTraceEnabled) $prefix.underlying.trace($msg, $e)")
    }
  }
}