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
package object saare {
  import scala.util.control.Exception._
  import scala.reflect._

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