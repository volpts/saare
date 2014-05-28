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
package http
package client

import org.scalatest._

import saare._, Saare._
import saare.http.client._

class ClientSpec extends WordSpec with Logging[ClientSpec] {
  "saare.http.client.Client" should {
    "submit http request" in {
      val client = new Client
      import client._
      import Request._
      val f = Request("http://localhost:8080/") |> segment("test") |> GET |> headers("test" -> "test") |> secure |>
        queries("test" -> "test") |> Handler.string
      for (str <- f)
        println(str)
    }
  }
}
