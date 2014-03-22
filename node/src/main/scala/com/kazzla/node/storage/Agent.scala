/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node.storage

import com.kazzla.asterisk.codec.MsgPackCodec
import com.kazzla.asterisk.netty.Netty
import com.kazzla.asterisk.{Session, Node}
import com.kazzla.core.cert._
import com.kazzla.core.io._
import com.kazzla.node.Domain
import com.kazzla.service.Version
import java.io.{IOException, File}
import java.net.URL
import java.security.KeyStore
import java.util.concurrent.Executors
import javax.net.ssl.SSLContext
import org.slf4j.LoggerFactory
import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Await}

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

	val storage = new Storage(dataDir)

	implicit val threads = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())

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
				.serve(new StorageNodeImpl(storage))
				.build())
		logger.info(s"activate kazzla node: [${regionServices.mkString(",")}]")
		val session = connect()
		val remote = session.bind(classOf[com.kazzla.service.Domain])
		Await.result(remote.handshake(Version(1, 0, 0).toInt), Duration.Inf)
	}

	def stop():Unit = {
		node.foreach{ _.shutdown() }
		threads.shutdown()
	}

	def ssl:SSLContext = {
		val ks = KeyStore.getInstance("JKS")
		usingInput(new File(dataDir, "node.jks")){ in => ks.load(in, "000000".toCharArray) }
		ks.getSSLContext("000000".toCharArray)
	}

	@tailrec
	private[this] def connect():Session = {
		node match {
			case Some(n) =>
				val addr = domain.pickup()
				logger.debug(s"connecting to: $addr")
				val future = n.connect(addr, Some(ssl))
				try {
					val session = Await.result(future, Duration.Inf)
					logger.info(s"connection success to server: ${session.wire.peerName}")
					return session
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
		def parse(params:List[String]):Seq[(String,String)] = params match {
			case key :: value :: rest if key.startsWith("-") && ! value.startsWith("-") =>
				(key -> value) +: parse(rest)
			case key :: rest if key.startsWith("-") => (key -> "") +: parse(rest)
			case value :: rest => ("" -> value) +: parse(rest)
			case List() => Seq()
		}
		val params = parse(args.toList).groupBy{ _._1 }.map{ case (key, kvs) => key -> kvs.map{ _._2 } }.toMap

		val dir = new File(params.getOrElse("-d", Seq(".")).head)
		val regions = params.getOrElse("", Seq()).map{ url => new URL(url) }

		val server = new Agent(dir, regions)
		server.start()
	}

}
