/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.storage

import java.util.UUID

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Fragment
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * ファイルのデータフラグメントを表します。
 * ファイルの内容はブロック単位に分割されノードに分散されます。フラグメントはこのブロック内のデータを表します。
 *
 * @param fileId このフラグメントを含むファイル ID
 * @param offset ファイル内でのオフセット
 * @param length フラグメントの長さ
 * @param blockId このフラグメントを含むブロック ID
 * @param blockOffset ブロック内のでのオフセット
 * @param locations このフラグメントを含むノードのロケーション
 */
case class Fragment(fileId:UUID, offset:Long, length:Int, blockId:UUID, blockOffset:Int, locations:Seq[Location]) {
/*
	def toByteArray:Array[Byte] = {
		val msgpack = new MessagePack()
		val packer = msgpack.createBufferPacker()
		MsgPackCodec.encode(packer, this)
		packer.toByteArray
	}
*/
}

object Fragment {
/*
	def fromBlock(block:Block):Fragment = {
		val msgpack = new MessagePack()
		val unpacker = msgpack.createBufferUnpacker(block.payload, block.offset, block.length)
		MsgPackCodec.decode(unpacker).asInstanceOf[Fragment]
	}
*/
}
