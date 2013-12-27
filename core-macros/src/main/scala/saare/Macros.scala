/*Copyright 2013 sumito3478 <sumito3478@gmail.com>

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
package saare

import scala.language.experimental.macros
import scala.reflect.macros.Context

object Macros {
  trait TypeNameable[A] {
    def fullName: String
    def name: String
  }
  def materializeTypeNameableImpl[A: c.WeakTypeTag](c: Context): c.Expr[Macros.TypeNameable[A]] = {
    import c.universe._
    val a = weakTypeOf[A]
    val typeNameable = weakTypeOf[TypeNameable[A]]
    c.Expr[Macros.TypeNameable[A]](q"new $typeNameable { def fullName = ${a.typeSymbol.fullName} ; def name = ${a.typeSymbol.name.decoded} }")
  }
  implicit def materializeTypeNameable[A] = macro materializeTypeNameableImpl[A]

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