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

class ReflectCore {
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
  protected[this] implicit class ContextW(val self: blackbox.Context) {
    import self._
    def warn(message: String) = warning(enclosingPosition, message)
    def warnAndBail(message: String) = {
      warn(message)
      sys.error(message)
    }
    def bail(message: String) = {
      error(enclosingPosition, message)
      sys.error(message)
    }
  }
  protected[this] abstract class CaseClassTypeInfo[MethodSymbol <: Universe#MethodSymbol, Type <: Universe#Type, Tree <: Universe#Tree] {
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
        println(s"decodedType = $decodedType")
        val decoder = decodeReflector.decoderByName
        val params = typeInfo.params
        val applyParams = for (i <- 0 until params.size) yield {
          val (method, paramType) = params(i)
          val paramName = method.name.decodedName.toString
          println(s"paramName = $paramName")
          println(decoder(paramName))
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
  def convertByNameImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(x: c.Expr[A]): c.Expr[B] = {
    import c.universe._
    implicit val decoder = classDecoder(c)(weakTypeOf[A])
    val encoder = caseClassEncoder(c)(weakTypeOf[B]).asInstanceOf[EncodeReflector[c.Tree]] // I don't know how to get rid of asInstanceOf...
    val encode = encoder.encoderByName
    val tree = encode(x.tree)
    try c.typecheck(tree = tree, pt = weakTypeOf[B])
    catch {
      case e: TypecheckException =>
        println(s"Typecheck failed while converting from ${weakTypeOf[A]} to ${weakTypeOf[B]} by name! The expanded tree is:\n$tree")
        throw e
    }
    c.Expr[B](c.parse(tree.toString)) // without toString and parse, got ClassCastException at runtime... I don't understand
  }
  def convertByIndexImpl[A: c.WeakTypeTag, B: c.WeakTypeTag](c: blackbox.Context)(x: c.Expr[A]): c.Expr[B] = {
    import c.universe._
    implicit val decoder = classDecoder(c)(weakTypeOf[A])
    val encoder = caseClassEncoder(c)(weakTypeOf[B]).asInstanceOf[EncodeReflector[c.Tree]] // I don't know how to get rid of asInstanceOf...
    val encode = encoder.encoderByIndex
    val tree = encode(x.tree)
    try c.typecheck(tree = tree, pt = weakTypeOf[B])
    catch {
      case e: TypecheckException =>
        println(s"Typecheck failed while converting from ${weakTypeOf[A]} to ${weakTypeOf[B]} by name! The expanded tree is:\n$tree")
        throw e
    }
    c.Expr[B](c.parse(tree.toString)) // without toString and parse, got ClassCastException at runtime... I don't understand
  }
}
object ReflectCore extends ReflectCore {
  def convertByName[A, B](x: A): B = macro convertByNameImpl[A, B]
  def convertByIndex[A, B](x: A): B = macro convertByIndexImpl[A, B]
}