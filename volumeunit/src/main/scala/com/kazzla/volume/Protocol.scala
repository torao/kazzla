package com.kazzla.volume

import java.io._
import java.nio._
import java.util._
import scala.annotation._

/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Protocol
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */

object Protocol {

	/**
	 * 0-15: 作成するブロックの UUID
	 * 16-19: ブロックの長さ
	 */
	val CREATE_BLOCK = 10

	val READ_BLOCK = 11

	val UPDATE_BLOCK = 12

	val DELETE_BLOCK = 13

	implicit def richStream(in:DataInputStream) = new {
		def readUUID():UUID = {
			val msb = in.readLong()
			val lsb = in.readLong()
			return new UUID(msb, lsb)
		}
		def readFully(length:Int = Int.MaxValue):Array[Byte] = {
			readFully(in, new ByteArrayOutputStream(), new Array[Byte](1024), length)
		}

		@tailrec
		private[this] def readFully(in:InputStream, out:ByteArrayOutputStream, buffer:Array[Byte], remainings:Int):Array[Byte] = {
			val request = math.min(buffer.length, remainings)
			val len = in.read(buffer, 0, request)
			if(len > 0){
				out.write(buffer, 0, len)
				readFully(in, out, buffer, remainings - len)
			} else {
				out.toByteArray
			}
		}
	}

	implicit def ritchStream(out:DataOutputStream) = new {
		def writeUUID(uuid:UUID):Unit = {
			out.writeLong(uuid.getMostSignificantBits)
			out.writeLong(uuid.getLeastSignificantBits)
		}
		def writeUnsignedInt(value:Long):Unit = out.writeInt(value.toInt)
	}

	// =========================================================================
	// =========================================================================
	/**
	  */
	def read(in:DataInputStream):Protocol = {
		val code = in.readUnsignedByte() & 0xFF
		val length = in.readUnsignedShort() & 0xFFFF
		val bin = new Array[Byte](length)
		in.readFully(bin)

		code match {
			case CREATE_BLOCK => new CreateBlock(new DataInputStream(new ByteArrayInputStream(bin)))
			case READ_BLOCK => new ReadBlock(new DataInputStream(new ByteArrayInputStream(bin)))
			case UPDATE_BLOCK => new UpdateBlock(new DataInputStream(new ByteArrayInputStream(bin)))
			case DELETE_BLOCK => new DeleteBlock(new DataInputStream(new ByteArrayInputStream(bin)))
			case unknown => new Unknown(unknown, bin)
		}
	}

	def write(out:DataOutputStream, packet:Protocol):Unit = {
		val baos = new ByteArrayOutputStream()
		val dos = new DataOutputStream(baos)
		packet.writeTo(dos)
		dos.flush()
		val payload = baos.toByteArray
		out.writeByte(packet.code & 0xFF)
		out.writeShort(payload.length & 0xFFFF)
		out.write(payload)
	}

	class Unknown(code:Int, data:Array[Byte]) extends Protocol(code){
		override def writeTo(out:DataOutputStream):Unit = {
			out.write(data)
		}
	}

	class CreateBlock(blockId:UUID, blockSize:Long) extends Protocol(CREATE_BLOCK) {
		def this(in:DataInputStream) = {
			this(in.readUUID(), in.readLong())
		}
		override def writeTo(out:DataOutputStream):Unit = {
			out.writeUUID(blockId)
			out.writeLong(blockSize)
		}
	}

	class ReadBlock(blockId:UUID, offset:Long, length:Int) extends Protocol(READ_BLOCK){
		def this(in:DataInputStream) = {
			this(in.readUUID(), in.readLong(), in.readUnsignedShort())
		}
		override def writeTo(out:DataOutputStream):Unit = {
			out.writeUUID(blockId)
			out.writeLong(offset)
			out.writeShort(length & 0xFFFF)
		}
	}

	class UpdateBlock(blockId:UUID, offset:Long, length:Int, data:Array[Byte]) extends Protocol(UPDATE_BLOCK){
		private[this] def this(blockId:UUID, offset:Long, length:Int, in:DataInputStream) = {
			this(blockId, offset, length, in.readFully(length))
		}
		def this(in:DataInputStream) = {
			this(in.readUUID(), in.readLong(), in.readUnsignedShort(), in)
		}
		override def writeTo(out:DataOutputStream):Unit = {
			out.writeUUID(blockId)
			out.writeLong(offset)
			out.writeShort(length & 0xFFFF)
			out.write(data)
		}
	}

	class DeleteBlock(blockId:UUID) extends Protocol(DELETE_BLOCK){
		def this(in:DataInputStream) = {
			this(in.readUUID())
		}
		override def writeTo(out:DataOutputStream):Unit = {
			out.writeUUID(blockId)
		}
	}
}

abstract class Protocol(val code:Int) {
	def writeTo(out:DataOutputStream):Unit
}
