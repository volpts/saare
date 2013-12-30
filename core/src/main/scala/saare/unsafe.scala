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
package unsafe

import java.security._
import scala.util.control.Exception._
import sun.misc.Unsafe._
import java.nio._
import saare._, Saare._
import Memory._
import scala.concurrent.util.Unsafe.{ instance => _unsafe }

private[saare] class Unsafe

private[saare] object Unsafe extends Logging[Unsafe] {
  val byteBufferAddress: ByteBuffer => Long = {
    val address = classOf[Buffer].getDeclaredField("address")
    address.setAccessible(true)
    val addressOffset = _unsafe.objectFieldOffset(address)
    buffer => _unsafe.getLong(buffer, addressOffset)
  }
  def base_offset(buf: Array[_]) = _unsafe.arrayBaseOffset(buf.getClass)

  // whether unaligned access is allowed or not
  val unaligned = Seq("i386", "i586", "i686", "x86", "x64", "x86_64", "amd64").contains(System.getProperty("os.arch"))

  def checkAligned(buf: Long, off: Long, alignment: Int): Unit = {
    if ((buf + off) % alignment != 0) {
      logger.warn(s"Memory is not aligned to $alignment byte boundary")
      if (!unaligned)
        throw new UnsupportedOperationException(s"Memory is not aligned to $alignment byte boundary")
    }
  }
  def checkAligned(buf: Array[_], off: Long, alignment: Int): Unit = {
    if (off % alignment != 0) {
      logger.warn(s"Memory is not aligned to $alignment byte boundary")
      if (!unaligned)
        throw new UnsupportedOperationException(s"Memory is not aligned to $alignment byte boundary")
    }
  }
}