/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import java.nio.channels.Selector
import collection.mutable.{ArrayBuffer, Buffer}
import collection.JavaConversions._
import java.nio.ByteBuffer
import java.io.IOException

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// AsyncSocketContext: 非同期ソケットコンテキスト
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * SocketChannel を使用した非同期 I/O のためのクラスです。複数の非同期ソケットの通信処
 * 理を行うワーカースレッドプールを保持しています。
 * @author Takami Torao
 */
class AsyncSocketContext {

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
	private[this] var workers = List[Worker]()

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
		workers.find{
			_.join(socket)
		} match {
			case Some(worker) => None
			case None =>
				// 既に停止しているスレッドを除去
				workers = workers.filter{ _.isAlive }
				// 新しいスレッドを作成
				logger.debug("creating new worker thread: " + activeAsyncSockets + " + 1 sockets")
				val worker = new Worker()
				workers ::= worker
				worker.start()
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

	// TODO reboot and takeover thread that works specified times

	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Worker: ワーカースレッド
	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * 複数の非同期ソケットの受送信処理を担当するスレッドです。
	 * @author Takami Torao
	 */
	private[AsyncSocketContext] class Worker extends Thread(threadGroup, "AsyncWorker") {

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
		 * このワーカーに新しい接続先を追加するためのキューです。
		 */
		private[this] val queue:Buffer[AsyncSocket] = new ArrayBuffer[AsyncSocket]()

		// ======================================================================
		// イベントループ中フラグ
		// ======================================================================
		/**
		 * このスレッドがイベントループ中かどうかを表すフラグです。
		 */
		private[this] val inEventLoop = new java.util.concurrent.atomic.AtomicBoolean(false)

		// ======================================================================
		// 非同期ソケット数の参照
		// ======================================================================
		/**
		 * このワーカーが担当している非同期ソケット数を参照します。
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
			queue.synchronized{
				queue.append(socket)
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
				if(peer == socket){
					socket.leave()
					return true
				}
			}
			false
		}

		// ======================================================================
		// スレッドの実行
		// ======================================================================
		/**
		 * データの受送信が可能になった非同期ソケットに対するイベントループを実行します。
		 */
		override def run():Unit = {
			inEventLoop.set(true)

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

					// データ受信処理の実行
					if(key.isReadable){
						try {
							socket.channelRead(readBuffer)
						} catch {
							case ex:Exception =>
								logger.error("uncaught exception in read operation, closing connection", ex)
								socket.close()
						}
					}

					// データ送信処理の実行
					if (key.isWritable){
						try {
							socket.channelWrite()
						} catch {
							case ex:Exception =>
								logger.error("uncaught exception in write operation, closing connection", ex)
								socket.close()
						}
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
				socket.leave()
				join(socket)
			}
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
			} catch {
				case ex:IOException =>
					logger.fatal("select operatin failure: " + select, ex)
					inEventLoop.set(false)
					return
			}

			// スレッドが割り込まれていたら終了
			if(Thread.currentThread().isInterrupted){
				inEventLoop.set(false)
				return
			}

			// このスレッドへ参加する非同期ソケットを取り込み
			queue.synchronized {
				while(! queue.isEmpty){
					val endpoint = queue.remove(0)
					try {
						val key = endpoint.join(selector)
						key.attach(endpoint)
					} catch {
						case ex:IOException =>
							logger.error("register operation failed, ignore and close socket: " + endpoint, ex)
							endpoint.close()
					}
				}
			}
		}

	}

}
