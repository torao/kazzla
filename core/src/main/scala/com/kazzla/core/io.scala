/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core

import java.io._
import org.slf4j._
import java.net.{InetSocketAddress, SocketAddress}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// io
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
package object io {
	import scala.language.reflectiveCalls
	private[this] val logger = LoggerFactory.getLogger("com.kazzla.core.io")

	def close(cs:Closeable*):Unit = IO.close(cs:_*)

	def close[T <% { def close():Unit }](cs:T*):Unit = cs.filter{ _ != null }.foreach { c =>
		try {
			c.close()
		} catch {
			case ex:IOException =>
				logger.warn("fail to close object", ex)
		}
	}

	def using[T <% { def close():Unit }, U](resource:T)(f:(T)=>U):U = try {
		f(resource)
	} finally {
		close(resource)
	}

	implicit class RSocketAddress(addr:SocketAddress) {
		def getName:String = addr match {
			case i:InetSocketAddress =>
				s"${i.getAddress.getHostAddress}:${i.getPort}"
			case s => s.toString
		}
	}

}

