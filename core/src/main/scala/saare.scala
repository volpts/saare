package object saare {
  import scala.util.control.Exception._
  import scala.reflect._

  implicit class AnyOps[A](val self: A) extends AnyVal {
    def |>[B](f: A => B) = f(self)
    def #|>[B](f: A => B) = f(self)
  }

  implicit class BigDecimalW(val self: BigDecimal) extends AnyVal {
    def toIntOption = allCatch[Int].opt(self.toIntExact)

    def toLongOption = allCatch[Long].opt(self.toLongExact)

    def toBigIntOption = self.toBigIntExact

    def toDoubleOption = {
      val d = self.toDouble
      if (BigDecimal(d) == self) Some(d) else None
    }
  }

  implicit class StringOps(val self: String) extends AnyVal {
    def parseInt(radix: Int = 10): Option[Int] = allCatch[Int].opt(java.lang.Integer.parseInt(self, radix))
  }
}