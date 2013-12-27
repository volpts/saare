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
import scala.util.control.Exception._
import scala.reflect._
import scala.language.experimental.macros

trait Logging {
  self =>
  object logger {
    val underlying = org.slf4j.LoggerFactory.getLogger(self.getClass.getName)
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
}
object Saare {
  implicit class AnyOps[A](val self: A) extends AnyVal {
    def |>[B](f: A => B) = f(self)
    def #|>[B](f: A => B) = f(self)
  }

  def as[A: ClassTag]: Any => Option[A] = {
    case x if implicitly[ClassTag[A]].runtimeClass isAssignableFrom x.getClass => Some(x.asInstanceOf[A])
    case _ => None
  }

  implicit class BigDecimalOps(val self: BigDecimal) extends AnyVal {
    def toIntOption = allCatch[Int].opt(self.toIntExact)

    def toLongOption = allCatch[Long].opt(self.toLongExact)

    def toBigIntOption = self.toBigIntExact

    def toDoubleOption = {
      val d = self.toDouble
      if (BigDecimal(d) == self) Some(d) else None
    }
  }

  implicit class StringOps(val self: String) extends AnyVal {
    def parseInt(radix: Int = 10) = allCatch[Int].opt(java.lang.Integer.parseInt(self, radix))
    def parseLong(radix: Int = 10) = allCatch[Long].opt(java.lang.Long.parseLong(self, radix))
    def parseDouble = allCatch[Double].opt(java.lang.Double.parseDouble(self))
    def parseBigInt(radix: Int = 10) = allCatch[BigInt].opt(BigInt(self, radix))
    def parseBigDecimal: Option[BigDecimal] = allCatch[BigDecimal].opt(BigDecimal(self))
  }
}
