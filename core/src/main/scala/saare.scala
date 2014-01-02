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
import java.util.concurrent.atomic._
import scala.concurrent._

trait Logging[Repr] {
  self =>
  private[this] val _logger = new AtomicReference[Macros.Logger]()
  def logger(implicit typeNameable: Macros.TypeNameable[Repr]): Macros.Logger = {
    val ret = _logger.get
    if (ret == null) {
      val newLogger = new Macros.Logger {
        val underlying = org.slf4j.LoggerFactory.getLogger(Saare.fullTypeName[Repr])
      }
      _logger.compareAndSet(null, newLogger)
      _logger.get
    } else ret
  }
}
trait Disposable[Repr] extends Logging[Repr] {
  def disposeInternal: Unit

  private[this] val disposed = new AtomicBoolean(false)

  def dispose = if (disposed.compareAndSet(false, true)) disposeInternal

  def disposeAsync(implicit ec: ExecutionContext): Option[Future[Unit]] =
    if (disposed.compareAndSet(false, true))
      Some(Future {
        dispose
      })
    else None

  override def finalize =
    if (!disposed.get) {
      logger.warn(s"$this - calling dispose from finalizer!")
      dispose
    }
}
trait AsDisposable[A] {
  def asDisposable(x: A): Disposable[_]
}
object Saare {
  implicit class AnyOps[A](val self: A) extends AnyVal {
    def |>[B](f: A => B) = f(self)
    def #|>[B](f: A => B) = f(self)
  }
  implicit class FunctionOps[A, B](val self: A => B) extends AnyVal {
    def |<(x: A) = self(x)
    def #|<(x: A) = self(x)
  }

  def as[A: ClassTag]: Any => Option[A] = {
    case x if implicitly[ClassTag[A]].runtimeClass isAssignableFrom x.getClass => Some(x.asInstanceOf[A])
    case _ => None
  }

  def typeName[A: Macros.TypeNameable] = implicitly[Macros.TypeNameable[A]].name

  def fullTypeName[A: Macros.TypeNameable] = implicitly[Macros.TypeNameable[A]].fullName

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
  private[this] val le = (0xcafebabe >>> 16) == 0xcafe
  private[this] val be = !le
  implicit class ShortOps(val self: Short) extends AnyVal {
    def bswap: Short = java.lang.Short.reverseBytes(self)
    def toLE: Short = if (be) bswap else self
    def toBE: Short = if (le) bswap else self
    def fromLE: Short = if (be) bswap else self
    def fromBE: Short = if (le) bswap else self
  }
  implicit class IntOps(val self: Int) extends AnyVal {
    def bswap: Int = java.lang.Integer.reverseBytes(self)
    def toLE: Int = if (be) bswap else self
    def toBE: Int = if (le) bswap else self
    def fromLE: Int = if (be) bswap else self
    def fromBE: Int = if (le) bswap else self
  }
  implicit class LongOps(val self: Long) extends AnyVal {
    def bswap: Long = java.lang.Long.reverseBytes(self)
    def toLE: Long = if (be) bswap else self
    def toBE: Long = if (le) bswap else self
    def fromLE: Long = if (be) bswap else self
    def fromBE: Long = if (le) bswap else self
  }

  def asDisposable[A: AsDisposable]: A => Disposable[_] = x => implicitly[AsDisposable[A]].asDisposable(x)

  def dispose[A: AsDisposable]: A => Unit = x => (x |> asDisposable).dispose

  def using[A: AsDisposable, B](x: A)(f: A => B): B = try f(x) finally x |> dispose

  def disposing[A: AsDisposable, B](x: A)(f: A => Future[B]): Future[B] = {
    val fut = try f(x)
    catch {
      case e: Throwable => {
        // f failed to return future
        x |> dispose
        throw e
      }
    }
    fut transform (y => { x |> dispose; y }, e => { x |> dispose; e })
  }

  implicit def disposableIsDisposable[A](implicit ev: A => Disposable[_]) = new AsDisposable[A] {
    def asDisposable(x: A) = x
  }
  implicit val autoClosableIsDisposable = new AsDisposable[AutoCloseable] {
    def asDisposable(x: AutoCloseable) = new Disposable[AutoCloseable] {
      def disposeInternal = x.close
    }
  }
}
