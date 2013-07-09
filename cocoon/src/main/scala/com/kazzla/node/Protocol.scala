/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node

import java.nio._
import org.msgpack._
import org.msgpack.unpacker._

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Protocol
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object Protocol {
	private[Protocol] val logger = org.apache.log4j.Logger.getLogger(Protocol.getClass)

	def parse(buffer:ByteBuffer):Option[Message] = {
		if(buffer.remaining() >= 4){
			buffer.order(ByteOrder.BIG_ENDIAN)
			val length = buffer.getInt()
			if(buffer.remaining() >= length){

				// メッセージ部分の ByteBuffer を切り取り
				val buf = buffer.slice()
				buf.limit(length)
				buffer.position(buffer.position() + length)

				val msgpack = new MessagePack()
				val unpacker = msgpack.createBufferUnpacker(buf)
				parse(unpacker)
			} else {
				buffer.position(0)
				None
			}
		} else {
			None
		}
	}

	private[this] def parse(unpacker:BufferUnpacker):Option[Message] = {
		unpacker.readByte() & 0xFF match {
			case 0x00 => Some(Noop())
			case 0x01 => Some(Open(
				unpacker.readShort()      // version
			))
			case 0x02 => Some(Close())
			case other =>
				logger.debug("unexpected message type: 0x%02X".format(other))
				None
		}
	}

	abstract class Message()
	case class Noop() extends Message
	case class Close() extends Message
	case class Open(version:Short) extends Message

}

