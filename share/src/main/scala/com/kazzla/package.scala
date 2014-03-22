/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com

import java.util.UUID
import java.nio.{ByteOrder, ByteBuffer}

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
}
