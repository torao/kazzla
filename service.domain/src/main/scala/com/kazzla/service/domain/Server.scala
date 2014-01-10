/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service.domain

import com.twitter.finagle.builder
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http._
import com.twitter.util.Duration
import java.io._
import java.net.InetSocketAddress
import org.slf4j.LoggerFactory

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Server
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Server(docroot:File, domain:Domain) {
	private[this] var server:Option[builder.Server] = None

	def start():Unit = {

		val service = new Service(docroot, domain)
		server = Some(
			ServerBuilder()
			.codec(RichHttp[Request](Http()))
			.bindTo(new InetSocketAddress("localhost", 8088))
			.name("HttpServer")
			.build(service)
		)
	}

	def stop():Unit = {
		server.foreach{ _.close(Duration.fromSeconds(10)) }
	}

}

object Server {
	private[Server] val logger = LoggerFactory.getLogger(classOf[Server])

	// ==============================================================================================
	// ドメインサーバの起動
	// ==============================================================================================
	/**
	 * ドメインサーバを起動します。
	 */
	def main(args:Array[String]):Unit = {
		val docroot = new File("service.domain/docroot")
		val ca = new Domain.CA(new File("service.domain/ca/demoCA"))
		val domain = new Domain("com.kazzla", ca)
		val server = new Server(docroot, domain)
		server.start()
		// TODO JMX で終了が呼び出されるまで待機
	}

}
