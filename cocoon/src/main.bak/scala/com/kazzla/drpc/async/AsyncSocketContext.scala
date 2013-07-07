/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import collection.JavaConversions._
import java.nio.ByteBuffer
import org.apache.log4j.Logger
import collection.mutable.Queue
import java.nio.channels.{SelectionKey, Selector}
import java.io.{Closeable, IOException}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// AsyncSocketContext: 非同期ソケットコンテキスト
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * SocketChannel を使用した非同期 I/O のためのクラスです。複数の非同期ソケットの通信処
 * 理を行うワーカースレッドプールを保持しています。
 * @author Takami Torao
 */
class AsyncSocketContext extends Closeable with AutoCloseable{
	import AsyncSocketContext.logger

	// ========================================================================
	// スレッドグループ
	// ========================================================================
	/**
	 * スレッドを所属させるスレッドグループです。
	 */
	private[async] val threadGroup = new ThreadGroup("AsyncSocketContext")

	// ========================================================================
	// 1スレッドあたりのソケット数
	// ========================================================================
	/**
	 * 1スレッドが担当する非同期ソケット数の上限です。スレッドの担当するソケット数がこの数
	 * を超えると新しいスレッドが生成されます。
	 */
	var maxSocketsPerThread = 512

	// ========================================================================
	// 読み込みバッファサイズ
	// ========================================================================
	/**
	 * 1スレッド内で使用する読み込みバッファサイズです。
	 */
	var readBufferSize = 4 * 1024

	// ========================================================================
	// ワーカースレッド
	// ========================================================================
	/**
	 * 実行中のワーカースレッドです。
	 */
	private[this] var workers = List[Dispatcher]()

	// ========================================================================
	// 起動中のワーカースレッド数
	// ========================================================================
	/**
	 * 実行中のワーカースレッド数を参照します。
	 */
	def activeThreads:Int = workers.foldLeft(0){ (n,w) => n + (if(w.isAlive) 1 else 0) }

	// ========================================================================
	// 非同期ソケット数
	// ========================================================================
	/**
	 * 接続中の非同期ソケット数を参照します。
	 */
	def activeAsyncSockets:Int = workers.foldLeft(0){ _ + _.activeSockets }

	// ========================================================================
	// 非同期ソケットの追加
	// ========================================================================
	/**
	 * このコンテキストに新しい非同期ソケットを参加します。
	 * @param socket コンテキストに参加する非同期ソケット
	 */
	def join(socket:AsyncSocket):Unit = synchronized{
		if(workers.find{ _.join(socket) }.isEmpty){
			// 既に停止しているスレッドを除去
			workers = workers.filter{ _.isAlive }
			// 新しいスレッドを作成
			logger.debug("creating new worker thread: " + activeAsyncSockets + " + 1 sockets")
			val worker = new Dispatcher()
			workers ::= worker
			worker.start()
			val success = worker.join(socket)
			if(! success){
				throw new IllegalStateException()
			}
		}
	}

	// ========================================================================
	// 非同期ソケットの切り離し
	// ========================================================================
	/**
	 * このコンテキストから指定された非同期ソケットを切り離します。切り離しを行ったソケット
	 * は送受信処理が行えなくなっているだけでクローズされていません。切り離したソケットを
	 * 別のコンテキストで動作させることが可能です。
	 * @param socket コンテキストから切り離す非同期ソケット
	 * @return 指定された非同期ソケットが切り離された場合 true
	 */
	def leave(socket:AsyncSocket):Boolean = synchronized{
		workers.find{ worker => worker.leave(socket) } match {
			case Some(_) => true
			case None => false
		}
	}

	// ========================================================================
	// 非同期ソケットのクローズ
	// ========================================================================
	/**
	 * このコンテキストの入出力スレッドを停止しすべての非同期ソケットをクローズします。
	 */
	override def close():Unit = synchronized {
		workers.foreach{ worker =>
			worker.closeAll()
			worker.interrupt()
		}
	}

	// TODO reboot and takeover thread that works specified times

	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Dispatcher: ディスパッチャースレッド
	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * 複数の非同期ソケットの受送信処理を担当するスレッドです。
	 * @author Takami Torao
	 */
	private[async] class Dispatcher extends Thread(threadGroup, "AsyncDispatcher") {

		// ======================================================================
		// セレクター
		// ======================================================================
		/**
		 * このインスタンスが使用するセレクターです。
		 */
		private[this] val selector = Selector.open()

		// ======================================================================
		// 接続キュー
		// ======================================================================
		/**
		 * このディスパッチャーに新しい接続先を追加するためのキューです。
		 */
		private[this] val joinQueue = new Queue[AsyncSocket]()

		// ======================================================================
		// イベントループ中フラグ
		// ======================================================================
		/**
		 * このディスパッチャーがイベントループ中かどうかを表すフラグです。
		 */
		private[this] val inEventLoop = new java.util.concurrent.atomic.AtomicBoolean(true)

		// ======================================================================
		// 非同期ソケット数の参照
		// ======================================================================
		/**
		 * このディスパッチャーが担当している非同期ソケット数を参照します。
		 */
		def activeSockets:Int = selector.keys().size()

		// ======================================================================
		// 非同期ソケットの追加
		// ======================================================================
		/**
		 * このスレッドが担当する非同期ソケットを追加します。このスレッドに追加できなかった場合
		 * は false を返します。
		 * @param socket 追加する非同期ソケット
		 * @return 追加できなかった場合 false
		 */
		def join(socket:AsyncSocket):Boolean = {
			// イベントループが開始していない場合や担当ソケットがいっぱいの場合は false を返す
			if(! inEventLoop.get() || ! isAlive || activeSockets >= maxSocketsPerThread){
				return false
			}
			// スレッドのイベントループ内で追加しないと例外が発生する
			joinQueue.synchronized{
				joinQueue.enqueue(socket)
			}
			selector.wakeup()
			true
		}

		// ======================================================================
		// 非同期ソケットの切り離し
		// ======================================================================
		/**
		 * このスレッドが担当している非同期ソケットから指定されたものを除去します。
		 * 該当するソケットが存在しない場合は false を返します。
		 * @param socket 除去する非同期ソケット
		 * @return 非同期ソケットを除去した場合 true
		 */
		def leave(socket:AsyncSocket):Boolean = {
			selector.keys.foreach{ key =>
				val peer = key.attachment().asInstanceOf[AsyncSocket]
				if(peer.eq(socket)){
					socket.bind(null)
					return true
				}
			}
			false
		}

		// ======================================================================
		// 非同期ソケットの全クローズ
		// ======================================================================
		/**
		 * このワーカーが管理している非同期ソケットを全てクローズします。
		 */
		def closeAll():Unit = {
			selector.keys().foreach{ key =>
				val socket = key.attachment().asInstanceOf[AsyncSocket]
				try {
					socket.close()
				} catch {
					case ex:Exception => logger.error("fail to close async socket: " + socket, ex)
				}
			}
		}

		// ======================================================================
		// スレッドの実行
		// ======================================================================
		/**
		 * データの受送信が可能になった非同期ソケットに対するイベントループを実行します。
		 */
		override def run():Unit = {
			inEventLoop.set(true)
			logger.debug("start async I/O dispatcher thread")

			// データ受信用バッファを作成 (全非同期ソケット共用)
			val readBuffer = ByteBuffer.allocate(readBufferSize)

			while({ select(); inEventLoop.get() }){

				val keys = selector.selectedKeys()
				val it = keys.iterator()
				while(it.hasNext){

					// 送受信可能になった非同期ソケットを参照
					val key = it.next()
					it.remove()
					val socket = key.attachment().asInstanceOf[AsyncSocket]
					if(logger.isTraceEnabled){
						logger.trace("selection state: %s -> %s".format(socket, AsyncSocketContext.sk2s(key)))
					}

					if(key.isReadable){
						// データ受信処理の実行
						try {
							socket.channelRead(readBuffer)
						} catch {
							case ex:Exception =>
								logger.error("uncaught exception in read operation, closing connection", ex)
								socket.close()
						}
					} else if (key.isWritable){
						// データ送信処理の実行
						try {
							socket.channelWrite()
						} catch {
							case ex:Exception =>
								logger.error("uncaught exception in write operation, closing connection", ex)
								socket.close()
						}
					} else {
						logger.warn("unexpected selection key state: 0x%X".format(key.readyOps()))
					}
				}
			}

			// このスレッドをリストから除去
			AsyncSocketContext.this.synchronized {
				workers = workers.filter{ _.ne(this) }
			}

			// スレッドが終了する前に全ての処理中の非同期ソケットを別のスレッドに割り当て
			selector.keys().foreach{ key =>
				val socket = key.attachment().asInstanceOf[AsyncSocket]
				socket.bind(null)
				AsyncSocketContext.this.join(socket)
			}

			logger.debug("exit async I/O worker thread")
		}

		// ======================================================================
		// 送受信可能チャネルの待機
		// ======================================================================
		/**
		 * このスレッドが管理している非同期ソケットのいずれかが送受信可能になるまで待機します。
		 * イベントループ終了を検知した場合は inEventLoop を false に設定し終了します。
		 */
		private def select():Unit = {

			// チャネルが送受信可能になるまで待機
			try {
				selector.select()
				if(logger.isTraceEnabled){
					logger.trace("async I/O dispatcher thread awake")
				}
			} catch {
				case ex:IOException =>
					logger.fatal("select operatin failure: " + select, ex)
					inEventLoop.set(false)
					return
			}

			// スレッドが割り込まれていたら終了
			if(Thread.currentThread().isInterrupted){
				logger.debug("async I/O dispatcher thread interruption detected")
				inEventLoop.set(false)
				return
			}

			// このスレッドへ参加する非同期ソケットを取り込み
			joinQueue.synchronized {
				while(! joinQueue.isEmpty){
					val socket = joinQueue.dequeue()
					try {
						val key = socket.bind(Some(selector)).get
						key.attach(socket)
						logger.debug("join new async socket in dispatcher thread")
					} catch {
						case ex:IOException =>
							logger.error("register operation failed, ignore and close socket: " + socket, ex)
							socket.close()
					}
				}
			}
		}

	}

}

object AsyncSocketContext {
	val logger = Logger.getLogger(AsyncSocketContext.getClass)

	def sk2s(key:SelectionKey):String = {
		val opt = key.readyOps()
		Seq(
			if((opt & SelectionKey.OP_READ) != 0) "READ" else null,
			if((opt & SelectionKey.OP_WRITE) != 0) "WRITE" else null,
			if((opt & SelectionKey.OP_ACCEPT) != 0) "ACCEPT" else null,
			if((opt & SelectionKey.OP_CONNECT) != 0) "CONNECT" else null
		).filter{ _ != null }.mkString("|")
	}
}
