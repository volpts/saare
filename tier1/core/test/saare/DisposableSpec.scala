/*
Copyright 2013-2014 sumito3478 <sumito3478@gmail.com>

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
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

class DisposableSpec extends WordSpec with Logging[DisposableSpec] {
  import Saare._
  "Saare.disposing" should {
    "dispose the object asynchronously" in {
      var a = false
      class A extends Disposable[A] {
        override def disposeInternal = a = true
      }
      val f = disposing(new A) {
        a =>
          Future {
            // do something...
            ()
          }
      }
      ((a: A) => Future { () }) |> disposing(new A)
      Await.result(f, Duration.Inf)
      assert(a === true)
    }
  }
}