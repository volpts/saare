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
      import ReflectCore.{ writeVariant, readVariant }
      case class A(x1: String, x2: Int, x3: D, x4: Long)
      case class D(x1: String, x2: Int, x3: Long)
      case class G(x1: String, x2: Int)
      case class H(x1: String, x2: Int, x3: Option[D])
      val v = Variant.Sequence(Seq(Variant.Text("test"), Variant.Int32(10), Variant.Sequence(Seq(Variant.Text("test2"), Variant.Int32(1000), Variant.Int64(10000))), Variant.Int64(100)))
      val a = ReflectCore.readVariant[A](v)
      a shouldEqual A("test", 10, D("test2", 1000, 10000), 100)
      val v2 = ReflectCore.writeVariant(a)
      v2 shouldEqual v
      ReflectCore.writeVariant(None) shouldEqual Variant.Undefined
      ReflectCore.writeVariant(Some(100)) shouldEqual Variant.Int32(100)
      val o: Option[Int] = Some(100)
      readVariant[Option[Int]](writeVariant(o))
      readVariant[G](writeVariant(H("test", 10, None))) shouldEqual G("test", 10)
      readVariant[G](writeVariant(H("test", 10, Some(D("test2", 1000, 10000))))) shouldEqual G("test", 10)
      val a2 = writeVariant(A("test", 10, D("test2", 1000, 10000), 100))
      val h = readVariant[H](a2)
      h shouldEqual H("test", 10, Some(D("test2", 1000, 10000)))
    }
  }
}
