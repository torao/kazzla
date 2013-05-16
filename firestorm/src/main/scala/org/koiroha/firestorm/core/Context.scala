/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
 */

package org.koiroha.firestorm.core

import java.net.{ServerSocket, Socket}
import java.nio.ByteBuffer
import java.nio.channels._
import java.util.concurrent.ArrayBlockingQueue
import org.koiroha.firestorm.jmx.ContextMXBeanImpl
import scala.collection.JavaConversions._

/**
 * `Context` は Selector を共有する Channel をプールし非同期 Socket I/O を行うためのクラスです。内部に
 * I/O ディスパッチスレッドと Selector を持ち入出力可能になった Channel に対する I/O 処理を行います。
 *
 * コンテキストは処理を行う Channel が存在しない状態で一定時間が経過すると、新たな Channel が生成されるまで
 * 内部のスレッドを停止します。
 *
 * @param id このコンテキストの ID (JMX 名に使用)
 * @param readBufferSize 共用の読み込みバッファサイズ
 * @param maxIdleInMillis 処理する Channel がなくなってからディスパッチスレッドを終了するまでの時間 (ミリ秒)
 */
class Context(val id:String, readBufferSize:Int = 8 * 1024, var maxIdleInMillis:Long = Long.MaxValue){
	EventLog.info("[%s] startup context".format(id))

	/**
	 * select() から各 Channel の入出力処理を行うスレッドです。
	 */
	private[this] var dispatcher:Option[Thread] = None

	/**
	 * ディスパッチスレッド内で行う処理のキューです。
	 */
	private[this] val queue = new ArrayBlockingQueue[()=>Unit](10)

	/**
	 * このコンテキストが使用する `Selector`。コンテキストスレッドが停止しても shutdown が行われるまで
	 * クローズは行われません。
	 */
	private[this] val selector = Selector.open()

	/**
	 * すべての Socket で共有する内部的な読み込みバッファ。ディスパッチスレッド内でのみ使用するためスレッド間で
	 * 共有されません。
	 */
	private[this] val readBuffer = ByteBuffer.allocateDirect(readBufferSize)

	/**
	 * このコンテキストを監視しているリスナ。
	 */
	private[this] var listeners = List[Context.Listener]()

	/**
	 * このコンテキストを JMX で監視する MXBean。
	 */
	private[this] val mxbean = new ContextMXBeanImpl(this)

	/**
	 * 接続済みの Socket チャネルをこのコンテキストにバインドします。
	 * @param channel バインドするチャネル
	 * @return 接続したエンドポイント
	 */
	private[firestorm] def bind(channel:SocketChannel, endpoint:Endpoint) = {
		execInDispatcherThread{ () => openSelectionKey(channel, endpoint) }
	}

	/**
	 * 接続済みの ServerSocket チャネルをこのコンテキストにバインドします。
	 * @param channel バインドするチャネル
	 */
	private[firestorm] def bind(channel:ServerSocketChannel, server:Server):SelectionKey = {
		execInDispatcherThread { () =>
			channel.configureBlocking(false)
			channel.register(selector, SelectionKey.OP_ACCEPT, server)
		}
	}

	/**
	 * 指定されたリスナを追加します。
	 * @param l 追加するリスナ
	 */
	def +=(l:Context.Listener):Unit = listeners ::= l

	/**
	 * 指定されたリスナを削除します。
	 * @param l 削除するリスナ
	 */
	def -=(l:Context.Listener):Unit = listeners = listeners.filter{ _ != l }

	/**
	 * すべてのリスナに対して処理 `f` を実行します。
	 * @param f 実行する処理
	 */
	private[core] def eachListener(f:(Context.Listener)=>Unit) = listeners.foreach { f }

	/**
	 * このコンテキストで動作しているすべての Channel をクローズし処理を終了します。
	 *
	 * @param gracefulCloseInMillis gracefully close in milliseconds
	 */
	def shutdown(gracefulCloseInMillis:Long):Unit = {

		// JavaVM 上にデーモンスレッドしかない場合 shutdown でディスパッチスレッドが停止すると同時に JavaVM が
		// 終了するため警告
		if(Thread.currentThread().isDaemon){
			EventLog.warn("shutdown is processing in daemon thread; note that jvm will exit immediately" +
				" in case it becomes only daemon threads by the end of this server")
		}

		// 終了処理をディスパッチスレッド内で実行
		execInDispatcherThread { () =>

			// ServerSocket をクローズして新しい接続の受け付けを停止
			selector.keys().filter{ _.channel().isInstanceOf[ServerSocketChannel] }.foreach { key =>
				key.cancel()
				EventLog.debug("closing server channel %s on port %d".format(
					key.socket().getInetAddress.getHostAddress, key.socket().getLocalPort))
				key.channel().close()
			}

			// すべての Endpoint に対して shutdown 要求を実行
			selector.keys().filter{
				_.attachment().isInstanceOf[Endpoint]
			}.map{
				_.attachment().asInstanceOf[Endpoint]
			}.foreach { e =>
				e.onShutdown()
				e.close()
				eachListener { _.onClosing(e) }
			}

			// すべての SelectionKey が取り除かれるまで待機
			val start = System.nanoTime()
			while((System.nanoTime() - start) / 1000 / 1000 < gracefulCloseInMillis && selector.keys().size() > 0){
				Thread.sleep(300)
			}

			// 残っているすべてのチャネルを強制的にクローズ
			selector.keys().foreach { key =>
				EventLog.warn("unclosed selection-key remaining: %s".format(key))
				closeSelectionKey(key)
			}

			// ディスパッチスレッドに割り込みを実行しスレッドを停止する
			Thread.currentThread().interrupt()
			selector.wakeup()
		}

		// Selector をクローズ
		selector.close()

		// すべてのリスナに shutdown を通知
		eachListener { _.onShutdown() }
	}

	/**
	 * 指定された処理を I/O ディスパッチスレッド内で非同期に実行します。
	 * @param job ディスパッチスレッド内で行う処理
	 */
	private[this] def asyncExecInDispatcherThread(job:()=>Unit):Unit = queue.synchronized {

		// Selector がクローズ状態ならすでに shutdown 済み
		if(! selector.isOpen){
			throw new IllegalStateException("context already shutdown")
		}

		// I/O ディスパッチスレッドが開始していなければ開始する
		if(! dispatcher.isDefined || ! dispatcher.get.isAlive){
			val thread = new Thread(){
				override def run():Unit = dispatch()
			}
			thread.setName("Firestorm I/O Dispatcher [%s]".format(id))
			thread.setDaemon(false)
			thread.start()
			dispatcher = Some(thread)
		}

		// キューに処理を投入
		queue.put(job)

		// select() で停止している処理を起動
		selector.wakeup()
	}

	/**
	 * 指定された処理を I/O ディスパッチスレッド内で同期実行し結果を返します。
	 *
	 * @param job ディスパッチスレッド内で行う処理
	 * @return 処理結果
	 */
	private[this] def execInDispatcherThread[T](job:()=>T):T = {
		val signal = new Object()
		var result = List[T]()
		signal.synchronized {
			asyncExecInDispatcherThread { () =>
				signal.synchronized {
					result ::= job()
					signal.notify()
				}
			}
			signal.wait()
		}
		result(0)
	}

	/**
	 * I/O ディスパッチスレッドの処理を実行します。
	 */
	private[this] def dispatch():Unit = try {

		// loop while reject ServerSocketChannel from selector
		var idleStartTime:Option[Long] = None
		while (! Thread.currentThread().isInterrupted) {

			// キューに投入されている処理を実行
			while(! queue.isEmpty){
				queue.take()()
			}

			// 入出力可能になった Socket の処理を実行
			if(selector.select(1000) > 0){
				val selectedKeys = selector.selectedKeys()
				// ※ループ内の remove() により ConcurrentModificationException が発生するため toList している
				selectedKeys.toList.foreach {
					key =>
						selectedKeys.remove(key)
						selected(key)
				}
				idleStartTime = None
			} else if (selector.keys().isEmpty){
				// Selector の処理する SelectionKey がなくなって一定時間経過したら Selector や Channel をそのままで
				// スレッドのみを終了
				idleStartTime match {
					case Some(t) =>
						if((System.nanoTime() - t) / 1000 / 1000 > maxIdleInMillis){
							Thread.currentThread().interrupt()
						}
					case None =>
						idleStartTime = Some(System.nanoTime())
				}
			} else {
				idleStartTime = None
			}
		}
	} catch {
		case ex:Throwable =>
			if (ex.isInstanceOf[ThreadDeath]){
				throw ex
			}
			EventLog.fatal(ex, "uncaught exception detected in context thread %s".format(id))
	} finally {
		queue.synchronized {

			// スレッド終了
			assert(dispatcher.get == Thread.currentThread())
			dispatcher = None

			// キューに処理が入っている状態で終了処理を行っている場合は処理を投入して次の処理を投入しスレッドを開始する
			if(! queue.isEmpty){
				asyncExecInDispatcherThread{ () => }
			}
		}
	}

	/**
	 * 単一の SelectionKey を処理します。
	 * @param key 入出力可能になった SelectionKey
	 */
	private[this] def selected(key:SelectionKey):Unit = key.attachment() match {
		case e:Endpoint =>
			// *** catch exception and close individual connection only read or write
			try {

				if (key.isReadable) {
					// read from channel
					if(! e.internalRead(readBuffer)){
						EventLog.debug("connection reset by peer: %s".format(e))
						closeSelectionKey(key)
					}
				}
				if (key.isWritable) {
					// write to channel
					e.internalWrite()
				}

			} catch {
				case ex:Throwable =>
					// クライアントソケットの例外は致命的ではないためクローズして終了
					if (ex.isInstanceOf[ThreadDeath]){
						throw ex
					}
					eachListener { _.onError(e, ex) }
					closeSelectionKey(key)
			}

		case s:Server =>
			// setSelectionKey new connection (server behaviour)
			if (key.isAcceptable) {
				val server = key.channel().asInstanceOf[ServerSocketChannel]
				val client = server.accept()
				eachListener { _.onAccept(client) }
				val endpoint = Endpoint(this)
				openSelectionKey(client, endpoint)
				key.onAccept(endpoint)

				// 初期状態で書き込みバッファは空だが初回に onDepartureBufferedOut を呼び出すために OP_WRITE を指定
				endpoint.out.andThen{ None }
			}
	}

	private[this] implicit def sk2endpoint(key:SelectionKey):Endpoint = key.attachment().asInstanceOf[Endpoint]
	private[this] implicit def sk2server(key:SelectionKey):Server = key.attachment().asInstanceOf[Server]
	private[this] implicit def sk2socket(key:SelectionKey):Socket = key.channel().asInstanceOf[SocketChannel].socket()
	private[this] implicit def sk2ssocket(key:SelectionKey):ServerSocket = {
		key.channel().asInstanceOf[ServerSocketChannel].socket()
	}
	private[this] implicit def sk2schannel(key:SelectionKey):ServerSocketChannel = {
		key.channel.asInstanceOf[ServerSocketChannel]
	}

	/**
	 * Join new client channel to this context.
	 * @param channel new channel
	 */
	private[this] def openSelectionKey(channel:SocketChannel, endpoint:Endpoint):Unit = {
		channel.configureBlocking(false)
		val selectionKey = channel.register(selector, SelectionKey.OP_READ, endpoint)
		endpoint.setSelectionKey(selectionKey)
		listeners.foreach { _.onOpen(endpoint) }

		//
		endpoint.out.andThen { None }
	}

	/**
	 * Detach specified selection key and close channel. This method is for client channel only.
	 * @param key selection key to close
	 */
	private[this] def closeSelectionKey(key:SelectionKey){
		key.cancel()
		key.channel().close()
		if(key.attachment().isInstanceOf[Endpoint]){
			listeners.foreach { _.onClosing(key.attachment().asInstanceOf[Endpoint]) }
		}
	}

	// ログ出力のためのリスナを追加
	this += new Context.Listener {
		override def onShutdown():Unit = EventLog.info("[%s] shutdown context".format(id))
		override def onError(ex:Throwable):Unit = EventLog.fatal(ex, "")
		override def onError(endpoint:Endpoint, ex:Throwable):Unit
			= EventLog.error(ex, "[%s] uncaught exception in endpoint %s".format(id, endpoint))

		override def onListen(server:Server):Unit = {
			EventLog.debug("[%s] listenining on %s port %d".format(id,
				server.address.get.getAddress.getHostAddress,
				server.address.get.getPort))
		}
		override def onAccept(channel:SocketChannel):Unit = {
			val socket = channel.socket()
			EventLog.debug("[%s] accepts new connection from %s on port %d".format(
				id, socket.getInetAddress.getHostAddress, socket.getPort))
		}
		override def onOpen(endpoint:Endpoint):Unit = { }
		override def onClosing(endpoint:Endpoint):Unit = { }
		override def onClosed(endpoint:Endpoint):Unit = { }
		override def onRead(endpoint:Endpoint, length:Int):Unit = { }
		override def onWrite(endpoint:Endpoint, length:Int):Unit = { }

	}

}

object Context {

	/**
	 * コンテキストに対する状態通知を受け取るためのリスナです。
	 * @see Context
	 */
	trait Listener {

		/**
		 * コンテキストが shutdown した時に呼び出されます。
		 */
		def onShutdown():Unit = { }

		/**
		 * コンテキスト内でエラーが発生したときに呼び出されます。
		 * @param ex 発生したエラー
		 */
		def onError(ex:Throwable):Unit = { }

		/**
		 * 指定されたエンドポイントの処理でエラーが発生したときに呼び出されます。
		 * @param endpoint 例外の発生したエンドポイント
		 * @param ex 発生した例外
		 */
		def onError(endpoint:Endpoint, ex:Throwable):Unit = { }

		/**
		 * このコンテキスト内の ServerSocketChannel が新しい接続を受け付けたときに呼び出されます。
		 * @param channel 接続した channel
		 */
		def onAccept(channel:SocketChannel):Unit = { }

		/**
		 * このコンテキストでサーバが Listen を開始したときに呼び出されます。
		 * @param server Listen を開始したサーバ
		 */
		def onListen(server:Server):Unit = { }

		/**
		 * このコンテキスト上のサーバが Listen を終了したときに呼び出されます。
		 * @param server Listen を終了したサーバ
		 */
		def onUnlisten(server:Server):Unit = { }

		/**
		 * このコンテキストに新しいエンドポイントが参加した時に呼び出されます。
		 * @param endpoint 新しく参加したエンドポイント
		 */
		def onOpen(endpoint:Endpoint):Unit = { }

		/**
		 * 指定されたエンドポイントがクローズされようとしている時に呼び出されます。
		 * @param endpoint クローズ使用としているエンドポイント
		 */
		def onClosing(endpoint:Endpoint):Unit = { }

		/**
		 * 指定されたエンドポイントがクローズされたときに呼び出されます。
		 * @param endpoint クローズされたエンドポイント
		 */
		def onClosed(endpoint:Endpoint):Unit = { }

		/**
		 * 指定されたエンドポイント上でデータが読み出された時に呼び出されます。
		 * @param endpoint データの読み出されたエンドポイント
		 * @param length 読み出されたデータのバイト長
		 */
		def onRead(endpoint:Endpoint, length:Int):Unit = { }

		/**
		 * 指定されたエンドポイント上でデータが書き込まれたときに呼び出されます。
		 * @param endpoint データの書き出されたエンドポイント
		 * @param length 書き出されたデータのバイト長
		 */
		def onWrite(endpoint:Endpoint, length:Int):Unit = { }

	}
}