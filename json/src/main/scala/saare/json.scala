/*Copyright 2013-2014 sumito3478 <sumito3478@gmail.com>

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
package json

import scala.language.dynamics
import scala.util._
import scala.reflect._
import scala.language.experimental.macros
import scala.reflect.macros._
import scala.util.control._
import scala.util.control.Exception._
import saare._, Saare._
import Json._

sealed trait JValue
case class JNumber(value: BigDecimal) extends JValue
object JNumber {
  def apply(value: Int): JNumber = JNumber(BigDecimal(value))
  def apply(value: Long): JNumber = JNumber(BigDecimal(value))
  def apply(value: Double): JNumber = JNumber(BigDecimal(value))
  def apply(value: BigInt): JNumber = JNumber(BigDecimal(value))
}
case class JBoolean(value: Boolean) extends JValue
case class JString(value: String) extends JValue
case class JArray(value: Seq[JValue]) extends JValue
case class JObject(value: Map[String, JValue]) extends JValue
case object JNull extends JValue
object JNothing extends JValue

class Lens(private val get: JValue => Option[JValue], private val set: JValue => JValue => Option[JValue]) extends Dynamic {
  def selectDynamic(name: String) = {
    val get = this.get.andThen {
      case Some(JObject(xs)) => xs.get(name)
      case Some(JArray(xs)) => name.parseInt().flatMap(xs lift _)
      case _ => None
    }
    val set = (x: JValue) => (y: JValue) => {
      this.get(x) match {
        case Some(JObject(xs)) => this.set(x)(JObject(xs.updated(name, y)))
        case Some(JArray(xs)) => {
          for {
            index <- name.parseInt()
            if xs.isDefinedAt(index)
            ret <- this.set(x)(JArray(xs.updated(index, y)))
          } yield ret
        }
        case _ => None
      }
    }
    new Lens(get, set)
  }
}

trait Encoder[A] {
  def encode: A => JValue
}
trait Decoder[A] {
  def decode: JValue => Option[A]
}
case class Codec[A](encode: A => JValue, decode: JValue => Option[A]) extends Encoder[A] with Decoder[A]

object Lens {
  val get: Lens => JValue => Option[JValue] = x => x.get
  val set: Lens => JValue => JValue => Option[JValue] = x => x.set
}

object Json {
  val parse: String => Try[JValue] = x => Jackson.readJValue(x)

  val print: JValue => String = x => Jackson.writeJValue(x)

  val pretty_print: JValue => String = x => Jackson.prettyPrintJValue(x)

  def lens = new Lens(get = (x: JValue) => Some(x), set = (x: JValue) => (y: JValue) => Some(y))

  implicit def JValueCodec[A <: JValue: ClassTag] = Codec[A](encode = x => x, decode = as[A])

  def encode[A: Encoder](x: A) = implicitly[Encoder[A]].encode(x)

  def decode[A: Decoder](x: JValue) = implicitly[Decoder[A]].decode(x)

  implicit def IntCodec = Codec[Int](encode = x => JNumber(x), decode = as[JNumber].andThen(_.flatMap { case JNumber(x) => x.toIntOption }))
  implicit def LongCodec = Codec[Long](encode = x => JNumber(x), decode = as[JNumber].andThen(_.flatMap { case JNumber(x) => x.toLongOption }))
  implicit def DoubleCodec = Codec[Double](encode = x => JNumber(x), decode = as[JNumber].andThen(_.flatMap { case JNumber(x) => x.toDoubleOption }))
  implicit def BigIntCodec = Codec[BigInt](encode = x => JNumber(x), decode = as[JNumber].andThen(_.flatMap { case JNumber(x) => x.toBigIntOption }))
  implicit def BigDecimalCodec = Codec[BigDecimal](encode = x => JNumber(x), decode = as[JNumber].andThen(_.map { case JNumber(x) => x }))
  implicit def BooleanCodec = Codec[Boolean](encode = x => JBoolean(x), decode = as[JBoolean].andThen(_.map(_.value)))
  implicit def StringCodec = Codec[String](encode = x => JString(x), decode = as[JString].andThen(_.map(_.value)))
  implicit def OptionCodec[A: Codec] = Codec[Option[A]](encode = x => x.map(encode(_)).getOrElse(JNothing), decode = { case JNothing | JNull => Some(None); case x => decode[A](x).map(Some(_)) })
  implicit def SeqCodec[A: Codec] = {
    val d: JValue => Option[Seq[A]] = {
      case JArray(xs) =>
        xs.foldLeft[Option[Seq[A]]](Some(Seq())) {
          case (Some(xs), value) => for (x <- decode[A](value)) yield xs :+ x
          case (None, _) => None
        }
      case _ => None
    }
    Codec[Seq[A]](encode = x => JArray(x.map(encode(_))), decode = d)
  }
  implicit def MapCodec[A: Codec] = {
    val d: JValue => Option[Map[String, A]] = {
      case JObject(xs) =>
        xs.foldLeft[Option[Map[String, A]]](Some(Map())) {
          case (Some(xs), value) => for (x <- decode[A](value._2)) yield xs + (value._1 -> x)
          case (None, _) => None
        }
      case _ => None
    }
    Codec[Map[String, A]](encode = x => JObject(x.mapValues(encode(_))), decode = d)
  }
  def CaseClassCodecImpl[A: c.WeakTypeTag](c: blackbox.Context): c.Expr[json.Codec[A]] = {
    import c.universe._
    val aType = weakTypeOf[A]
    val typeParams = aType.typeSymbol.asClass.typeParams
    val TypeRef(_, _, actualTypeParams) = aType
    val typeName = aType.typeSymbol
    val companion = {
      def f: c.Tree = {
        val symbol = aType.typeSymbol.companion.orElse {
          // due to SI-7567, if A is a inner class, companion returns NoSymbol...
          // as I don't know how to avoid SI-7567, let's fall back into an anaphoric macro!
          return c.parse(typeName.name.decodedName.toString)
        }
        q"$symbol"
      }
      f
    }
    val params = aType.decls.collect { case m: MethodSymbol if m.isCaseAccessor => m }.toList
    val returnTypes = for (param <- params) yield {
      val returnType = param.returnType
      // if the return type is one of case class type parameter, replace with the actual type
      typeParams.indexOf(returnType.typeSymbol) match {
        case -1 => returnType
        case i => actualTypeParams(i)
      }
    }
    val unapplyParams =
      for (i <- 0 until params.size) yield {
        val param = params(i)
        val e = c.parse(s"_root_.saare.json.Json.encode(x.${param.name.decodedName.toString})") // I don't understand why use of quasiquote here doen't work...
        q"""(${param.name.decodedName.toString}, $e)"""
      }
    val unapply = q"Map(..$unapplyParams).filterNot { case (_, v) => v == JNothing }"
    val applyParams = for (i <- 0 until params.size) yield {
      val param = params(i)
      val returnType = returnTypes(i)
      q"""_root_.saare.json.Json.decode[$returnType](xs.getOrElse(${param.name.decodedName.toString}, JNothing)).get"""
    }
    val apply = q"$companion(..$applyParams)"
    val src = q"""
{
  val encode: $aType => JValue = x => JObject($unapply)
  val decode: JValue => Option[$aType] = x => x match {
    case JObject(xs) => scala.util.control.Exception.allCatch[$aType].opt($apply)
    case _ => None
  }
  Codec[$aType](encode = encode, decode = decode)
}
"""
    // println(src) // debug print
    c.Expr[Codec[A]](src)
  }
  implicit def CaseClassCodec[A]: Codec[A] = macro CaseClassCodecImpl[A]
}
