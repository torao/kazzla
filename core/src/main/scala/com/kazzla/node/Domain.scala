/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node

import com.kazzla.core.io._
import java.net.{InetSocketAddress, SocketAddress, URL}
import org.slf4j.LoggerFactory
import scala.annotation.tailrec
import scala.io.Source
import scala.util.Random
import java.util.concurrent.atomic.{AtomicReference, AtomicInteger}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Domain
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Domain(regionServices:Seq[URL]){

	import Domain._

	/**
	 * 接続先のランダム選択のための
	 */

	/**
	 * このインスタンスがキャッシュしている、ドメインに対するソケットアドレス。`pickup()` が呼び出されるごとに先頭
	 * から順に利用されてゆき空になると一覧を再取得する。
	 */
	private[this] val addresses = new AtomicReference[Seq[SocketAddress]](Seq())

	// ==============================================================================================
	// ドメイン接続先の参照
	// ==============================================================================================
	/**
	 * このドメインに接続するためのソケットアドレスを参照します。
	 */
	@tailrec
	final def pickup():SocketAddress = {
		val expected = addresses.get()
		val addr = {
			if(expected.isEmpty){
				getSocketAddresses()
			} else {
				expected
			}
		}
		if(addresses.compareAndSet(expected, addr.drop(1))){
			pickup()
		} else {
			addr.head
		}
	}

	// ==============================================================================================
	// ドメイン接続先の参照
	// ==============================================================================================
	/**
	 * このドメインの接続先を参照します。
	 */
	@tailrec
	private[this] def getSocketAddresses(errorCount:Int = 0):Seq[SocketAddress] = {
		val random = new Random()
		Domain.getSocketAddresses(random.shuffle(regionServices)) match {
			case Some(addr) => addr
			case None =>
				Thread.sleep(100 * (1L << math.min(errorCount, 8)))   // 最大 25.6[sec]
				getSocketAddresses(math.max(errorCount, errorCount + 1))
		}
	}

}

object Domain {
	private[Domain] val logger = LoggerFactory.getLogger(classOf[Domain])

	// ==============================================================================================
	// ドメイン接続先の参照
	// ==============================================================================================
	/**
	 * 指定された Region Server から接続先の一覧を参照します。
	 */
	@tailrec
	private[Domain] def getSocketAddresses(servers:Seq[URL]):Option[Seq[SocketAddress]] = {
		if(servers.isEmpty){
			None
		} else {
			try {
				val con = servers.head.openConnection()
				val addresses = using(con.getInputStream){ in =>
					Source.fromInputStream(in, "UTF-8").getLines().map{ line =>
						line.split(":", 2) match {
							case Array(host:String, port:String) =>
								Some(new InetSocketAddress(host, port.toInt))
							case _ =>
								None
						}
					}.filter{ _.isDefined }.map{ _.get }.toSeq
				}
				if(! addresses.isEmpty){
					return Some(addresses)
				}
			} catch {
				case ex:Exception =>
					logger.error(s"fail to retrieve domain list: $servers.head", ex)
			}
			getSocketAddresses(servers.drop(1))
		}
	}
}