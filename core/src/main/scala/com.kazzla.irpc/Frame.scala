/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.irpc

import java.nio.ByteBuffer
import org.msgpack.{MessageTypeException, MessagePack}
import org.msgpack.packer.BufferPacker
import scala.collection.JavaConversions._
import java.util.UUID
import java.io.{EOFException, IOException}
import org.msgpack.unpacker.BufferUnpacker
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Frame
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
sealed abstract class Frame(val pipeId:Short)

case class Open(override val pipeId:Short, function:Short, params:AnyRef*) extends Frame(pipeId)

case class Close[T](override val pipeId:Short, result:T, errorMessage:String) extends Frame(pipeId)

/**
 * 長さが 0 以下の
 */
case class Block(override val pipeId:Short, binary:Array[Byte], offset:Int, length:Int) extends Frame(pipeId) {
	def isEOF:Boolean = (length <= 0)
}

object Block {
	private[this] val empty = Array[Byte]()
	def eof(id:Short) = Block(id, empty)
	def apply(pipeId:Short, binary:Array[Byte]):Block = Block(pipeId, binary, 0, binary.length)
}

object Frame {
	private[Frame] val logger = LoggerFactory.getLogger(classOf[Frame])
	val TYPE_OPEN:Byte = 1
	val TYPE_CLOSE:Byte = 2
	val TYPE_BLOCK:Byte = 3

	def encode(packet:Frame):ByteBuffer = {
		val msgpack = new MessagePack()
		val packer = msgpack.createBufferPacker()
		packet match {
			case o:Open =>
				packer.write(TYPE_OPEN)
				packer.write(o.pipeId)
				packer.write(o.function)
				encode(packer, o.params.toSeq)
			case c:Close[_] =>
				packer.write(TYPE_CLOSE)
				packer.write(c.pipeId)
				packer.write(c.errorMessage == null)
				if(c.errorMessage == null){
					encode(packer, c.result)
				} else {
					encode(packer, c.errorMessage)
				}
			case b:Block =>
				packer.write(TYPE_BLOCK)
				packer.write(b.pipeId)
				packer.write(b.binary, b.offset, b.length)
		}
		if(logger.isTraceEnabled){
			logger.trace(s"$packet -> ${packer.getBufferSize} bytes")
		}
		ByteBuffer.wrap(packer.toByteArray)
	}

	def decode(buffer:ByteBuffer):Option[Frame] = try {
		val msgpack = new MessagePack()
		val unpacker = msgpack.createBufferUnpacker(buffer)
		unpacker.readByte() match {
			case TYPE_OPEN =>
				val pipeId = unpacker.readShort()
				val port = unpacker.readShort()
				val params = decode(unpacker).asInstanceOf[Array[AnyRef]]
				buffer.position(unpacker.getReadByteCount)
				Some(Open(pipeId, port, params:_*))
			case TYPE_CLOSE =>
				val pipeId = unpacker.readShort()
				val success = unpacker.readBoolean()
				val (result, error) = if(success){
					(decode(unpacker), null)
				} else {
					(null, decode(unpacker).asInstanceOf[String])
				}
				buffer.position(unpacker.getReadByteCount)
				Some(Close(pipeId, result, error))
			case TYPE_BLOCK =>
				val pipeId = unpacker.readShort()
				val binary = unpacker.readByteArray()
				buffer.position(unpacker.getReadByteCount)
				Some(Block(pipeId, binary))
			case unknown =>
				throw new CodecException(f"unsupported frame-type: 0x$unknown%02X")
		}
	} catch {
		case ex:EOFException =>
			// logger.trace(ex.toString)
			None
		case ex:MessageTypeException =>
			logger.trace("", ex)
			None
	}

	private[this] def encode(packer:BufferPacker, value:Any):Unit = {
		if(value == null){
			packer.write(0.toByte)
			packer.writeNil()
		} else value match {
			case i:Byte =>
				packer.write(1.toByte)
				packer.write(i)
			case i:Char =>
				packer.write(2.toByte)
				packer.write(i.toShort)
			case i:Short =>
				packer.write(3.toByte)
				packer.write(i)
			case i:Int =>
				packer.write(4.toByte)
				packer.write(i)
			case i:Long =>
				packer.write(5.toByte)
				packer.write(i)
			case i:Float =>
				packer.write(6.toByte)
				packer.write(i)
			case i:Double =>
				packer.write(7.toByte)
				packer.write(i)
			case i:Array[Byte] =>
				packer.write(8.toByte)
				packer.write(i)
			case i:String =>
				packer.write(9.toByte)
				packer.write(i)
			case i:UUID =>
				packer.write(10.toByte)
				packer.write(i.getMostSignificantBits)
				packer.write(i.getLeastSignificantBits)
			case i:Map[_,_] =>
				packer.write(101.toByte)
				packer.writeMapBegin(i.size * 4)
				i.foreach{ case (k, v) =>
					encode(packer, k)
					encode(packer, v)
				}
				packer.writeMapEnd()
			case i:Seq[_] =>
				packer.write(100.toByte)
				packer.writeArrayBegin(i.size * 2)    // type x value x n
				i.foreach{ x =>
					encode(packer, x)
				}
				packer.writeArrayEnd()
			case i:java.util.List[_] =>
				packer.write(100.toByte)
				packer.writeArrayBegin(i.size())
				i.foreach{ x => encode(packer, x) }
				packer.writeArrayEnd()
			case unsupported =>
				throw new CodecException(s"unsupported data-type: ${unsupported.getClass} ($unsupported)")
		}
	}

	private[this] def decode(unpacker:BufferUnpacker):Any = {
		unpacker.readByte() match {
			case 0 =>
				unpacker.readNil()
				null
			case 1 => unpacker.readByte()
			case 2 => unpacker.readShort().toChar
			case 3 => unpacker.readShort()
			case 4 => unpacker.readInt()
			case 5 => unpacker.readLong()
			case 6 => unpacker.readFloat()
			case 7 => unpacker.readDouble()
			case 8 => unpacker.readByteArray()
			case 9 => unpacker.readString()
			case 10 => new UUID(unpacker.readLong(), unpacker.readLong())
			case 100 =>
				val length = unpacker.readArrayBegin() / 2
				val array = for(i <- 0 until length) yield{
					decode(unpacker)
				}
				unpacker.readArrayEnd()
				array.toArray
			case 101 =>
				val length = unpacker.readMapBegin() / 4
				val map = (0 until length).map{ _ =>
					val k = decode(unpacker)
					val v = decode(unpacker)
					k -> v
				}.toMap
				unpacker.readMapEnd()
				map
			case unsupported =>
				throw new CodecException(f"unsupported data-type: 0x$unsupported%02X")
		}
	}

	case class CodecException(msg:String) extends IOException(msg)


	final class Queue {
		private[this] val queue = new AtomicReference(List[Frame]())
		private[this] var _onPut:Option[()=>Unit] = None
		private[this] var _onEmpty:Option[()=>Unit] = None

		@tailrec
		def put(frame:Frame):Unit = {
			val q = queue.get()
			if(queue.compareAndSet(q, q :+ frame)){
				_onPut.foreach { _() }
			} else {
				put(frame)
			}
		}

		def head():Option[Frame] = {
			val q = queue.get()
			if(q.size == 0){
				None
			} else {
				Some(q.head)
			}
		}

		@tailrec
		def take():Option[Frame] = {
			val q = queue.get()
			if(q.size == 0){
				return None
			} else if(queue.compareAndSet(q, q.drop(1))){
				if(q.size == 1){
					_onEmpty.foreach{ _() }
				}
				return Some(q.head)
			}
			take()
		}

		def onPut(f: =>Unit):Unit = {
			_onPut = Some({ () => f })
		}
		def onEmpty(f: =>Unit):Unit = {
			_onEmpty = Some({ () => f })
		}

	}
}
