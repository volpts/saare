package saare

import scala.language.experimental.macros
import scala.reflect.macros.Context

object Macros {
  object Logger {
    type Context = scala.reflect.macros.Context {
      type PrefixType = { val underlying: org.slf4j.Logger }
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
      c.Expr[Unit](q"if ($prefix.underlying.isErrorEnabled) $prefix.underlying.error($msg)")
    }
    def warnThrowableImpl(c: Context)(msg: c.Expr[String], e: c.Expr[Throwable]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isErrorEnabled) $prefix.underlying.error($msg, $e)")
    }
    def infoImpl(c: Context)(msg: c.Expr[String]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isErrorEnabled) $prefix.underlying.error($msg)")
    }
    def infoThrowableImpl(c: Context)(msg: c.Expr[String], e: c.Expr[Throwable]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isErrorEnabled) $prefix.underlying.error($msg, $e)")
    }
    def debugImpl(c: Context)(msg: c.Expr[String]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isErrorEnabled) $prefix.underlying.error($msg)")
    }
    def debugThrowableImpl(c: Context)(msg: c.Expr[String], e: c.Expr[Throwable]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isErrorEnabled) $prefix.underlying.error($msg, $e)")
    }
    def traceImpl(c: Context)(msg: c.Expr[String]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isErrorEnabled) $prefix.underlying.error($msg)")
    }
    def traceThrowableImpl(c: Context)(msg: c.Expr[String], e: c.Expr[Throwable]): c.Expr[Unit] = {
      import c.universe._
      val prefix = q"${c.prefix}"
      c.Expr[Unit](q"if ($prefix.underlying.isErrorEnabled) $prefix.underlying.error($msg, $e)")
    }
  }
}