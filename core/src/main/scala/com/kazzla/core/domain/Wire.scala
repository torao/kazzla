/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.domain

import com.kazzla.core._
import com.kazzla.core.io.irpc._
import java.net._
import java.nio.channels._
import scala.util._
import java.io._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Wire
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Wire[T,U](name:String, context:io.irpc.Context, local:T, remoteInterface:Class[U], urls: =>Seq[URL]) {
	private[this] var _session:Option[Session] = None
	def session:Session = synchronized {
		if(_session.isEmpty || ! _session.get.isOpen){
			_session.foreach{ _.close() }
			_session = Some(connect())
		}
		_session.get
	}
	def peer:U = session.getInterface(remoteInterface)
	private[this] def connect():Session = {
		new Random().shuffle(urls).foreach { url =>
			val s = SocketChannel.open(new InetSocketAddress(url.getHost, url.getPort))
			return context.newSession(name).on(s).service(local).create()
		}
		throw new IOException(s"valid server not found: $urls")
	}
}