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

import scala.language.experimental.macros
import scala.reflect.macros._
import akka.util._
import com.google.common.io._
import scala.util.control._
import scala.util.control.Exception._
import scala.collection.mutable
import scala.collection.immutable._

class Pickle {
  import Pickle._
  import ReflectCore.Variant
  case class MismatchedTagException(id: Int) extends ControlThrowable
  private[this] def readVariantImpl(in: LittleEndianDataInputStream): Variant = {
    val tag = in.readByte
    tag match {
      case id @ Tag.SeqEnd.id => throw new MismatchedTagException(id)
      case id @ Tag.MapEnd.id => throw new MismatchedTagException(id)
      case Tag.Seq.id =>
        val builder = new mutable.ListBuffer[Variant]
        val result = try {
          while (true)
            builder += readVariantImpl(in)
          sys.error("[Bug] This should not be reached")
        } catch {
          case MismatchedTagException(Tag.SeqEnd.id) => builder.result
        }
        Variant.Sequence(result)
      case Tag.Map.id =>
        val builder = new mutable.ListBuffer[(String, Variant)]
        val result = try {
          while (true) {
            builder += {
              val key = readVariantImpl(in).asText.value
              val value = readVariantImpl(in)
              (key, value)
            }
          }
          sys.error("[Bug] This should not be reached")
        } catch {
          case MismatchedTagException(Tag.MapEnd.id) => builder.result
        }
        Variant.Object(ListMap(result: _*))
      case Tag.True.id => Variant.Bool(true)
      case Tag.False.id => Variant.Bool(false)
      case Tag.Int64.id => Variant.Int64(in.readLong)
      case Tag.Float64.id => Variant.Float64(in.readDouble)
      case Tag.Text.id =>
        val builder = new mutable.ArrayBuilder.ofByte
        def loop: Array[Byte] = {
          while (true) {
            val len = in.readUnsignedByte
            if (len == 0)
              return builder.result
            val buf = new Array[Byte](len)
            in.readFully(buf)
            builder ++= buf
          }
          sys.error("[Bug] This should not be reached")
        }
        val result = loop
        Variant.Text(new String(result, "UTF-8"))
      case Tag.Binary.id =>
        val builder = new mutable.ArrayBuilder.ofByte
        def loop: Array[Byte] = {
          while (true) {
            val len = in.readUnsignedByte
            if (len == 0)
              return builder.result
            val buf = new Array[Byte](len)
            in.readFully(buf)
            builder ++= buf
          }
          sys.error("[Bug] This should not be reached")
        }
        val result = loop
        Variant.Binary(ByteString(result))
      case Tag.Decimal.id =>
        val builder = new mutable.ArrayBuilder.ofByte
        def loop: Array[Byte] = {
          while (true) {
            val len = in.readUnsignedByte
            if (len == 0)
              return builder.result
            val buf = new Array[Byte](len)
            in.readFully(buf)
            builder ++= buf
          }
          sys.error("[Bug] This should not be reached")
        }
        val result = loop
        Variant.Decimal(BigDecimal(new String(result, "UTF-8")))
      case Tag.Float32.id =>
        Variant.Float32(in.readFloat)
      case Tag.InetAddress.id =>
        val builder = new mutable.ArrayBuilder.ofByte
        def loop: Array[Byte] = {
          while (true) {
            val len = in.readUnsignedByte
            if (len == 0)
              return builder.result
            val buf = new Array[Byte](len)
            in.readFully(buf)
            builder ++= buf
          }
          sys.error("[Bug] This should not be reached")
        }
        val result = loop
        val strs = new String(result, "UTF-8").split('/')
        val addr = strs(1).split(',').map(java.lang.Integer.parseInt(_).toByte)
        Variant.InetAddress(java.net.InetAddress.getByAddress(strs(0), addr))
      case Tag.Int32.id =>
        Variant.Int32(in.readInt)
      case Tag.Timestamp.id =>
        Variant.Timestamp(org.threeten.bp.Instant.ofEpochMilli(in.readLong))
      case Tag.UUID.id =>
        val msb = in.readLong
        val lsb = in.readLong
        Variant.UUID(new java.util.UUID(msb, lsb))
      case Tag.VarInt.id =>
        val builder = new mutable.ArrayBuilder.ofByte
        def loop: Array[Byte] = {
          while (true) {
            val len = in.readUnsignedByte
            if (len == 0)
              return builder.result
            val buf = new Array[Byte](len)
            in.readFully(buf)
            builder ++= buf
          }
          sys.error("[Bug] This should not be reached")
        }
        val result = loop
        val str = new String(result, "UTF-8")
        Variant.VarInt(BigInt(str, 36))
      case id => sys.error(s"Unknown tag $id")
    }
  }
  def readVariant(source: ByteString): Variant =
    try readVariantImpl(new LittleEndianDataInputStream(new java.io.ByteArrayInputStream(source.toArray))) catch {
      case MismatchedTagException(id) => sys.error(s"Mismached tag $id found while reading variant")
    }
  private[this] def writeVariantImpl(variant: Variant, sink: LittleEndianDataOutputStream): Unit = {
    variant match {
      case Variant.Undefined => // ignore
      case Variant.Bool(x) => if (x) sink.writeByte(Tag.True.id) else sink.writeByte(Tag.False.id)
      case Variant.Int64(x) =>
        sink.writeByte(Tag.Int64.id)
        sink.writeLong(x)
      case Variant.Float64(x) =>
        sink.writeByte(Tag.Float64.id)
        sink.writeDouble(x)
      case Variant.Sequence(xs) =>
        sink.writeByte(Tag.Seq.id)
        for (x <- xs)
          writeVariantImpl(x, sink)
        sink.writeByte(Tag.SeqEnd.id)
      case Variant.Object(xs) =>
        sink.writeByte(Tag.Map.id)
        for ((k, v) <- xs) {
          writeVariantImpl(Variant.Text(k), sink)
          writeVariantImpl(v, sink)
        }
        sink.writeByte(Tag.MapEnd.id)
      case Variant.Text(x) =>
        sink.writeByte(Tag.Text.id)
        for (chunk <- x.getBytes("UTF-8").iterator.grouped(255)) {
          val buf = chunk.toArray
          sink.writeByte(buf.length.toByte)
          sink.write(buf)
        }
        sink.writeByte(0)
      case Variant.Binary(x) =>
        sink.writeByte(Tag.Text.id)
        for (chunk <- x.iterator.grouped(255)) {
          val buf = chunk.toArray
          sink.writeByte(buf.length.toByte)
          sink.write(buf)
        }
        sink.writeByte(0)
      case Variant.Decimal(x) =>
        sink.writeByte(Tag.Decimal.id)
        val str = x.bigDecimal.toPlainString
        for (chunk <- str.getBytes("UTF-8").iterator.grouped(255)) {
          val buf = chunk.toArray
          sink.writeByte(buf.length.toByte)
          sink.write(buf)
        }
        sink.writeByte(0)
      case Variant.Float32(x) =>
        sink.writeByte(Tag.Float32.id)
        sink.writeFloat(x)
      case Variant.InetAddress(x) =>
        sink.writeByte(Tag.InetAddress.id)
        val str = x.toString
        for (chunk <- str.getBytes("UTF-8").iterator.grouped(255)) {
          val buf = chunk.toArray
          sink.writeByte(buf.length.toByte)
          sink.write(buf)
        }
        sink.writeByte(0)
      case Variant.Int32(x) =>
        sink.writeByte(Tag.Int32.id)
        sink.writeInt(x)
      case Variant.Timestamp(x) =>
        sink.writeByte(Tag.Timestamp.id)
        sink.writeLong(x.toEpochMilli)
      case Variant.UUID(x) =>
        sink.writeByte(Tag.UUID.id)
        sink.writeLong(x.getMostSignificantBits)
        sink.writeLong(x.getLeastSignificantBits)
      case Variant.VarInt(x) =>
        sink.writeByte(Tag.VarInt.id)
        val str = x.toString(36)
        for (chunk <- str.getBytes("UTF-8").iterator.grouped(255)) {
          val buf = chunk.toArray
          sink.writeByte(buf.length.toByte)
          sink.write(buf)
        }
        sink.writeByte(0)
    }
  }
  def writeVariant(x: Variant): ByteString = {
    val sink = new java.io.ByteArrayOutputStream
    writeVariantImpl(x, new LittleEndianDataOutputStream(sink))
    ByteString(sink.toByteArray)
  }
}
object Pickle extends Pickle {
  sealed abstract class Tag(val id: Byte)
  object Tag {
    case object True extends Tag(0)
    case object False extends Tag(1)
    case object Int32 extends Tag(2)
    case object Int64 extends Tag(3)
    case object Float32 extends Tag(4)
    case object Float64 extends Tag(5)
    case object Decimal extends Tag(6)
    case object VarInt extends Tag(7)
    case object Map extends Tag(8)
    case object MapEnd extends Tag(9)
    case object Seq extends Tag(10)
    case object SeqEnd extends Tag(11)
    case object Text extends Tag(12)
    case object Binary extends Tag(13)
    case object InetAddress extends Tag(14)
    case object Timestamp extends Tag(15)
    case object UUID extends Tag(16)
  }
}
