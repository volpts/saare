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
package saare.hashing

import org.scalatest._
import saare._, Saare._
import unsafe._, Unsafe._
import Memory._

class XXHash32Spec extends WordSpec with Logging[XXHash32Spec] {
  "XXHash32#hash" should {
    "calculate the xxHash32 value" in {
      val length = 1024 * 1024 * 128 // 128 MB
      logger.info(s"xxhash32 benchmark for random $length bytes data")
      val testData = new Array[Byte](length)
      new java.util.Random().nextBytes(testData)
      val testBuffer = java.nio.ByteBuffer.allocateDirect(length)
      testBuffer.put(testData)
      import System._
      net.jpountz.xxhash.XXHashFactory.nativeInstance().hash32().hash(testData, 0, length, 0)
      val start = currentTimeMillis
      val correct = net.jpountz.xxhash.XXHashFactory.unsafeInstance().hash32().hash(testData, 0, length, 0)
      logger.info(s"lz4-java (JNI implementation) takes ${currentTimeMillis - start} millis")
      XXHash32.hash(testData, 0, length, 0)
      val start2 = currentTimeMillis
      val hash = XXHash32.hash(testData, 0, length, 0)
      logger.info(s"saare.hashing implementation (jvm heap array) takes ${currentTimeMillis - start2} millis")
      XXHash32.hash(testBuffer, 0, length, 0)
      val start3 = currentTimeMillis
      XXHash32.hash(testBuffer, 0, length, 0)
      logger.info(s"saare.hashing implementation (directBuffer) takes ${currentTimeMillis - start3} millis")
      assert(hash === correct)
    }
  }
}