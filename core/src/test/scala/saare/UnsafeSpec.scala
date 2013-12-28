/*Copyright 2013 sumito3478 <sumito3478@gmail.com>

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

import org.scalatest._

class UnsafeSpec extends WordSpec {
  "unsafe.Pointer" should {
    "directly access to the memory" in {
      import java.nio.ByteBuffer
      import saare._, Saare._
      import unsafe._, Unsafe._
      val buffer = ByteBuffer.allocateDirect(10)
      val ptr = buffer |> asPointer
      ptr.longLE = 10
      (ptr + 1).longLE = 10
      assert(ptr.longLE == 256 * 10 + 10)
      val array = new Array[Byte](10)
      buffer.get(array)
      assert(array.toBuffer === Seq(10, 10, 0, 0, 0, 0, 0, 0, 0, 0))
    }
  }
}