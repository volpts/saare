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
import saare.Logger.Slf4jLogger

class LoggerSpec extends FeatureSpec with GivenWhenThen with Matchers {
  implicit val logger = new Slf4jLogger("LoggerSpec")
  feature("Logger") {
    scenario("logging") {
      @Logger.logging(showRet = true, level = "error")
      def test(x: String, y: Int, z: Long): String = x + y + z
      @Logger.logging(level = "info")
      def test2(x: String, y: Int, z: Long): String = x + y + z
      test("a", 1, 1)
      test2("b", 2, 2)
    }
    scenario("Logger.<level>") {
      Logger.debug("test")
      Logger.info("test")
      Logger.warn("test")
      Logger.error("test")
    }
  }
}

