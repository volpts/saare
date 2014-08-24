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
import scala.reflect.macros.whitebox.Context

object ReflectCore {
  trait Reflect {
    val c: Context
    import c.universe._

    def warn(x: String) = c.warning(c.enclosingPosition, x)

    def warnAndBail(x: String) = {
      warn(x)
      sys.error(x)
    }

    def typecheck(tree: c.Tree, expected: c.Type): c.Tree = try c.typecheck(tree = tree, pt = expected) catch {
      case e: TypecheckException =>
        println(s"Typecheck failed! The expanded tree is:\n$tree")
        throw e
    }
    def checking(expected: c.Type)(f: => c.Tree): c.Tree = typecheck(f, expected)

    abstract class ExtractConstant[A] {
      def unapply(constant: Constant): Option[A]
    }
    implicit object extractStringConstant extends ExtractConstant[String] {
      def unapply(constant: Constant) = constant match {
        case Constant(value: String) => Some(value)
        case _ => None
      }
    }
    implicit object extractBooleanConstant extends ExtractConstant[Boolean] {
      def unapply(constant: Constant) = constant match {
        case Constant(value: Boolean) => Some(value)
        case _ => None
      }
    }
    implicit object extractLongConstant extends ExtractConstant[Long] {
      def unapply(constant: Constant) = constant match {
        case Constant(value: Long) => Some(value)
        case _ => None
      }
    }
    def macroAnnotationParam[A: ExtractConstant](name: String): Option[A] = c.macroApplication match {
      case q"new $cls(..${ params: List[Tree] }).macroTransform($tree)" =>
        val C = implicitly[ExtractConstant[A]]
        params.collectFirst {
          case AssignOrNamedArg(Ident(TermName(`name`)), Literal(C(value))) => value
        }
      case _ => None
    }

    case class CaseClassTypeInfo(params: Seq[(MethodSymbol, Type)])

    def companion(`type`: c.Type): c.Tree = {
      val typeSymbol = `type`.typeSymbol
      val typeName = typeSymbol.name.decodedName.toString
      def f: Tree = {
        val symbol = typeSymbol.companion.orElse {
          // local symbol does not have a full name - I think short name is probably ok
          return c.parse(typeName)
        }
        c.parse(symbol.fullName)
      }
      f
    }

    def companion[A: c.WeakTypeTag]: c.Tree = companion(weakTypeOf[A])

    def conform_?[A: c.WeakTypeTag](x: Type) = x <:< weakTypeOf[A]

    def isCaseClass(`type`: Type): Boolean = `type`.typeSymbol.asClass.isCaseClass

    def isCaseClass[A: c.WeakTypeTag]: Boolean = isCaseClass(weakTypeOf[A])

    def reflectCaseClass(`type`: Type): CaseClassTypeInfo = {
      val typeSymbol = `type`.typeSymbol
      val typeName = typeSymbol.name.decodedName.toString
      val `class` = typeSymbol.asClass
      if (!isCaseClass(`type`))
        warnAndBail(s"Tried to reflect $typeName as a case class but $typeName seems not be a case class!")
      val aTypeParams = `class`.typeParams
      val TypeRef(_, _, actualTypeParams) = `type`
      val paramMethods = `type`.decls.collect { case m: MethodSymbol if m.isCaseAccessor => m }.toList
      val paramTypes = for (paramMethod <- paramMethods) yield {
        val paramType = paramMethod.returnType
        // if the return type is one of case class type parameters, replace with the actual type
        aTypeParams.indexOf(paramType.typeSymbol) match {
          case -1 => paramType
          case i => actualTypeParams(i)
        }
      }
      CaseClassTypeInfo(paramMethods zip paramTypes)
    }
    def reflectCaseClass[A: c.WeakTypeTag]: CaseClassTypeInfo = reflectCaseClass(weakTypeOf[A])

    case class TupleTypeInfo(params: List[Type]) {
      def paramNames: List[String] = for (param <- params) yield param.typeSymbol.fullName
    }

    def isTuple[A: c.WeakTypeTag]: Boolean = !weakTypeOf[A].typeSymbol.name.decodedName.toString.matches("scala.Tuple[0-9]+")

    def reflectTuple[A: c.WeakTypeTag]: TupleTypeInfo = {
      val aType = weakTypeOf[A]
      if (!isTuple[A])
        warnAndBail(s"$aType is not a scala.Tuple* type (note - subtypes of tuples are not supported)")
      val TypeRef(_, _, actualTypeParams) = weakTypeOf[A]
      TupleTypeInfo(actualTypeParams)
    }

    def tupleParamNames[A: c.WeakTypeTag]: List[String] = reflectTuple[A].paramNames
    def tupleParamNameExprs[A: c.WeakTypeTag]: c.Expr[List[String]] = c.Expr[List[String]](q"${companion[List[_]]}(..${reflectTuple[A].paramNames})")
    def readVariantImpl[A: c.WeakTypeTag](x: c.Tree): c.Tree = {
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
        val ret = q"(if ($variant == ${companion[Variant]}.Undefined) _root_.scala.None else _root_.scala.Some($reflectCore.readVariant[$tp]($variant)))"
        ret
      } else if (`type`.typeSymbol.asClass.isCaseClass) {
        val typeInfo = reflectCaseClass[A]
        def loop(typeInfo: CaseClassTypeInfo, seq: Tree): Seq[Tree] = {
          val params = typeInfo.params
          for (i <- 0 until params.size) yield {
            val (method, paramType) = params(i)
            val methodName = method.name.decodedName.toString
            val variant = q"$seq($i)"
            if (paramType.typeSymbol.asClass.isCaseClass) {
              val paramTypeInfo = reflectCaseClass(paramType.asInstanceOf[Type])
              val children = loop(paramTypeInfo, q"$variant.asSequence")
              q""" ${companion(paramType).asInstanceOf[Tree]}(..$children) """
            } else {
              q"$reflectCore.readVariant[${paramType.asInstanceOf[Type]}]($variant)"
            }
          }
        }
        val children = loop(typeInfo, q"$variant.asSequence")
        q""" ${companion[A].asInstanceOf[Tree]}(..$children) """
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
      else if (`type` <:< weakTypeOf[java.time.Instant]) q"$variant.asTimestamp.value"
      else sys.error(s"Type ${`type`} is not (yet) supported by ReflectCore#readVariant!")
      val ret = q"{ val $variant = $x; $tree }"
      typecheck(ret, weakTypeOf[A])
      ret
    }
    def writeVariantImpl[A: c.WeakTypeTag](x: c.Tree): c.Tree = {
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
        val typeInfo = reflectCaseClass[A]
        def loop(typeInfo: CaseClassTypeInfo, x: Tree): Seq[Tree] = {
          val params = typeInfo.params
          for (i <- 0 until params.size) yield {
            val (method, paramType) = params(i)
            if (paramType.typeSymbol.asClass.isCaseClass) {
              val paramTypeInfo = reflectCaseClass(paramType.asInstanceOf[Type])
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
      else if (`type` =:= weakTypeOf[java.time.Instant]) q"${weakTypeOf[Timestamp].typeSymbol.companion}($x)"
      else sys.error(s"Type ${`type`} is not (yet) supported by ReflectCore#readVariant!")
      typecheck(tree, weakTypeOf[Variant])
      tree
    }
  }
  class Macro(val c: Context) extends Reflect
  def readVariant[A](x: Variant): A = macro Macro.readVariantImpl[A]
  def writeVariant[A](x: A): Variant = macro Macro.writeVariantImpl[A]
}
