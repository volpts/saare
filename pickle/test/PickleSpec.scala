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

object PickleSpec {
  case class A(x1: String, x2: Int, x3: D, x4: Long)
  case class B(x1: String, x2: Int, x3: E)
  case class C(x4: Long, x1: String, x3: E, x2: Int)
  case class D(x1: String, x2: Int, x3: Long)
  case class E(x1: String, x2: Int)
  case class F(_1: String, _2: Long, _3: D, _4: Long)
}

class PickleSpec extends FeatureSpec with GivenWhenThen with Matchers {
  feature("Pickle.write") {
    scenario("serialize case classes") {
      import ReflectCore.{ Variant, readVariant, writeVariant }
      case class A(x1: String, x2: Int, x3: D, x4: Long)
      case class B(x1: String, x2: Int, x3: E)
      case class C(x4: Long, x1: String, x3: E, x2: Int)
      case class D(x1: String, x2: Int, x3: Long)
      case class E(x1: String, x2: Int)
      case class F(_1: String, _2: Long, _3: D, _4: Long)
      val a = A("test", 10, D("test2", 1000, 10000), 100)
      val b = B("test", 10, E("test2", 1000))
      val c = C(100, "test", E("test2", 1000), 10)
      val f = F("test", 10, D("test2", 1000, 10000), 100)
      val ab = Pickle.writeVariant(writeVariant(a))
      val bb = Pickle.writeVariant(writeVariant(b))
      val cb = Pickle.writeVariant(writeVariant(c))
      val fb = Pickle.writeVariant(writeVariant(f))
      readVariant[A](Pickle.readVariant(ab)) shouldEqual a
      readVariant[B](Pickle.readVariant(bb)) shouldEqual b
      readVariant[C](Pickle.readVariant(cb)) shouldEqual c
      readVariant[F](Pickle.readVariant(fb)) shouldEqual f
      // non-local classes must be referenced by full names
      val a2 = PickleSpec.A("test", 10, PickleSpec.D("test2", 1000, 10000), 100)
      val b2 = PickleSpec.B("test", 10, PickleSpec.E("test2", 1000))
      val c2 = PickleSpec.C(100, "test", PickleSpec.E("test2", 1000), 10)
      val f2 = PickleSpec.F("test", 10, PickleSpec.D("test2", 1000, 10000), 100)
      val a2b = Pickle.writeVariant(writeVariant(a2))
      val b2b = Pickle.writeVariant(writeVariant(b2))
      val c2b = Pickle.writeVariant(writeVariant(c2))
      val f2b = Pickle.writeVariant(writeVariant(f2))
      readVariant[PickleSpec.A](Pickle.readVariant(a2b)) shouldEqual a2
      readVariant[PickleSpec.B](Pickle.readVariant(b2b)) shouldEqual b2
      readVariant[PickleSpec.C](Pickle.readVariant(c2b)) shouldEqual c2
      readVariant[PickleSpec.F](Pickle.readVariant(f2b)) shouldEqual f2
    }
  }
}
