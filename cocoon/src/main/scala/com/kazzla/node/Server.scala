/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.kazzla.node

import com.kazzla.util._
import java.io._
import java.net._
import java.nio.channels._
import scala._
import scala.annotation._

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Server
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 *
 * @author Takami Torao
 * @param dir ディレクトリ
 * @param name サーバ名
 * @param default デフォルト設定
 */
class Server(dir:File, name:String, default:Config)(exec: =>Unit) extends Closeable{
	lazy val configDirectory = new File(dir, "conf")
	lazy val config = {
		val file = new File(configDirectory, name + ".properties")
		if(file.exists()){
			new Config(file.toURI.toURL).combine(default)
		} else {
			default
		}
	}

	private[this] var server:Option[ServerSocketChannel] = None
	private[this] var selector:Option[Selector] = None
	private[this] var thread:Option[Thread] = None

	def running = thread.isDefined

	def apply():Unit = {
		if(thread.isEmpty){
			this.thread = Some(new Thread(new Runnable(){ def run() = serve() }, name))
			this.thread.get.start()
		}
	}

	private[this] def serve():Unit = try {
		config.getInt("server.port") match {
			case Some(port) =>
				server = Some(new ServerSocket(port).getChannel)
				selector = Some(Selector.open())
				server.foreach{ s =>
					s.configureBlocking(false)
					s.register(selector.get, SelectionKey.OP_ACCEPT)
				}
			case None =>
				Server.logger.error("server.port")
		}
	} catch {
		case ex:IOException =>
			if(running){
				Server.logger.fatal("unexpected exception, server down", ex)
			}
	} finally {
		close()
	}

	@tailrec
	private[this] def listen():Unit = {
		val count = selector.get.select()
		if(count > 0){
			val keys = selector.get.selectedKeys()
			val it = keys.iterator()
			while(it.hasNext){
				val key = it.next()
				it.remove()
				if(key.isAcceptable){
					val client = key.channel().asInstanceOf[ServerSocketChannel].accept()
				}
			}
		} else if(! running){
			return
		}
		listen()
	}

	def close():Unit = {
		// サーバソケットクローズ時の例外で running == false をチェックするために先にスレッドを停止
		thread.foreach{ _.interrupt() }
		thread = None
		server.foreach{ _.close() }
		server = None
		selector.foreach{ _.close() }
		selector = None
	}

}

object Server {
	val logger = org.apache.log4j.Logger.getLogger(Server.getClass)
}