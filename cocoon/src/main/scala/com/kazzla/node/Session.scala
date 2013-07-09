/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node

import java.nio._
import java.nio.channels._
import scala.collection._
import scala.annotation._
import scala._

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Session
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Session private[node](dispatcher:Dispatcher, key:SelectionKey) extends AutoCloseable {
	import Session._

	// ========================================================================
	// 入力
	// ========================================================================
	/**
	 * このセッションに対応するチャネルが入力可能な場合に有効な入力機能です。
	 * {{{
	 *   session.in.get.onRead { buffer => ... }
	 * }}}
	 */
	val channel = key.channel()

	// ========================================================================
	// 入力
	// ========================================================================
	/**
	 * このセッションに対応するチャネルが入力可能な場合に有効な入力機能です。
	 * {{{
	 *   session.in.get.onRead { buffer => ... }
	 * }}}
	 */
	val in:Option[In] = if((key.channel().validOps() & SelectionKey.OP_READ) != 0){ Some(new In()) } else { None }

	// ========================================================================
	// 出力
	// ========================================================================
	/**
	 * このセッションに対応するチャネルが出力可能な場合に有効な出力機能です。
	 */
	val out:Option[Out] = if((key.channel().validOps() & SelectionKey.OP_WRITE) != 0){ Some(new Out()) } else { None }

	// ========================================================================
	// サーバ
	// ========================================================================
	/**
	 * このセッションに対応するチャネルが Accept 可能な場合に有効なサーバ機能です。
	 */
	val server:Option[Server] = if((key.channel().validOps() & SelectionKey.OP_ACCEPT) != 0){ Some(new Server()) } else { None }

	// ========================================================================
	// セッションのクローズ
	// ========================================================================
	/**
	 * このセッションをクローズします。この呼び出しによりセッションに対応するチャネルは
	 * クローズされます。
	 */
	def close():Unit = try {
		dispatcher.postAndWait(() => {
			key.cancel()
		})
		key.channel().close()
	} catch {
		case ex:Exception =>
			logger.error("fail to close session", ex)
	}

	class In private[this]() {

		/**
		 * 読み出し可能になった時に行う処理。
		 */
		private[this] var read:Option[(ByteBuffer)=>Unit] = None

		/**
		 * 読み出し用に蓄積されているバッファ。
		 */
		private[this] var readBuffer:ByteBuffer = ByteBuffer.allocate(8 * 1024)

		// ======================================================================
		// 読み出し処理の設定
		// ======================================================================
		/**
		 * チャネルからデータが読みされたときに呼び出される処理を指定します。
		 */
		def onRead(f:(ByteBuffer)=>Unit):In = {
			read = Some(f)
			key.interestOps(key.interestOps() | SelectionKey.OP_READ)
			this
		}

		/**
		 * チャネルからデータが読み出されたときに呼び出されます。内部バッファに読み込み済みの
		 * データを連結し処理を呼び出します。
		 * @param buffer
		 */
		private[node] def onRead(buffer:ByteBuffer):Unit = read match {
			case Some(r) =>
				val len = buffer.remaining() + readBuffer.remaining()
				if(len > readBuffer.capacity()){
					val temp = ByteBuffer.allocate((len + 1.2).toInt)
					temp.put(readBuffer).put(buffer).flip()
					readBuffer = temp
				} else if(readBuffer.capacity() - readBuffer.limit() >= buffer.remaining()){
					val pos = readBuffer.position()
					val lim = readBuffer.limit()
					readBuffer.limit(lim + buffer.remaining())
					readBuffer.position(lim)
					readBuffer.put(buffer)
					readBuffer.position(pos)
				} else {
					val sub = readBuffer.slice()
					readBuffer.clear()
					readBuffer.put(sub)
					readBuffer.put(buffer)
					readBuffer.flip()
				}
				r(readBuffer)
			case None => None
		}
	}

	class Out private[this](){

		/**
		 * チャネルが書き込み可能になったときに実行する処理のキュー。処理が false を返した場合
		 * は処理がキューから取り除かれる。
		 */
		private[this] val queue:mutable.Buffer[(WritableByteChannel)=>Boolean] = mutable.Buffer()

		/**
		 * 書き込み待ち状態にあるデータのサイズ。
		 */
		@volatile
		private[this] var _writePending = 0

		// ======================================================================
		// 出力待機データサイズ
		// ======================================================================
		/**
		 * このセッションで使用しているチャネルに対して出力を待機しているデータサイズをバイト
		 * 単位で参照します。
		 */
		def remaining:Int = _writePending

		// ======================================================================
		// データの出力
		// ======================================================================
		/**
		 * このセッションで使用しているチャネルに対して出力用のデータを追加します。指定された
		 * バッファはチャネルが書き込み可能な状態になると非同期で出力が行われます。バッファは
		 * 内部でコピーされないため、書き込みが完了する前にバッファ内のデータを書き換えると
		 * 副作用が発生します。
		 * @param buffer 非同期で出力するデータのバッファ
		 */
		def write(buffer:ByteBuffer):Out = {
			if(buffer.remaining() > 0){
				_writePending += buffer.remaining()
				post { channel =>
					val len = channel.write(buffer)
					_writePending -= len
					buffer.remaining() == 0
				}
			}
			this
		}

		// ======================================================================
		// データ出力の同期
		// ======================================================================
		/**
		 * このセッションで使用しているチャネルに対して出力用のデータがすべて出力されるまで
		 * 待機します。
		 */
		def sync():Out = {
			val signal = new Object()
			signal.synchronized{
				post { _ =>
					signal.synchronized { signal.notify() }
					true
				}
				signal.wait()
			}
			this
		}

		/**
		 * このセッションが使用しているチャネルが書き込み可能になったときに呼び出される処理。
		 * 書き込みキューに投入されている処理を可能なだけ実行する。
		 */
		private[node] def onWritable(channel:WritableByteChannel):Unit = queue.synchronized {

			// 書き込みキューに投入されているすべての処理を実行
			@tailrec
			def execWriteQueue(channel:WritableByteChannel):Unit = {
				if(! queue.isEmpty && queue(0)(channel)){
					queue.remove(0)
					execWriteQueue(channel)
				}
			}
			execWriteQueue(channel)

			// 書き込みキューが空になったら書き込み可能になっても通知を受けない
			if(queue.isEmpty){
				key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE)
				_writePending = 0
			}
		}

		/**
		 * 書き込みキューに指定された処理を投入する。
		 */
		private[this] def post(f:(WritableByteChannel)=>Boolean) = queue.synchronized {
			queue.append(f)

			// 書き込み可能通知を受けない状態なら通知を行うように設定
			if((key.interestOps() & SelectionKey.OP_WRITE) == 0){
				key.interestOps(key.interestOps() | SelectionKey.OP_WRITE)
			}
		}
	}

	class Server private[this](){
		private[this] var accept:Option[(SocketChannel) => Unit] = None

		// ======================================================================
		// Accept 処理の設定
		// ======================================================================
		/**
		 * Accept 処理を設定します。このメソッドの呼び出しによってチャネルが Accept 通知を
		 * 始めるため、メソッド呼び出し前の Accept も処理することが出来ます。
		 */
		def onAccept(f:(SocketChannel)=>Unit):Server = {
			this.accept = Some(f)
			key.interestOps(key.interestOps() | SelectionKey.OP_ACCEPT)
			this
		}

		/**
		 * チャネルが新しい接続を受け付けたときに呼び出されます。
		 * @param socket
		 */
		private[node] def onAccept(socket:SocketChannel):Unit = {
			accept.foreach{ f => f(socket) }
		}
	}

}

object Session {
	private[Session] val logger = org.apache.log4j.Logger.getLogger(classOf[Session])
}
