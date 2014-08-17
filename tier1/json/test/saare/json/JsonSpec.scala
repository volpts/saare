/*
Copyright 2013 sumito3478 <sumito3478@gmail.com>

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
package json

import org.scalatest._

class JsonSpec extends WordSpec {
  import saare._, Saare._
  import json._, Json._
  "json.lens" should {
    "lens the jvalue" in {
      val json = """ { "test": 100, "test2": [10, 20], "test3": { "test" : 100 } } """
      val jvalue = (json |> parse).get
      assert((jvalue |> lens.test #|> Lens.get) === Some(JNumber(100)))
      assert((jvalue |> lens.test2.`0` #|> Lens.get) === Some(JNumber(10)))
      assert((jvalue |> lens.test2.`1` #|> Lens.get) === Some(JNumber(20)))
      assert((jvalue |> lens.test3.test #|> Lens.get) === Some(JNumber(100)))
      assert((jvalue |> lens.test3.test #|> Lens.set)(JNumber(200)) === Some((""" { "test": 100, "test2": [10, 20], "test3": { "test": 200 } } """ |> parse).get))
    }
  }
  "json.decode" should {
    "decode JSON value to Scala value" in {
      val seq = (""" [0, 1, 2, 3] """ |> parse).get
      assert(decode[Seq[Int]](seq) === Some(Seq(0, 1, 2, 3)))
      val map = (""" { "0": 0, "1": 1, "2": 2, "3": 3 } """ |> parse).get
      assert(decode[Map[String, Int]](map) === Some(Map("0" -> 0, "1" -> 1, "2" -> 2, "3" -> 3)))
      val test = (""" {"test": {"test": 100, "test2": null }, "test2": [100, 200, 300], "test3": 10.5, "test4": { "test5" : 100, "test6" : 3.4} } """ |> parse).get
      case class Test(test: Int, test2: Option[Long], test3: Option[Boolean])
      case class Test2(test: Test, test2: Seq[BigInt], test3: Double, test4: Map[String, BigDecimal])
      assert(decode[Test2](test) === Some(Test2(Test(100, None, None), Seq(100, 200, 300), 10.5, Map("test5" -> 100, "test6" -> 3.4))))
    }
  }
  "json.encode" should {
    "encode Scala value to JSON value" in {
      val seq = (""" [0, 1, 2, 3] """ |> parse).get
      assert(encode(Seq(0, 1, 2, 3)) === seq)
      val map = (""" { "0": 0, "1": 1, "2": 2, "3": 3 } """ |> parse).get
      assert(encode[Map[String, Int]](Map("0" -> 0, "1" -> 1, "2" -> 2, "3" -> 3)) === map)
      val test = (""" {"test": {"test": 100}, "test2": [100, 200, 300], "test3": 10.5, "test4": { "test5" : 100, "test6" : 3.4} } """ |> parse).get
      case class Test(test: Int, test2: Option[Long], test3: Option[Boolean])
      case class Test2(test: Test, test2: Seq[BigInt], test3: Double, test4: Map[String, BigDecimal])
      assert(encode(Test2(Test(100, None, None), Seq(100, 200, 300), 10.5, Map("test5" -> 100, "test6" -> 3.4))) === test)
    }
  }
}