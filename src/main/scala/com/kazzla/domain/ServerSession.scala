/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain

import org.apache.log4j.Logger
import com.kazzla.irpc._
import javax.net.ServerSocketFactory
import java.nio.channels.{SelectionKey, Selector, ServerSocketChannel}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// ServerSession
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * <p>
 * 異なるセッション間で接続が共有されることはありません。
 * </p>
 * @author Takami Torao
 */
class ServerSession private[domain](domain:Domain, addresses:ServerAddress*) extends Session(domain) {
	import ServerSession.logger

	// ========================================================================
	// 接続受付スレッド
	// ========================================================================
	/**
	 * このサーバへの接続を受け付けるスレッドです。
	 */
	private[this] var serverThread:Option[Thread] = None

	// ========================================================================
	// 呼び出し処理
	// ========================================================================
	/**
	 * このセッション上で現在実行中の呼び出し処理です。
	 */
	private[this] var localProcessing = Map[Call,Session.Processing]()

	// ========================================================================
	// サーバの開始
	// ========================================================================
	/**
	 * このセッション上でのサーバを開始します。
	 */
	def startService():Unit = synchronized{

	}

	// ========================================================================
	// サーバの開始
	// ========================================================================
	/**
	 * このセッション上でのサーバを開始します。
	 */
	def stopService():Unit = synchronized{
	}

	// ========================================================================
	// セッションのクローズ
	// ========================================================================
	/**
	 * このセッションをクローズし使用していたリソースを全て開放します。
	 */
	override def close() {
		super.close()
	}

	private[ServerSession] class Server extends Runnable {

		// ======================================================================
		// 接続の受付
		// ======================================================================
		/**
		 * 接続の受付処理を行います。
		 */
		def run(){
			var selector:Option[Selector] = None
			var servers = List[ServerSocketChannel]()
			try {

				// セレクタの構築
				selector = Some(Selector.open())

				// サーバソケットのオープン
				// n 個目で例外が発生した場合に n-1 個目までを確実にクローズするため逐次代入してゆく
				addresses.foreach{ address =>
					val channel = address.create()
					servers ::= channel
					channel.configureBlocking(false)
					channel.register(selector.get, SelectionKey.OP_ACCEPT)
				}

				// 接続受付の開始
				while(! Thread.currentThread().isInterrupted){
					val keys = selector.get.selectedKeys()
					val it = keys.iterator()
					while(it.hasNext){
						val key = it.next()
						it.remove()

						assert(key.isAcceptable)
						val channel = key.channel().asInstanceOf[ServerSocketChannel]
						val client = channel.accept()
					}
				}

				// サーバソケットをセレクタにと売る億
			} finally {
				ServerSession.closeAll(servers)
				selector.foreach{ _.close() }
			}
		}

	}

}

object ServerSession {
	private[ServerSession] val logger = Logger.getLogger(classOf[ServerSession])

	private[ServerSession] def closeAll(servers:Seq[ServerSocketChannel]){
		servers.foreach{ channel =>
			try {
				channel.close
			} catch {
				case ex:Exception =>
					logger.error("fail to close server socket: " + channel, ex)
			}
		}
	}


}