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

private[saare] trait Pointer {
  import Unsafe._
  def +(x: Int): Pointer
  def -(x: Int): Pointer
  def byte: Byte
  def byte_=(x: Byte): Unit
  def short: Short
  def short_=(x: Short): Unit
  def int: Int
  def int_=(x: Int): Unit
  def long: Long
  def long_=(x: Long): Unit
  def float: Float
  def float_=(x: Float): Unit
  def double: Double
  def double_=(x: Double): Unit
  def shortLE: Short = short.fromLE
  def shortLE_=(x: Short) = short = x.toLE
  def intLE: Int = int.fromLE
  def intLE_=(x: Int) = int = int.toLE
  def longLE: Long = long.fromLE
  def longLE_=(x: Long) = long = x.toLE
  def shortBE: Short = short.fromBE
  def shortBE_=(x: Short) = short = x.toBE
  def intBE: Int = int.fromBE
  def intBE_=(x: Int) = int = x.toBE
  def longBE: Long = long.fromBE
  def longBE_=(x: Long) = long = x.toBE
}
private[saare] sealed trait PointerLike[A] {
  private[unsafe] def asPointer(x: A): Pointer
}

private[saare] trait Unsafe {
  def getAddress(buffer: Buffer): Long
  def getCleaner(buffer: ByteBuffer): Option[sun.misc.Cleaner]
  def unsafePointer(address: Long): Pointer
  def unsafeByteArrayPointer(x: Array[Byte]): Pointer
}

private[saare] object Unsafe extends Logging[Unsafe] {
  private[this] val _unsafe = if (System.getProperty("saare.unsafe.disable") == "true") None else {
    val result = allCatch[Unsafe].either(AccessController.doPrivileged(new PrivilegedAction[Unsafe] {
      def run(): Unsafe = try {
        val unaligned = Class.forName("java.nio.Bits").getDeclaredMethod("unaligned")
        unaligned.setAccessible(true)
        if (!unaligned.invoke(null).asInstanceOf[Boolean])
          sys.error("java.nio.Bits.unaligned() returns false, cannot use sun.misc.Unsafe for direct memory access")
        val f = classOf[sun.misc.Unsafe].getDeclaredField("theUnsafe")
        f.setAccessible(true)
        val _unsafe = f.get(null).asInstanceOf[sun.misc.Unsafe]
        val address = classOf[Buffer].getDeclaredField("address")
        address.setAccessible(true)
        val addressOffset = _unsafe.objectFieldOffset(address)
        val cleaner = {
          val direct = ByteBuffer.allocateDirect(1)
          val cleaner = direct.getClass.getDeclaredField("cleaner")
          cleaner.setAccessible(true)
          cleaner.get(direct).asInstanceOf[sun.misc.Cleaner].clean
          cleaner
        }
        val cleanerOffset = _unsafe.objectFieldOffset(cleaner)
        new Unsafe {
          def getAddress(buffer: Buffer) = _unsafe.getLong(buffer, addressOffset)
          def getCleaner(buffer: ByteBuffer) = Option(_unsafe.getObject(buffer, cleanerOffset).asInstanceOf[sun.misc.Cleaner])
          def unsafePointer(address: Long) = {
            class UnsafePointer(self: Long) extends Pointer {
              def +(x: Int): Pointer = new UnsafePointer(self + x)
              def -(x: Int): Pointer = new UnsafePointer(self - x)
              def byte: Byte = _unsafe.getByte(self)
              def byte_=(x: Byte): Unit = _unsafe.putByte(self, x)
              def short: Short = _unsafe.getShort(self)
              def short_=(x: Short): Unit = _unsafe.putShort(self, x)
              def int: Int = _unsafe.getInt(self)
              def int_=(x: Int): Unit = _unsafe.putInt(self, x)
              def long: Long = _unsafe.getLong(self)
              def long_=(x: Long): Unit = _unsafe.putLong(self, x)
              def float: Float = _unsafe.getFloat(self)
              def float_=(x: Float): Unit = _unsafe.putFloat(self, x)
              def double: Double = _unsafe.getDouble(self)
              def double_=(x: Double): Unit = _unsafe.putDouble(self, x)
            }
            new UnsafePointer(address)
          }
          def unsafeByteArrayPointer(x: Array[Byte]) = {
            class ByteArrayPointer(self: Array[Byte], offset: Long) extends Pointer {
              def +(x: Int): Pointer = new ByteArrayPointer(self, offset + x)
              def -(x: Int): Pointer = new ByteArrayPointer(self, offset - x)
              def byte: Byte = _unsafe.getByte(self, offset + ARRAY_BYTE_BASE_OFFSET)
              def byte_=(x: Byte): Unit = _unsafe.putByte(self, offset + ARRAY_BYTE_BASE_OFFSET, x)
              def short: Short = _unsafe.getShort(self, offset + ARRAY_BYTE_BASE_OFFSET)
              def short_=(x: Short): Unit = _unsafe.putShort(self, offset + ARRAY_BYTE_BASE_OFFSET, x)
              def int: Int = _unsafe.getInt(self, offset + ARRAY_BYTE_BASE_OFFSET)
              def int_=(x: Int): Unit = _unsafe.putInt(self, offset + ARRAY_BYTE_BASE_OFFSET, x)
              def long: Long = _unsafe.getLong(self, offset + ARRAY_BYTE_BASE_OFFSET)
              def long_=(x: Long): Unit = _unsafe.putLong(self, offset + ARRAY_BYTE_BASE_OFFSET, x)
              def float: Float = _unsafe.getFloat(self, offset + ARRAY_BYTE_BASE_OFFSET)
              def float_=(x: Float): Unit = _unsafe.putFloat(self, offset + ARRAY_BYTE_BASE_OFFSET, x)
              def double: Double = _unsafe.getDouble(self, offset + ARRAY_BYTE_BASE_OFFSET)
              def double_=(x: Double): Unit = _unsafe.putDouble(self, offset + ARRAY_BYTE_BASE_OFFSET, x)
            }
            new ByteArrayPointer(x, 0)
          }
        }
      } catch {
        case e @ (_: NoSuchElementException | _: IllegalArgumentException) => {
          throw new Error(e)
        }
      }
    }))
    for (e <- result.left)
      logger.warn("Cannot use sun.misc.Unsafe for faster operations, falling back to the safe code", e)
    result.right.toOption
  }

  private[this] val le = (0xcafebabe >>> 16) == 0xcafe
  private[this] val be = !le
  private[unsafe] implicit class ShortW(val self: Short) extends AnyVal {
    def bswap: Short = java.lang.Short.reverseBytes(self)
    def toLE: Short = if (be) bswap else self
    def toBE: Short = if (le) bswap else self
    def fromLE: Short = if (be) bswap else self
    def fromBE: Short = if (le) bswap else self
  }
  private[unsafe] implicit class IntW(val self: Int) extends AnyVal {
    def bswap: Int = java.lang.Integer.reverseBytes(self)
    def toLE: Int = if (be) bswap else self
    def toBE: Int = if (le) bswap else self
    def fromLE: Int = if (be) bswap else self
    def fromBE: Int = if (le) bswap else self
  }
  private[unsafe] implicit class LongW(val self: Long) extends AnyVal {
    def bswap: Long = java.lang.Long.reverseBytes(self)
    def toLE: Long = if (be) bswap else self
    def toBE: Long = if (le) bswap else self
    def fromLE: Long = if (be) bswap else self
    def fromBE: Long = if (le) bswap else self
  }

  private[this] class SafeByteBufferPointer(self: ByteBuffer, offset: Int) extends Pointer {
    def +(x: Int): Pointer = new SafeByteBufferPointer(self, offset + x)
    def -(x: Int): Pointer = new SafeByteBufferPointer(self, offset - x)
    def byte: Byte = self.get(offset)
    def byte_=(x: Byte): Unit = self.put(offset, x)
    def short: Short = self.getShort(offset)
    def short_=(x: Short): Unit = self.putShort(offset, x)
    def int: Int = self.getInt(offset)
    def int_=(x: Int): Unit = self.putInt(offset, x)
    def long: Long = self.getLong(offset)
    def long_=(x: Long): Unit = self.putLong(offset, x)
    def float: Float = self.getFloat(offset)
    def float_=(x: Float): Unit = self.putFloat(offset, x)
    def double: Double = self.getDouble(offset)
    def double_=(x: Double): Unit = self.putDouble(offset, x)
  }

  implicit val byteArrayIsPointerLike =
    _unsafe map {
      _unsafe =>
        new PointerLike[Array[Byte]] {
          def asPointer(x: Array[Byte]) = _unsafe.unsafeByteArrayPointer(x)
        }
    } getOrElse {
      new PointerLike[Array[Byte]] {
        def asPointer(x: Array[Byte]) = new SafeByteBufferPointer(ByteBuffer.wrap(x), 0)
      }
    }
  implicit val byteBufferIsPointerLike =
    _unsafe map {
      _unsafe =>
        new PointerLike[ByteBuffer] {
          def asPointer(x: ByteBuffer) = {
            val address = _unsafe.getAddress(x)
            if (address == 0) new SafeByteBufferPointer(x, 0)
            else _unsafe.unsafePointer(address)
          }
        }
    } getOrElse new PointerLike[ByteBuffer] {
      def asPointer(x: ByteBuffer) = new SafeByteBufferPointer(x, 0)
    }
  def asPointer[A: PointerLike]: A => Pointer = x => implicitly[PointerLike[A]].asPointer(x)
}