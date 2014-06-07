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

import org.scalatest._
import scala.collection.immutable._

object ReflectCoreSpec {
  case class A(x1: String, x2: Int, x3: D, x4: Long)
  case class B(x1: String, x2: Int, x3: E)
  case class C(x4: Long, x1: String, x3: E, x2: Int)
  case class D(x1: String, x2: Int, x3: Long)
  case class E(x1: String, x2: Int)
  case class F(_1: String, _2: Long, _3: D, _4: Long)
}

class ReflectCoreSpec extends FeatureSpec with GivenWhenThen with Matchers {
  feature("ReflectCore#readVariant, writeVariant") {
    scenario("read and write variant data") {
      case class A(x1: String, x2: Int, x3: D, x4: Long)
      case class D(x1: String, x2: Int, x3: Long)
      import ReflectCore.Variant
      val v = Variant.Sequence(Seq(Variant.Text("test"), Variant.Int32(10), Variant.Sequence(Seq(Variant.Text("test2"), Variant.Int32(1000), Variant.Int64(10000))), Variant.Int64(100)))
      val a = ReflectCore.readVariant[A](v)
      a shouldEqual A("test", 10, D("test2", 1000, 10000), 100)
      val v2 = ReflectCore.writeVariant(a)
      v2 shouldEqual v
    }
  }
  feature("ReflectionCore#convertByName, convertByIndex") {
    scenario("reflect, encode and decode case classes") {
      case class A(x1: String, x2: Int, x3: D, x4: Long)
      case class B(x1: String, x2: Int, x3: E)
      case class C(x4: Long, x1: String, x3: E, x2: Int)
      case class D(x1: String, x2: Int, x3: Long)
      case class E(x1: String, x2: Int)
      case class F(_1: String, _2: Long, _3: D, _4: Long)
      val a = A("test", 10, D("test2", 1000, 10000), 100)
      val b = ReflectCore.convertByName[A, B](a)
      val c = ReflectCore.convertByName[A, C](a)
      b shouldEqual B("test", 10, E("test2", 1000))
      c shouldEqual C(100, "test", E("test2", 1000), 10)
      val b2 = ReflectCore.convertByIndex[A, B](a)
      b2 shouldEqual B("test", 10, E("test2", 1000))
      val f = ReflectCore.convertByIndex[A, F](a)
      f shouldEqual F("test", 10, D("test2", 1000, 10000), 100)

      // non-local classes must be referenced by full names
      val a2 = ReflectCoreSpec.A("test", 10, ReflectCoreSpec.D("test2", 1000, 10000), 100)
      val b3 = ReflectCore.convertByName[ReflectCoreSpec.A, ReflectCoreSpec.B](a2)
      val c2 = ReflectCore.convertByName[ReflectCoreSpec.A, ReflectCoreSpec.C](a2)
      b3 shouldEqual ReflectCoreSpec.B("test", 10, ReflectCoreSpec.E("test2", 1000))
      c2 shouldEqual ReflectCoreSpec.C(100, "test", ReflectCoreSpec.E("test2", 1000), 10)
      val b4 = ReflectCore.convertByIndex[ReflectCoreSpec.A, ReflectCoreSpec.B](a2)
      b4 shouldEqual ReflectCoreSpec.B("test", 10, ReflectCoreSpec.E("test2", 1000))
      val f2 = ReflectCore.convertByIndex[ReflectCoreSpec.A, ReflectCoreSpec.F](a2)
      f2 shouldEqual ReflectCoreSpec.F("test", 10, ReflectCoreSpec.D("test2", 1000, 10000), 100)
    }
    scenario("reflect and decode javish classes") {
      class A {
        def getTest = "test"
        def getTest2 = "test2"
        def getUPPER = "upper"
      }
      class B {
        def getTest3 = "test3"
        def getA = new A
        def getTest4 = new A
      }
      case class ScalaA(test: String, test2: String, UPPER: String)
      case class ScalaB(test3: String, A: ScalaA, test4: ScalaA)
      val a = new A
      val b = new B
      val a2 = ReflectCore.convertByName[A, ScalaA](a)
      val b2 = ReflectCore.convertByName[B, ScalaB](b)
      a2 shouldEqual ScalaA("test", "test2", "upper")
      b2 shouldEqual ScalaB("test3", ScalaA("test", "test2", "upper"), ScalaA("test", "test2", "upper"))
    }
  }
}
