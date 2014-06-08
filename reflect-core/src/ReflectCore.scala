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

import scala.language.experimental.macros
import scala.reflect.macros._
import scala.collection.immutable._
import akka.util._

object ReflectCore {
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
    case class Timestamp(value: org.threeten.bp.Instant) extends Variant
    case class UUID(value: java.util.UUID) extends Variant
  }
  implicit class ContextW(val c: blackbox.Context) {
    import c.universe._
    def warn(message: String) = c.warning(c.enclosingPosition, message)
    def warnAndBail(message: String) = {
      c.warn(message)
      sys.error(message)
    }
    def bail(message: String) = {
      c.error(c.enclosingPosition, message)
      sys.error(message)
    }
    def typecheck(tree: Tree, expected: Type): Tree = try c.typecheck(tree = tree, pt = expected) catch {
      case e: TypecheckException =>
        println(s"Typecheck failed! The expanded tree is:\n$tree")
        throw e
    }
  }
  case class CaseClassInfo(params: Seq[(Universe#MethodSymbol, Universe#Type)], companion: Universe#Tree)
  def reflectCaseClassInfo(c: blackbox.Context)(`type`: c.Type): CaseClassInfo = {
    import c.universe._
    val typeSymbol = `type`.typeSymbol
    val typeName = typeSymbol.name.decodedName.toString
    val `class` = typeSymbol.asClass
    if (!`class`.isCaseClass) {
      c.warnAndBail(s"Tried to reflect $typeName as a case class but $typeName seems not be a case class!")
    }
    val aTypeParams = `class`.typeParams
    val TypeRef(_, _, actualTypeParams) = `type`
    val aCompanion = {
      def f: Tree = {
        val symbol = typeSymbol.companion.orElse {
          // due to SI-7567, if A is a inner class, companion returns NoSymbol...
          // as I don't know how to avoid SI-7567, let's fall back into an anaphoric macro!
          c.warn(s"Due to SI-7567, cannot get the companion of $typeName. Falling back to an anaphoric macro.")
          return c.parse(typeName)
        }
        c.parse(symbol.fullName)
      }
      f
    }
    val paramMethods = `type`.decls.collect { case m: MethodSymbol if m.isCaseAccessor => m }.toList
    val paramTypes = for (paramMethod <- paramMethods) yield {
      val paramType = paramMethod.returnType
      // if the return type is one of case class type parameters, replace with the actual type
      aTypeParams.indexOf(paramType.typeSymbol) match {
        case -1 => paramType
        case i => actualTypeParams(i)
      }
    }
    CaseClassInfo(params = paramMethods zip paramTypes, companion = aCompanion)
  }
  def readVariantImpl[A: c.WeakTypeTag](c: blackbox.Context)(x: c.Tree): c.Tree = {
    import c.universe._
    import Variant._
    val `type` = weakTypeOf[A]
    val reflectCore = q"_root_.saare.ReflectCore"
    val variant = TermName(c.freshName("x"))
    val tree = if (`type` <:< weakTypeOf[None.type]) {
      q"${weakTypeOf[Variant].companion}.Undefined"
    } else if (`type` <:< weakTypeOf[Some[_]]) {
      val TypeRef(_, _, actualTypeParams) = `type`
      val tp = actualTypeParams.head
      q" _root_.scala.Some($reflectCore.readVariant[$tp]($variant))"
    } else if (`type` <:< weakTypeOf[Option[_]]) {
      val TypeRef(_, _, actualTypeParams) = `type`
      val tp = actualTypeParams.head
      val ret = q"(if ($variant == $reflectCore.Variant.Undefined) _root_.scala.None else _root_.scala.Some($reflectCore.readVariant[$tp]($variant)))"
      ret
    } else if (`type`.typeSymbol.asClass.isCaseClass) {
      val typeInfo = reflectCaseClassInfo(c)(weakTypeOf[A])
      def loop(typeInfo: CaseClassInfo, seq: Tree): Seq[Tree] = {
        val params = typeInfo.params
        for (i <- 0 until params.size) yield {
          val (method, paramType) = params(i)
          val methodName = method.name.decodedName.toString
          val variant = q"$seq($i)"
          if (paramType.typeSymbol.asClass.isCaseClass) {
            val paramTypeInfo = reflectCaseClassInfo(c)(paramType.asInstanceOf[Type])
            val children = loop(paramTypeInfo, q"$variant.asSequence")
            q""" ${paramTypeInfo.companion.asInstanceOf[Tree]}(..$children) """
          } else {
            q"$reflectCore.readVariant[${paramType.asInstanceOf[Type]}]($variant)"
          }
        }
      }
      val children = loop(typeInfo, q"$variant.asSequence")
      q""" ${typeInfo.companion.asInstanceOf[Tree]}(..$children) """
    } else if (`type` =:= weakTypeOf[Boolean]) q"$variant.asBool.value"
    else if (`type` =:= weakTypeOf[Int]) q"$variant.asInt32.value"
    else if (`type` =:= weakTypeOf[Long]) q"$variant.asInt64.value"
    else if (`type` =:= weakTypeOf[BigInt]) q"$variant.asVarInt.value"
    else if (`type` =:= weakTypeOf[ByteString]) q"$variant.asBinary.value"
    else if (`type` =:= weakTypeOf[BigDecimal]) q"$variant.asDecimal.value"
    else if (`type` =:= weakTypeOf[Double]) q"$variant.asFloat64.value"
    else if (`type` =:= weakTypeOf[Float]) q"$variant.asFloat32.value"
    else if (`type` =:= weakTypeOf[java.net.InetAddress]) q"$variant.asInetAddress.value"
    else if (`type` =:= weakTypeOf[Seq[_]]) {
      val TypeRef(_, _, actualTypeParams) = `type`
      val tp = actualTypeParams.head
      q"${q"$variant.asSequence.value"}.map($reflectCore.readVariant[$tp](_))"
    } else if (`type` =:= weakTypeOf[Map[String, _]]) {
      val TypeRef(_, _, actualTypeParams) = `type`
      val tp = actualTypeParams(1)
      q"${q"$variant.asObject.value"}.mapValues($reflectCore.readVariant[$tp](_))"
    } else if (`type` =:= weakTypeOf[String]) q"$variant.asText.value"
    else if (`type` =:= weakTypeOf[java.util.UUID]) q"$variant.asUUID.value"
    else if (`type` <:< weakTypeOf[org.threeten.bp.Instant]) q"$variant.asTimestamp.value"
    else sys.error(s"Type ${`type`} is not (yet) supported by ReflectCore#readVariant!")
    val ret = q"{ val $variant = $x; $tree }"
    typecheck(c)(ret, weakTypeOf[A])
    ret
  }
  def writeVariantImpl[A: c.WeakTypeTag](c: blackbox.Context)(x: c.Tree): c.Tree = {
    import c.universe._
    import Variant._
    val `type` = weakTypeOf[A]
    val reflectCore = q"_root_.saare.ReflectCore"
    val tree = if (`type` <:< weakTypeOf[None.type]) q"${weakTypeOf[Variant].typeSymbol.companion}.Undefined"
    else if (`type` <:< weakTypeOf[Some[_]]) {
      val TypeRef(_, _, actualTypeParams) = `type`
      val tp = actualTypeParams.head
      val x2 = TermName(c.freshName("x"))
      q"$reflectCore.writeVariant[$tp]($x.get)"
    } else if (`type` <:< weakTypeOf[Option[_]]) {
      val TypeRef(_, _, actualTypeParams) = `type`
      val tp = actualTypeParams.head
      val x2 = TermName(c.freshName("x"))
      q"($x match { case Some($x2) => $reflectCore.writeVariant[$tp]($x2) ; case None => ${weakTypeOf[Variant].typeSymbol.companion}.Undefined})"
    } else if (`type`.typeSymbol.asClass.isCaseClass) {
      val typeInfo = reflectCaseClassInfo(c)(weakTypeOf[A])
      def loop(typeInfo: CaseClassInfo, x: Tree): Seq[Tree] = {
        val params = typeInfo.params
        for (i <- 0 until params.size) yield {
          val (method, paramType) = params(i)
          val methodName = method.name.decodedName.toString
          if (paramType.typeSymbol.asClass.isCaseClass) {
            val paramTypeInfo = reflectCaseClassInfo(c)(paramType.asInstanceOf[Type])
            val children = loop(paramTypeInfo, q"$x.${method.asInstanceOf[MethodSymbol]}")
            q""" ${weakTypeOf[Variant.Sequence].typeSymbol.companion}(${weakTypeOf[Seq[_]].typeSymbol.companion}(..$children)) """
          } else {
            q"$reflectCore.writeVariant($x.${method.asInstanceOf[MethodSymbol]})"
          }
        }
      }
      val children = loop(typeInfo, x)
      q""" ${weakTypeOf[Variant.Sequence].typeSymbol.companion}(${weakTypeOf[Seq[_]].typeSymbol.companion}(..$children)) """
    } else if (`type` =:= weakTypeOf[Boolean]) q"${weakTypeOf[Bool].typeSymbol.companion}($x)"
    else if (`type` =:= weakTypeOf[Int]) q"${weakTypeOf[Int32].typeSymbol.companion}($x)"
    else if (`type` =:= weakTypeOf[Long]) q"${weakTypeOf[Int64].typeSymbol.companion}($x)"
    else if (`type` =:= weakTypeOf[BigInt]) q"${weakTypeOf[VarInt].typeSymbol.companion}($x)"
    else if (`type` =:= weakTypeOf[ByteString]) q"${weakTypeOf[Binary].typeSymbol.companion}($x)"
    else if (`type` =:= weakTypeOf[BigDecimal]) q"${weakTypeOf[Decimal].typeSymbol.companion}($x)"
    else if (`type` =:= weakTypeOf[Double]) q"${weakTypeOf[Float64].typeSymbol.companion}($x)"
    else if (`type` =:= weakTypeOf[Float]) q"${weakTypeOf[Float32].typeSymbol.companion}($x)"
    else if (`type` =:= weakTypeOf[java.net.InetAddress]) q"${weakTypeOf[InetAddress].typeSymbol.companion}($x)"
    else if (`type` =:= weakTypeOf[Seq[_]]) {
      val TypeRef(_, _, actualTypeParams) = `type`
      val tp = actualTypeParams.head
      q"${weakTypeOf[Sequence].typeSymbol.companion}($x.map($reflectCore.writeVariant[$tp](_)))"
    } else if (`type` =:= weakTypeOf[Map[String, _]]) {
      val TypeRef(_, _, actualTypeParams) = `type`
      val tp = actualTypeParams(1)
      q"${weakTypeOf[Object].typeSymbol.companion}($x.mapValues($reflectCore.writeVariant[$tp](_)))"
    } else if (`type` =:= weakTypeOf[String]) q"${weakTypeOf[Text].typeSymbol.companion}($x)"
    else if (`type` =:= weakTypeOf[java.util.UUID]) q"${weakTypeOf[UUID].typeSymbol.companion}($x)"
    else if (`type` =:= weakTypeOf[org.threeten.bp.Instant]) q"${weakTypeOf[Timestamp].typeSymbol.companion}($x)"
    else sys.error(s"Type ${`type`} is not (yet) supported by ReflectCore#readVariant!")
    typecheck(c)(tree, weakTypeOf[Variant])
    tree
  }
  sealed abstract class MapDecoder[Tree <: Universe#Tree]
  case class MapDecoderLeaf[Tree <: Universe#Tree](tree: Tree => Tree) extends MapDecoder[Tree]
  case class MapDecoderSeq[Tree <: Universe#Tree](reflector: DecodeReflector[Tree]) extends MapDecoder[Tree]
  case class MapDecoderLazy[Tree <: Universe#Tree](tree: Tree => Tree, reflector: () => DecodeReflector[Tree]) extends MapDecoder[Tree]
  sealed abstract class SeqDecoder[Tree <: Universe#Tree]
  case class SeqDecoderLeaf[Tree <: Universe#Tree](tree: Tree => Tree) extends SeqDecoder[Tree]
  case class SeqDecoderSeq[Tree <: Universe#Tree](reflector: DecodeReflector[Tree]) extends SeqDecoder[Tree]
  abstract class DecodeReflector[Tree <: Universe#Tree] {
    def decodedType: Universe#Type
    def decoderByName: String => MapDecoder[Tree]
    def decoderByIndex: Int => SeqDecoder[Tree]
  }
  abstract class EncodeReflector[Tree <: Universe#Tree] {
    def encoderByName(implicit decodeReflector: DecodeReflector[Tree]): Tree => Tree
    def encoderByIndex(implicit decodeReflector: DecodeReflector[Tree]): Tree => Tree
  }
  abstract class CaseClassTypeInfo[MethodSymbol <: Universe#MethodSymbol, Type <: Universe#Type, Tree <: Universe#Tree] {
    def params: Seq[(MethodSymbol, Type)]
    def companion: Tree
  }
  protected[this] abstract class JavishClassTypeInfo[MethodSymbol <: Universe#MethodSymbol, Type <: Universe#Type] {
    def props: Seq[(MethodSymbol, Type)]
  }
  def reflectJavishClass(c: blackbox.Context)(`type`: c.Type): ListMap[c.universe.MethodSymbol, c.universe.Type] = {
    import c.universe._
    val typeSymbol = `type`.typeSymbol
    val typeName = typeSymbol.name.decodedName.toString
    val `class` = typeSymbol.asClass
    if (`class`.isCaseClass)
      c.warn(s"Trying to reflect $typeName as a javish class but seems to be a case class!")
    val typeSignature = `class`.typeSignature
    val propMethods = `type`.decls.collect { case m: MethodSymbol if (m.name.decodedName.toString.startsWith("get") || m.name.decodedName.toString.startsWith("is")) && !m.paramLists.exists(!_.isEmpty) => m }
    val aTypeParams = `class`.typeParams
    val TypeRef(_, _, actualTypeParams) = `type`
    val propTypes = for (propMethod <- propMethods) yield {
      val propType = propMethod.returnType
      // if the return type is one of case class type parameters, replace with the actual type
      aTypeParams.indexOf(propType.typeSymbol) match {
        case -1 => propType
        case i => actualTypeParams(i)
      }
    }
    ListMap((propMethods zip propTypes).toSeq: _*)
  }
  def reflectCaseClass(c: blackbox.Context)(`type`: c.Type): CaseClassTypeInfo[c.universe.MethodSymbol, c.universe.Type, c.universe.Tree] = {
    import c.universe._
    val typeSymbol = `type`.typeSymbol
    val typeName = typeSymbol.name.decodedName.toString
    val `class` = typeSymbol.asClass
    if (!`class`.isCaseClass) {
      c.warnAndBail(s"Tried to reflect $typeName as a case class but $typeName seems not be a case class!")
    }
    val aTypeParams = `class`.typeParams
    val TypeRef(_, _, actualTypeParams) = `type`
    val aCompanion = {
      def f: Tree = {
        val symbol = typeSymbol.companion.orElse {
          // due to SI-7567, if A is a inner class, companion returns NoSymbol...
          // as I don't know how to avoid SI-7567, let's fall back into an anaphoric macro!
          c.warn(s"Due to SI-7567, cannot get the companion of $typeName. Falling back to an anaphoric macro.")
          return c.parse(typeName)
        }
        c.parse(symbol.fullName)
      }
      f
    }
    val paramMethods = `type`.decls.collect { case m: MethodSymbol if m.isCaseAccessor => m }.toList
    val paramTypes = for (paramMethod <- paramMethods) yield {
      val paramType = paramMethod.returnType
      // if the return type is one of case class type parameters, replace with the actual type
      aTypeParams.indexOf(paramType.typeSymbol) match {
        case -1 => paramType
        case i => actualTypeParams(i)
      }
    }
    new CaseClassTypeInfo[MethodSymbol, Type, Tree] {
      def params = paramMethods zip paramTypes
      def companion = aCompanion
    }
  }
  // FIXME: need to conform Java Beans specification
  def fromJavaPropMethodName(name: String) = {
    val drop = if (name.startsWith("get") || name.startsWith("set")) 3 else if (name.startsWith("is")) 2 else sys.error(s"$name seems not be a Java property method name")
    val rest = name.drop(drop)
    if (rest.toUpperCase(java.util.Locale.ENGLISH) == rest) rest else rest.head.toLower + rest.drop(1)
  }
  // FIXME: need to conform Java Beans specification
  def toJavaPropGetMethodName(name: String) = "get" + name.head.toUpper + name.drop(1)
  def toJavaPropIsMethodName(name: String) = "is" + name.head.toUpper + name.drop(1)
  def toJavaPropSetMethodName(name: String) = "set" + name.head.toUpper + name.drop(1)
  def javishClassMapDecoder(c: blackbox.Context)(`type`: c.Type): ListMap[String, MapDecoder[c.Tree]] = {
    import c.universe._
    val typeInfo = reflectJavishClass(c)(`type`)
    val ret = for ((method, paramType) <- typeInfo if method.name.decodedName.toString.startsWith("get") || method.name.decodedName.toString.startsWith("is")) yield {
      val paramSymbol = paramType.typeSymbol
      fromJavaPropMethodName(method.name.decodedName.toString) -> MapDecoderLazy[Tree](tree = { x: Tree => q"$x.$method" }, reflector = () => classDecoder(c)(paramType).asInstanceOf[DecodeReflector[Tree]])
    }
    ret
  }
  def caseClassMapDecoder(c: blackbox.Context)(`type`: c.Type): ListMap[String, MapDecoder[c.Tree]] = {
    import c.universe._
    val typeInfo = reflectCaseClass(c)(`type`)
    val decoders = for ((method, paramType) <- typeInfo.params) yield {
      val paramSymbol = paramType.typeSymbol
      method.name.decodedName.toString -> (paramType.typeSymbol match {
        case paramSymbol if paramSymbol.isClass && paramSymbol.asClass.isCaseClass => MapDecoderSeq[Tree](caseClassDecoder(c)(paramType).asInstanceOf[DecodeReflector[Tree]])
        case _ => MapDecoderLeaf[Tree]({ x: Tree => q"$x.$method" })
      })
    }
    ListMap[String, MapDecoder[Tree]](decoders: _*)
  }
  def caseClassSeqDecoder(c: blackbox.Context)(`type`: c.Type): Seq[SeqDecoder[c.Tree]] = {
    import c.universe._
    val typeInfo = reflectCaseClass(c)(`type`)
    for ((method, paramType) <- typeInfo.params) yield {
      val paramSymbol = paramType.typeSymbol
      paramType.typeSymbol match {
        case paramSymbol if paramSymbol.isClass && paramSymbol.asClass.isCaseClass => SeqDecoderSeq(caseClassDecoder(c)(paramType))
        case _ => SeqDecoderLeaf({ x: Tree => q"$x.$method" })
      }
    }
  }
  def caseClassDecoder(c: blackbox.Context)(`type`: c.Type) = {
    import c.universe._
    val typeInfo = reflectCaseClass(c)(`type`)
    val mapDecoder = caseClassMapDecoder(c)(`type`)
    val seqDecoder = caseClassSeqDecoder(c)(`type`)
    new DecodeReflector[Tree] {
      override def decodedType = `type`
      override def decoderByName = (name: String) => mapDecoder(name).asInstanceOf[MapDecoder[c.Tree]]
      override def decoderByIndex = (i: Int) => seqDecoder(i).asInstanceOf[SeqDecoder[c.Tree]]
    }
  }
  def javishClassDecoder(c: blackbox.Context)(`type`: c.Type) = {
    import c.universe._
    val typeInfo = reflectJavishClass(c)(`type`)
    val mapDecoder = javishClassMapDecoder(c)(`type`)
    new DecodeReflector[Tree] {
      override def decodedType = `type`
      override def decoderByName = (name: String) => mapDecoder(name).asInstanceOf[MapDecoder[c.Tree]]
      override def decoderByIndex = sys.error(s"decoding non case class ${`type`} by index is not supported")
    }
  }
  def classDecoder(c: blackbox.Context)(`type`: c.Type) = {
    import c.universe._
    if (`type`.typeSymbol.asClass.isCaseClass) caseClassDecoder(c)(`type`) else javishClassDecoder(c)(`type`)
  }
  def caseClassEncoder(c: blackbox.Context)(`type`: c.Type): EncodeReflector[c.Tree] = {
    import c.universe._
    val typeInfo = reflectCaseClass(c)(`type`)
    new EncodeReflector[Tree] {
      override def encoderByName(implicit decodeReflector: DecodeReflector[Tree]) = (x: Tree) => {
        val decodedType = decodeReflector.decodedType
        val decoder = decodeReflector.decoderByName
        val params = typeInfo.params
        val applyParams = for (i <- 0 until params.size) yield {
          val (method, paramType) = params(i)
          val paramName = method.name.decodedName.toString
          decoder(paramName) match {
            case MapDecoderLazy(f, reflector) =>
              val tree = f(x)
              try c.typecheck(tree = tree, pt = paramType.asInstanceOf[c.Type]) catch {
                case e: TypecheckException =>
                  val decoder = reflector()
                  val encodeReflector = caseClassEncoder(c)(paramType)
                  val encode = encodeReflector.encoderByName(decoder)
                  val suffix = reflectJavishClass(c)(decodedType.asInstanceOf[Type]).toSeq(i)._1
                  encode(q"$x.$suffix")
              }
            case MapDecoderLeaf(f) =>
              val tree = f(x)
              try c.typecheck(tree = tree, pt = paramType.asInstanceOf[c.Type]) catch {
                case e: TypecheckException =>
                  classDecoder(c)(paramType)
              }
              tree
            case MapDecoderSeq(decodeReflector) =>
              implicit val decoder = decodeReflector
              val encodeReflector = caseClassEncoder(c)(paramType)
              val encode = encodeReflector.encoderByName
              val suffix = reflectCaseClass(c)(decodedType.asInstanceOf[Type]).params(i)._1
              encode(q"$x.$suffix")
          }
        }
        q"${typeInfo.companion}(..$applyParams)"
      }
      override def encoderByIndex(implicit decodeReflector: DecodeReflector[Tree]) = (x: Tree) => {
        val decodedType = decodeReflector.decodedType
        val decoder = decodeReflector.decoderByIndex
        val params = typeInfo.params
        val applyParams = for (i <- 0 until params.size) yield {
          val (method, paramType) = params(i)
          val paramName = method.name.decodedName.toString
          decoder(i) match {
            case SeqDecoderLeaf(f) => f(x)
            case SeqDecoderSeq(decodeReflector) =>
              implicit val decoder = decodeReflector
              val encodeReflector = caseClassEncoder(c)(paramType)
              val encode = encodeReflector.encoderByIndex
              val suffix = reflectCaseClass(c)(decodedType.asInstanceOf[Type]).params(i)._1
              encode(q"$x.$suffix")
          }
        }
        q"${typeInfo.companion}(..$applyParams)"
      }
    }
  }
  def typecheck(c: blackbox.Context)(tree: c.Tree, expected: c.Type): c.Tree = try c.typecheck(tree = tree, pt = expected) catch {
    case e: TypecheckException =>
      println(s"Typecheck failed! The expanded tree is:\n$tree")
      throw e
  }
  def convertByNameImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(x: c.Expr[A]): c.Expr[B] = {
    import c.universe._
    implicit val decoder = classDecoder(c)(weakTypeOf[A])
    val encoder = caseClassEncoder(c)(weakTypeOf[B]).asInstanceOf[EncodeReflector[c.Tree]] // I don't know how to get rid of asInstanceOf...
    val encode = encoder.encoderByName
    val tree = encode(x.tree)
    typecheck(c)(tree = tree, expected = weakTypeOf[B])
    c.Expr[B](c.parse(tree.toString)) // without toString and parse, got ClassCastException at runtime... I don't understand
  }
  def convertByIndexImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(x: c.Expr[A]): c.Expr[B] = {
    import c.universe._
    implicit val decoder = classDecoder(c)(weakTypeOf[A])
    val encoder = caseClassEncoder(c)(weakTypeOf[B]).asInstanceOf[EncodeReflector[c.Tree]] // I don't know how to get rid of asInstanceOf...
    val encode = encoder.encoderByIndex
    val tree = encode(x.tree)
    typecheck(c)(tree = tree, expected = weakTypeOf[B])
    c.Expr[B](c.parse(tree.toString)) // without toString and parse, got ClassCastException at runtime... I don't understand
  }
  def convertByName[A, B](x: A): B = macro convertByNameImpl[A, B]
  def convertByIndex[A, B](x: A): B = macro convertByIndexImpl[A, B]
  def readVariant[A](x: Variant): A = macro readVariantImpl[A]
  def writeVariant[A](x: A): Variant = macro writeVariantImpl[A]
}
