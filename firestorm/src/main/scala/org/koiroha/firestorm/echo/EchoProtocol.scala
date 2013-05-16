/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
 */

package org.koiroha.firestorm.echo

import org.koiroha.firestorm.core.{Server, Context, Endpoint}

class EchoServer(context:Context) extends org.koiroha.firestorm.core.Server(context) {
	onAccept(EchoProtocol.accept)
}

class EchoClient(context:Context) extends Endpoint(context){
}

object EchoProtocol {
	def accept(server:Server, endpoint:Endpoint){
		endpoint.onArrivalBufferedIn { e =>
			val buffer = e.in.slice{ _.remaining() }
			e.out.write(buffer.get)
		}
	}
}
