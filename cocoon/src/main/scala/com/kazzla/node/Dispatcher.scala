/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node

import java.nio.channels._
import scala.collection._
import scala.collection.JavaConversions._
import java.nio._

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Dispatcher
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Dispatcher(name:String, readBufferSize:Int = 8 * 1024) extends Thread {
	import Dispatcher._

	/**
	 * このディスパッチャーが使用する Selector です。
	 */
	private[this] val selector = Selector.open()

	/**
	 * ディスパッチャーのスレッド内で実行するキューです。
	 */
	private[this] val queue:mutable.Buffer[()=>Unit] = mutable.Buffer()

	/**
	 * 単一スレッドで読み込むため読み込みバッファはすべてのチャネルで共有可能。
	 */
	private[this] val readBuffer:ByteBuffer = ByteBuffer.allocateDirect(readBufferSize)

	// ========================================================================
	// チャネルの追加
	// ========================================================================
	/**
	 * このディスパッチャーで指定されたチャネルの処理を開始します。
	 * @param channel このディスパッチャーで処理するチャネル
	 * @return チャネルのセッション
	 */
	def join(channel:SelectableChannel):Session = {

		// 初期状態では何も通知しないオプションを使用
		// 読み込みはハンドラが設定されてから
		// 書き込みは出力バッファにデータが保留されてから
		// 接続はハンドラが設定されてから
		val options = 0

		// チャネルを登録してセッションを返す
		postAndWait(() => {
			channel.configureBlocking(false)
			val key = channel.register(selector, options)
			val session = new Session(this, key)
			key.attach(session)
			session
		})
	}

	override def run():Unit = {
		org.apache.log4j.NDC.push(name)
		currentDispatcher.set(this)
		logger.debug("starting dispatcher \"%s\"".format(name))
		try {
			while(! this.isInterrupted){
				if(selector.select(1000) >= 0){
					val keys = selector.selectedKeys()
					val it = keys.iterator()
					while(it.hasNext){
						val key = it.next()
						it.remove()

						val session = key.attachment().asInstanceOf[Session]
						try {
							if(key.isReadable){
								readBuffer.clear()
								val len = key.channel().asInstanceOf[ReadableByteChannel].read(readBuffer)
								if(len < 0){
									session.close()
								} else {
									session.in.get.onRead(readBuffer)
								}
							} else if(key.isWritable){
								session.out.get.onWritable(key.channel().asInstanceOf[WritableByteChannel])
							} else if(key.isAcceptable){
								val socket = key.channel().asInstanceOf[ServerSocketChannel].accept()
								session.server.get.onAccept(socket)
							}
						} catch {
							case ex:Exception =>
								logger.error("operation rejected", ex)
								session.close()
						}
					}
				}

				execQueue()
			}
		} catch {
			case ex:InterruptedException =>
				logger.debug("dispatcher interrupted")
			case ex:Throwable =>
				if(ex.isInstanceOf[ThreadDeath]){
					throw ex
				} else {
					logger.fatal("unexpected exception", ex)
				}
		} finally {
			try {
				selector.keys().map{ key => key.attachment().asInstanceOf[Session] }.foreach { _.close }
			} catch {
				case ex:Throwable => logger.error("", ex)
			}
			try {
				selector.close()
			} catch {
				case ex:Throwable => logger.error("", ex)
			}
			logger.debug("end dispatcher \"%s\"".format(name))
			currentDispatcher.set(null)
			org.apache.log4j.NDC.pop()
		}
	}

	// ========================================================================
	// ディスパッチャーの終了
	// ========================================================================
	/**
	 * このディスパッチャーの処理を終了します。
	 */
	def shutdown():Unit = {
		this.interrupt()
	}

	/**
	 * 指定された処理をディスパッチャースレッド内で実行します。このメソッドは処理が終了
	 * するまで待ちません。
	 */
	private[node] def post(task:()=>Unit):Unit = {
		queue.synchronized {
			queue.append(task)
		}
		selector.wakeup()
	}

	/**
	 * 指定された処理をディスパッチャースレッド内で実行します。このメソッドは処理が終了
	 * するまでブロックし結果を返します。処理順序については保障されません。
	 */
	private[node] def postAndWait[T](task:()=>T):T = {
		if(currentDispatcher.get().eq(this)){
			task()
		} else {
			val lock = new Object()
			var result:Option[T] = None
			lock.synchronized {
				post(() => {
					result = Some(task())
					lock.synchronized{ lock.notify() }
				})
				lock.wait()
			}
			result.get
		}
	}

	/**
	 * キュー内のすべての処理を実行します。
	 */
	private[this] def execQueue():Unit = queue.synchronized {
		if(queue.size > 0){
			queue.foreach { f =>
				try {
					f()
				} catch {
					case ex:Exception =>
						logger.error("fail to in-dispatcher operation", ex)
				}
			}
			queue.clear()
		}
	}

}

object Dispatcher {
	private[Dispatcher] val logger = org.apache.log4j.Logger.getLogger(classOf[Dispatcher])

	/**
	 * ディスパッチャースレッド内であることを判定するための ThreadLocal。
	 */
	private[Dispatcher] val currentDispatcher = new ThreadLocal[Dispatcher]()
}
