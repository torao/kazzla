/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node.storage

import com.kazzla.asterisk.Node
import com.kazzla.asterisk.codec.MsgPackCodec
import com.kazzla.asterisk.netty.Netty
import com.kazzla.core.cert._
import com.kazzla.core.io._
import com.kazzla.node.Domain
import java.io.{IOException, File}
import java.net.URL
import java.security.KeyStore
import javax.net.ssl.SSLContext
import org.slf4j.LoggerFactory
import scala.concurrent.duration.Duration
import scala.concurrent.Await
import scala.annotation.tailrec

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Agent
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Agent(dataDir:File, regionServices:Seq[URL]) {

	import Agent._

	var node:Option[Node] = None

	val domain = new Domain(regionServices)

	// ==============================================================================================
	// 実体ファイルの参照
	// ==============================================================================================
	/**
	 * 指定された UUID のブロックに対するローカルファイルを参照します。
	 * @return ブロックファイル
	 */
	def start():Unit = {

		node = Some(Node("storage")
				.bridge(Netty)
				.codec(MsgPackCodec)
				.serve(new StorageImpl(dataDir))
				.build())
		logger.info(s"activate kazzla node: [${regionServices.mkString(",")}]")
		connect()
	}

	def stop():Unit = {
		node.foreach{ _.shutdown() }
	}

	def ssl:SSLContext = {
		val ks = KeyStore.getInstance("JKS")
		usingInput(new File(dataDir, "node.jks")){ in => ks.load(in, "000000".toCharArray) }
		ks.getSSLContext("000000".toCharArray)
	}

	@tailrec
	private[this] def connect():Unit = {
		node match {
			case Some(n) =>
				val addr = domain.pickup()
				logger.debug(s"connecting to: $addr")
				val future = n.connect(addr, Some(ssl))
				try {
					val session = Await.result(future, Duration.Inf)
					logger.info(s"connection success to server: ${session.wire.peerName}")
					val remote = session.bind(classOf[com.kazzla.service.Domain])
					remote.handshake()
					return
				} catch {
					case ex:Exception =>
						logger.error("fail to connect server", ex)
				}
			case None =>
				throw new IOException("node is not started")
		}
		connect()
	}

}

object Agent {
	private[Agent] val logger = LoggerFactory.getLogger(classOf[Agent])

	// ==============================================================================================
	// 実体ファイルの参照
	// ==============================================================================================
	/**
	 * 指定された UUID のブロックに対するローカルファイルを参照します。
	 * @return ブロックファイル
	 */
	def main(args:Array[String]):Unit = {

		// コマンドライン引数の解析
		var dir:File = new File(".")
		var regions = List[URL]()
		lazy val parse:(List[String])=>Unit = {
			case "-d" :: d :: rest =>
				dir = new File(d)
				parse(rest)
			case url :: rest =>
				regions ::= new URL(url)
				parse(rest)
			case List() => None
		}
		parse(args.toList)

		val server = new Agent(dir, regions)
		server.start()
	}

}
