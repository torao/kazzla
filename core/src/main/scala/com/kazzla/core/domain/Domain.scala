/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.domain

import java.net.{InetSocketAddress, URL}
import java.lang.reflect.{Method, InvocationHandler, Proxy}
import scala.xml._
import com.kazzla.core.protocol._
import com.kazzla.core.io
import com.kazzla.core.io.irpc.{Context, Session}
import scala.util.Random
import java.nio.channels.SocketChannel
import java.io.IOException

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Domain
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Domain(val url:URL){
	private[this] var _stub:Option[Stub] = None
	private[this] def stub = _stub match {
		case Some(s) => s
		case None =>
			val root = XML.load(url)
			_stub = Some(Stub(
				root \ "@name" text,
				root \ "regions" \ "region" map { x => new URL(x.text) }
			))
			_stub.get
	}

	def name:String = stub.name

	def newRegionNode(context:Context, volume:Volume):Node[Volume,Region] = {
		new Node[Volume,Region]("RegionServer", context, volume, classOf[Region], { stub.regions })
	}

	private[this] case class Stub(name:String, regions:Seq[URL])

	class Node[T,U] private[Domain](val name:String, context:Context, local:T, remoteInterface:Class[U], urls: =>Seq[URL]) {
		private[this] var session:Option[Session] = None

		lazy val service:U = Proxy.newProxyInstance(
			Thread.currentThread().getContextClassLoader,
			Array(classOf[U]), new InvocationHandler {
				def invoke(proxy:Any, method:Method, args:Array[AnyRef]):AnyRef = {
					if(session.isEmpty || ! session.get.isOpen){
						close()
						session = Some(connect(context, local))
					}
					val remote = session.get.getInterface(remoteInterface)
					method.invoke(remote, args)
				}
			}
		).asInstanceOf[U]

		def close():Unit = {
			session.foreach{ _.close() }
		}

		private[this] def connect(context:Context, local:T):Session = {
			new Random().shuffle(urls).foreach { url =>
				val s = SocketChannel.open(new InetSocketAddress(url.getHost, url.getPort))
				return context.newSession(name).on(s).service(local).create()
			}
			throw new IOException(s"valid server not found: $urls")
		}
	}
}

object Domain {
}
