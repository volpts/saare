/*Copyright 2014 sumito3478 <sumito3478@gmail.com>

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package saare
package http
package client

import org.scalatest._

import saare._, Saare._
import saare.http.client._, Client._

class ClientSpec extends WordSpec with Logging[ClientSpec] {
  "saare.http.client.Client" should {
    "submit http request" in {
      import Request._
      val client = new Client
      import client._
      val f = Request("http://localhost:8080/") |> segment("test") |> GET |> headers("test" -> "test") |> secure |>
        queries("test" -> "test") |> handler(Client.string)
      for (str <- f)
        println(str)
    }
  }
}
