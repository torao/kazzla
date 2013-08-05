/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io.irpc

import java.nio.channels._
import org.slf4j._
import com.kazzla.core.io.using
import java.net._
import java.io._
import scala._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Server
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Server private[Server](name:String, factory:()=>ServerSocketChannel, context:Context, service:Object, codec:Codec) {

	private[this] var worker:Option[Worker] = None

	def startup():Unit = synchronized {
		if(worker.isEmpty){
			worker = Some(new Worker(context))
			worker.get.start()
		}
	}

	def shutdown():Unit = synchronized {
		worker.foreach { w =>
			w.interrupt()
			w.join()
		}
		worker = None
	}

	private[this] class Worker(context:Context) extends Thread {
		override def run():Unit = using(factory()){ server =>
			server.socket().setSoTimeout(3 * 1000)
			while(! this.isInterrupted){
				try {
					val client = server.accept()
					context.newSession(name(client.getRemoteAddress))
						.codec(codec)
						.on(client)
						.passive()
						.service(service)
						.create()
				} catch {
					case ex:InterruptedIOException => None
				}
			}
		}
		private[this] def name(addr:SocketAddress):String = addr match {
			case i:InetSocketAddress => i.getHostName + ":" + i.getPort
			case u => u.toString
		}
	}

}

object Server {
	private[Server] val logger = LoggerFactory.getLogger(classOf[Server])

	def newBuilder(name:String) = new Builder(name)

	class Builder private[Server](name:String) {
		var context:Option[Context] = None
		var service:Option[Object] = None
		var codec:Codec = new MsgpackCodec()
		var factory:Option[()=>ServerSocketChannel] = None
		def _context(c:Context) = {
			context = Option(c)
			this
		}
		def _service(s:Object) = {
			service = Option(s)
			this
		}
		def _codec(c:Codec) = {
			codec = c
			this
		}
		def bind(f: =>ServerSocketChannel):Builder = {
			factory = Option({ () => f })
			this
		}
		def bind(port:Int):Builder = bind(new InetSocketAddress(port))
		def bind(addr:SocketAddress):Builder = bind {
			val ch = ServerSocketChannel.open()
			ch.bind(addr)
		}
		def create():Server = {
			if(context.isEmpty){
				throw new IllegalArgumentException("context not specified")
			}
			if(service.isEmpty){
				throw new IllegalArgumentException("service not specified")
			}
			new Server(name, factory.get, context.get, service.get, codec)
		}
	}

}