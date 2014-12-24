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

import akka.util._
import scala.util.control.ControlThrowable
import scala.language.experimental.macros
import scalaz._, Scalaz._
import org.threeten.bp._

sealed abstract class Variant {
  import Variant._
  private[this] def castError(t: String) = sys.error(s"Expected $t but $this found")
  def asBool = this match {
    case x: Bool => x
    case _ => castError("Bool")
  }
  def asInt32 = this match {
    case x: Int32 => x
    case _ => castError("Int32")
  }
  def asInt64 = this match {
    case x: Int64 => x
    case Int32(x) => Int64(x)
    case _ => castError("Int64")
  }
  def asVarInt = this match {
    case x: VarInt => x
    case Int64(x) => VarInt(BigInt(x))
    case Int32(x) => VarInt(BigInt(x))
    case _ => castError("VarInt")
  }
  def asBinary = this match {
    case x: Binary => x
    case _ => castError("Binary")
  }
  def asDecimal = this match {
    case x: Decimal => x
    case Int64(x) => Decimal(BigDecimal(x))
    case Int32(x) => Decimal(BigDecimal(x))
    case Float64(x) => Decimal(BigDecimal(x))
    case Float32(x) => Decimal(BigDecimal(x.toDouble))
    case _ => castError("Decimal")
  }
  def asFloat64 = this match {
    case x: Float64 => x
    case Int32(x) => Float64(x)
    case Float32(x) => Float64(x)
    case _ => castError("Float64")
  }
  def asFloat32 = this match {
    case x: Float32 => x
    case _ => castError("Float32")
  }
  def asInetAddress = this match {
    case x: InetAddress => x
    case _ => castError("InetAddress")
  }
  def asSequence = this match {
    case x: Sequence => x
    case _ => castError("Sequence")
  }
  def asObject = this match {
    case x: Object => x
    case _ => castError("Object")
  }
  def asText = this match {
    case x: Text => x
    case _ => castError("Text")
  }
  def asTimestamp = this match {
    case x: Timestamp => x
    case _ => castError("Timestamp")
  }
  def asUUID = this match {
    case x: UUID => x
    case _ => castError("UUID")
  }
}
object Variant {
  trait Reflect extends saare.Reflect {
    import c.universe._
    def weakCast[A: c.WeakTypeTag](src: c.Tree): c.Tree = checking(weakTypeOf[Validation[String, A]]) {
      val dst = weakTypeOf[A]
      val simpleConverted = List(weakTypeOf[Bool], weakTypeOf[Int32], weakTypeOf[Binary], weakTypeOf[InetAddress], weakTypeOf[Sequence], weakTypeOf[Object], weakTypeOf[Text], weakTypeOf[Timestamp], weakTypeOf[UUID])
      val dstInfo = reflectCaseClass[A]
      val x = TermName(c.freshName("x"))
      val failure = q"""("Cannot convert " + $x + " to " + ${dst.typeSymbol.name.decodedName.toString}).failure[${weakTypeOf[A]}]"""
      val tree = if (dst <:< weakTypeOf[Undefined.type])
        q"_root_.saare.Variant.Undefined.success[${weakTypeOf[String]}]"
      else if (simpleConverted.exists(x => dst <:< x))
        q"""$src match {
          case $x: $dst => $x.success[${weakTypeOf[String]}]
          case $x => $failure
        }"""
      else if (dst <:< weakTypeOf[Int64])
        q"""$src match {
          case $x: ${weakTypeOf[Int64]} => $x.success[${weakTypeOf[String]}]
          case ${companion[Int32]}($x) => ${companion[Int64]}(x).success[${weakTypeOf[String]}]
          case $x => $failure
        }"""
      else if (dst <:< weakTypeOf[VarInt])
        q"""$src match {
          case $x: ${weakTypeOf[VarInt]} => $x.success[${weakTypeOf[String]}]
          case ${companion[Int64]}($x) => ${companion[VarInt]}(_root_.scala.math.BigInt($x)).success[${weakTypeOf[String]}]
          case ${companion[Int32]}($x) => ${companion[VarInt]}(_root_.scala.math.BigInt($x)).success[${weakTypeOf[String]}]
          case $x => $failure
        }"""
      else if (dst <:< weakTypeOf[Decimal])
        q"""$src match {
          case $x: ${weakTypeOf[Decimal]} => $x
          case ${companion[Int64]}($x) => ${companion[Decimal]}(_root_.scala.math.BigDecimal($x)).success[${weakTypeOf[String]}]
          case ${companion[Int32]}($x) => ${companion[Decimal]}(_root_.scala.math.BigDecimal($x)).success[${weakTypeOf[String]}]
          case ${companion[Float64]}($x) => ${companion[Decimal]}(_root_.scala.math.BigDecimal($x)).success[${weakTypeOf[String]}]
          case $x => $failure
        }"""
      else if (dst <:< weakTypeOf[Float64])
        q"""$src match {
          case $x: ${weakTypeOf[Float64]} => $x.success[${weakTypeOf[String]}]
          case ${companion[Int32]}($x) => ${companion[Float64]}($x).success[${weakTypeOf[String]}]
          case $x => $failure
        }"""
      else warnAndBail(s"Variant.weakCast[${dst.typeSymbol.name.decodedName.toString}] is not supported (yet).")
      q"""
      {
        import scalaz._, Scalaz._
        $tree
      }
      """
    }
  }
  class Macro(val c: scala.reflect.macros.whitebox.Context) extends Reflect
  def weakCast[A](src: Variant): Validation[String, A] = macro Macro.weakCast[A]
  case object Undefined extends Variant
  case class Bool(value: Boolean) extends Variant
  case class Int32(value: Int) extends Variant
  case class Int64(value: Long) extends Variant
  case class VarInt(value: scala.math.BigInt) extends Variant
  case class Binary(value: ByteString) extends Variant
  case class Decimal(value: BigDecimal) extends Variant
  case class Float64(value: Double) extends Variant
  case class Float32(value: Float) extends Variant
  case class InetAddress(value: java.net.InetAddress) extends Variant
  case class Sequence(value: scala.collection.immutable.Seq[Variant]) extends Variant {
    def apply(i: Int): Variant = value.lift(i).getOrElse(Undefined)
  }
  case class Object(value: scala.collection.immutable.ListMap[String, Variant]) extends Variant {
    def apply(x: String): Variant = value.get(x).getOrElse(Undefined)
  }
  case class Text(value: String) extends Variant
  case class Timestamp(value: Instant) extends Variant
  case class UUID(value: java.util.UUID) extends Variant
}

