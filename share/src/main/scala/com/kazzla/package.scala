/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com

import java.util.UUID
import java.nio.{ByteOrder, ByteBuffer}
import org.asterisque.msg.Block
import java.security.MessageDigest

package object kazzla {

	implicit class IUUID(i:UUID) {
		def toByteArray:Array[Byte] = {
			// UUID の MostSigBits は下位バイトに文字列表記上先頭のバイトが配置されている (リトルエンディアン)
			ByteBuffer
				.allocate(java.lang.Long.SIZE * 2)
				.order(ByteOrder.LITTLE_ENDIAN)
				.putLong(i.getMostSignificantBits)
				.putLong(i.getLeastSignificantBits).array()
		}
	}

	implicit class IByteArray(b:Array[Byte]) {
		def toMD5:Array[Byte] = digest("MD5")
		def digest(algorithm:String):Array[Byte] = {
			MessageDigest.getInstance(algorithm).digest(b)
		}
		def toUUID:UUID = {
			val bin = ByteBuffer.wrap(b)
			new UUID(bin.getLong, bin.getLong)
		}
	}

	implicit class IBlock(block:Block) {
		def toInt:Int = block.toByteBuffer.getInt
		def toLong:Long = block.toByteBuffer.getLong
	}
}
