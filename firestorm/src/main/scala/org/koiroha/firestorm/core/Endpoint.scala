/*
 * Copyright (c) 2012 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * org.koiroha.firestorm.http://www.apache.org/licenses/LICENSE-2.0.html
 */

package org.koiroha.firestorm.core

import java.io.IOException
import java.net._
import java.nio.ByteBuffer
import java.nio.channels.{SocketChannel, SelectionKey}
import java.util.concurrent.LinkedBlockingQueue

/**
 * 非同期入出力のエンドポイントを表すクラスです。
 *
 * {{{
 *   val context = new Context()
 *   val e1 = Endpoint(context)
 *    .onArrivalBufferedIn { e => ... }
 *    .onDepartureBufferedOut { e => ... }
 *    .connect("www.foo.com", 80)
 * }}}
 * コンストラクタでこの Endpoint が使用する `Context` と結びつけ、各種のイベントハンドラを定義し最後に接続を
 * 行います (イベントハンドラは任意のタイミングで追加削除することもできます)。
 *
 * エンドポイントに関連付けた変数が必要な場合はローカル変数をイベントハンドラで束縛します。
 */
case class Endpoint(dispatcher:Context){

	/**
	 * この Endpoint の入力ストリームバッファです。
	 */
	def in:ReadableStreamBuffer = _in
	private[this] val _in = new InternalReadableStreamBuffer()

	/**
	 * この Endpoint の出力ストリームバッファです。
	 */
	def out:WritableStreamBuffer = _out
	private[this] val _out = new InternalWritableStreamBuffer()

	/**
	 * このエンドポイントの SelectionKey です。まだ接続していない場合は None をとります。
	 */
	private[this] var selectionKey:Option[SelectionKey] = None

	/**
	 * このインスタンスを文字列として参照します。
	 * @return インスタンスの文字列表現
	 */
	override def toString():String = selectionKey match {
		case Some(key) =>
			val socket = key.channel().asInstanceOf[SocketChannel].socket()
			"%s at port %d".format(socket.getInetAddress.getHostAddress, socket.getPort)
		case None => "(disconnected)"
	}

	/**
	 * このエンドポイントのコンテキストを使用して指定されたホストへ接続します。
	 * @param hostname 接続先のホスト名
	 * @param port 接続先のポート番号
	 * @return 接続したエンドポイント
	 */
	def connect(hostname:String, port:Int):Endpoint = connect(new InetSocketAddress(hostname, port))

	/**
	 * このエンドポイントのコンテキストを使用して指定されたアドレスへ接続します。
	 * @param address 接続先のアドレス
	 * @return 接続したエンドポイント
	 */
	def connect(address:SocketAddress):Endpoint = synchronized {
		selectionKey match {
			case Some(k) =>
				if(k.channel().asInstanceOf[SocketChannel].isOpen) {
					throw new IllegalStateException("endpoint is already open state")
				}
			case None =>
		}
		dispatcher.bind(SocketChannel.open(address), this)
		this
	}

	/**
	 * 指定された SelectionKey (≒ Channel) をこのエンドポイントに設定します。このメソッドの呼び出しによりこの
	 * Endpoint は接続状態になります。
	 * @param key このエンドポイントに設定する SelectionKey
	 */
	private[core] def setSelectionKey(key:SelectionKey):Unit = {
		assert(key != null)
		assert(selectionKey.isEmpty)
		selectionKey = Some(key)
		onConnect()
	}

	/**
	 * このエンドポイントの Channel を参照します。
	 */
	private[this] def channel:SocketChannel = {
		selectionKey match {
			case Some(k) => k.channel().asInstanceOf[SocketChannel]
			case None => throw new IllegalStateException("not connected yet")
		}
	}

	def setSocketOption[T](name:SocketOption[T], value:T){
		channel.setOption(name, value)
	}

	/**
	 * Finish all pending writing buffer and close this endpoint.
	 *
	 * このエンドポイントを非同期でクローズします。この呼び出しにより直ちに読み出し処理が停止し、バッファリングされ
	 * ているすべての出力待ちデータの書き込みが終わった後に Channel のクローズが行われます。
	 */
	def close():Unit = {
		EventLog.debug("closing connection by protocol")
		channel.shutdownInput()
		dispatcher.eachListener { _.onClosing(this) }
		_out.close()
	}

	/**
	 * このエンドポイントを Selector から切り離し Channel のクローズを行います。
	 * `Context` から呼び出されます。
	 */
	private def internalClose():Unit = {
		selectionKey.get.cancel()
		channel.close()
		onClose()
		dispatcher.eachListener { _.onClosed(this) }
		EventLog.debug("connection closed by protocol")
	}

	/**
	 * Channel が読み込み可能になったときに `Context` から呼び出されます。
	 * @param buffer 読み出し用の共用バッファ
	 * @return 相手側から Channel がクローズされている場合 false
	 */
	private[core] def internalRead(buffer:ByteBuffer):Boolean = _in.internalRead(buffer)

	/**
	 * Channel が書き込み可能になったときに `Context` から呼び出されます。
	 */
	private[core] def internalWrite():Unit = _out.internalWrite()

	private class InternalReadableStreamBuffer extends ReadableStreamBuffer() {

		/**
		 * 内部バッファの初期サイズ。
		 */
		val initialSize:Int = 4 * 1024

		/**
		 * バッファ拡張時の計数。データを格納するために必要なバッファサイズ×`factor` が実際に使用されるバッファ
		 * サイズとなります。
		 */
		val factor:Double = 1.5

		/**
		 * Internal buffer that automatically expand. This is not overwritten for available data block
		 * because these are shared by chunked ByteBuffer.
		 * position() is offset to head of available data
		 * remainings() is available data length.
		 *
		 * 自動拡張する内部バッファ。このインスタンスは複数のスレッドで共有されることはない。`position()` がバッファ
		 * 内の読み出されていないデータの先頭を表し、remainings() が有効なデータを表す。
		 */
		private[this] var buffer = ByteBuffer.allocate(initialSize)
		buffer.limit(0)

		/**
		 * Available data length contained in this steam buffer.
		 *
		 * このストリームバッファが保持している有効なデータの長さを参照します。
		 */
		def length:Int = buffer.remaining()

		def readPendingBufferOverflow = (buffer.remaining() >= maxReadPendingBufferSize)

		/**
		 * Refer whether OP_READ option set.
		 * このエンドポイントの SelectionKey に `OP_READ` が設定されている場合 true を返します。
		 * @return true if selection key has read option
		 */
		def readable:Boolean = {
			selectionKey.isDefined && (selectionKey.get.interestOps() & SelectionKey.OP_READ) != 0
		}

		/**
		 * Set OP_READ option.
		 * このエンドポイントの SelectionKey に `OP_READ` を設定または削除します。
		 * @param r true if set read option on selection key
		 */
		def readable_=(r:Boolean){
			selectionKey.get.interestOps(
				if(r){
					selectionKey.get.interestOps() | SelectionKey.OP_READ
				} else {
					selectionKey.get.interestOps() & ~SelectionKey.OP_READ
				}
			)
		}

		/**
		 * Read from channel by use of specified buffer and append internal buffer, notify to receive
		 * to protocol.
		 * @param buffer commonly used read buffer
		 * @return false if channel reaches end-of-stream
		 */
		def internalRead(buffer:ByteBuffer):Boolean = {
			val length = channel.read(buffer)
			if(length < 0){
				channel.isOpen
			} else {
				if(length > 0){
					buffer.flip()
					append(buffer, length)
					dispatcher.eachListener { _.onRead(Endpoint.this, length) }
					onArrivalBufferedIn()
					buffer.clear()
				}
				true
			}
		}

		def slice(trimmer:(ByteBuffer)=>Int):Option[ByteBuffer] = synchronized{
			val len = trimmer(buffer.asReadOnlyBuffer())
			if(len <= 0 || len > buffer.limit()){
				None
			} else {
				val buf = buffer.duplicate().asReadOnlyBuffer()
				buf.limit(buf.position() + len)
				buffer.position(buffer.position() + len)

				// バッファ内の有効データサイズが規定値を下回ったら読み込みを再開
				if (! readable && ! readPendingBufferOverflow){
					EventLog.debug("reopen reading: %d/%d bytes".format(buffer.remaining(), maxReadPendingBufferSize))
					readable = true
					onReadBufferUnderLimit()
				}

				Some(buf)
			}
		}

		private[this] def append(buf:ByteBuffer, length:Int):Unit = synchronized{

			// expand internal buffer if remaining area is too small
			if(buffer.capacity() - buffer.limit() < buf.remaining()){
				val baseSize = buffer.remaining() + buf.remaining()

				// バッファ内の有効データサイズが規定量を超えたら読み込みをブロック
				if (baseSize >= maxReadPendingBufferSize){
					EventLog.debug("pending read because read buffer reaches limit: %d/%d bytes".format(baseSize, maxReadPendingBufferSize))
					readable = false
					onReadBufferOverflow()
				}

				// 新しいバッファを作成してデータをコピー
				val newSize = math.max(baseSize * factor, initialSize).toInt
				val newBuffer = ByteBuffer.allocate(newSize)
				newBuffer.put(buffer)
				newBuffer.put(buf)
				newBuffer.flip()
				buffer = newBuffer
			}

			// バッファにデータを追加して位置を調整
			val limit = buffer.limit()
			val pos = buffer.position()
			buffer.limit(limit + buf.remaining())
			buffer.position(limit)
			buffer.put(buf)
			buffer.position(pos)
		}

	}

	private class InternalWritableStreamBuffer extends WritableStreamBuffer {

		/**
		 * Flag to specify that write operation is invalid.
		 */
		private[this] var writeOperationClosed = false

		/**
		 * Queued buffer length.
		 */
		@volatile
		private[this] var length = 0

		/**
		 * Write pending queue that may contain ByteBuffers or other operations.
		 */
		private[this] val writeQueue = new LinkedBlockingQueue[()=>Boolean](Int.MaxValue)

		def andThen(callback: =>Unit):WritableStreamBuffer = writeQueue.synchronized {
			if(writeOperationClosed || ! selectionKey.isDefined){
				throw new IOException("channel closed")
			}
			post {() =>
				callback
				true
			}
			this
		}

		def write(buffer:ByteBuffer):WritableStreamBuffer = writeQueue.synchronized {

			// すでにクローズ要求が発行されているかクローズ済みの場合は例外
			if(writeOperationClosed || ! selectionKey.isDefined){
				throw new IOException("channel closed")
			}

			if(buffer.remaining() > 0){

				// 書き込みを待っているデータの総サイズが限界値を超えたら例外
				if(length + buffer.remaining() > maxWritePendingBufferSize){
					throw new IOException(
						"write pending buffer size overflow: %d + %d > %d; this may slow network"
							.format(length, buffer.remaining(), maxWritePendingBufferSize))
				}

				post {() =>
					val len = channel.write(buffer)
					length -= len
					dispatcher.eachListener { _.onWrite(Endpoint.this, len) }
					(buffer.remaining() == 0)
				}
				length += buffer.remaining()
			}
			this
		}

		def close():Unit = writeQueue.synchronized {
			writeOperationClosed = true
			post{ () => true }
		}

		private[this] def post(f:()=>Boolean){
			if(! writeQueue.offer(f)){
				throw new IOException("write queue overflow")
			}
			selectionKey.foreach { k => k.interestOps(k.interestOps() | SelectionKey.OP_WRITE) }
		}

		/**
		 * Write buffered data internally.
		 * This take job from queue and execute it
		 */
		def internalWrite():Unit = writeQueue.synchronized {
			val callback = writeQueue.peek()
			assert(callback != null)
			if(callback()){
				writeQueue.remove()
				if(writeQueue.isEmpty){
					if (writeOperationClosed){
						internalClose()
					} else {
						onDepartureBufferedOut()
						val key = selectionKey.get
						if(writeQueue.isEmpty && key.isValid){
							key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE)
						}
					}
				}
			}
		}
	}

	class Handler private[core] () {
		private var handlers = List[(Endpoint)=>Unit]()
		def apply(f:(Endpoint)=>Unit):Endpoint = attach(f)
		def attach(f:(Endpoint)=>Unit):Endpoint = {
			handlers ::= f
			Endpoint.this
		}
		def detach(f:(Endpoint)=>Unit):Endpoint = {
			handlers = handlers.filter{ _ != f }
			Endpoint.this
		}
		private[core] def apply():Unit = handlers.foreach { _(Endpoint.this) }
	}

	/**
	 * この Endpoint がピアと接続したときに実行する処理を指定します。このメソッドは connect() より前に記述する
	 * 必要があります。
	 */
	val onConnect = new Handler()

	/**
	 */
	val onClose = new Handler()

	/**
	 * この Endpoint に読み出し可能なデータが到着したときに実行する処理を指定します。
	 */
	val onArrivalBufferedIn = new Handler()

	val onDepartureBufferedOut = new Handler()


	// Context#shutdown() により実行される処理。Endpoint へ終了データを書き込む必要がある。
	val onShutdown = new Handler()


	// 読み込みバッファの上限を上回った場合
	val onReadBufferOverflow = new Handler()


	// 読み込みバッファの上限を下回った場合
	val onReadBufferUnderLimit = new Handler()

}

trait ReadableStreamBuffer {

	/**
	 * 読み込み用にバッファリングされるデータの最大バイト数。下層の Channel から読み込まれたがアプリケーションが
	 * slice していないデータ量がこの大きさを超えると Channel からの読み込みがブロックされます。
	 */
	var maxReadPendingBufferSize = 64 * 1024 * 1024

	/**
	 * buffered read-data length.
	 * このストリームバッファにバッファリングされているデータの量。
	 */
	def length:Int

	/**
	 * Retrieve protocol-specified data from read buffer. The closure `trimmer` should scan buffer
	 * and return available data block length of buffer from offset, or zero if buffered data is not
	 * enough.
	 *
	 * このストリームバッファが保持している読み込み済みデータから `f` が定義する長さのデータを取り出します。
	 * バッファ内に有効なデータ単位が格納されていない場合 (より多くのデータを読み込む必要がある場合)、`f` は 0
	 * 以下の値を返す事で次回の呼び出しまで未読データを維持します。この場合メソッドは `None` を返しバッファ内の
	 * 読み込み状態は変化しません。
	 *
	 * @param trimmer
	 * @return Some(ByteBuffer) if closure return available length, or None if 0 or negative value returned
	 */
	def slice(trimmer:(ByteBuffer)=>Int):Option[ByteBuffer]

}

trait WritableStreamBuffer {

	/**
	 * バッファリングされるデータの最大バイト数。出力バッファにこれより大きいデータを投入しようとした場合、
	 * `write()` 操作で `IOException` が発生します。
	 */
	var maxWritePendingBufferSize = 64 * 1024 * 1024

	def write(buffer:Array[Byte]):WritableStreamBuffer = write(buffer, 0, buffer.length)

	def write(buffer:Array[Byte], offset:Int, length:Int):WritableStreamBuffer = {
		val buf = ByteBuffer.allocate(length)
		buf.put(buffer, offset, length)
		buf.flip()
		write(buf)
	}

	/**
	 * Enqueue specified ByteBuffer to write. Note that modification for `buffer` after call effects
	 * write operation.
	 * This is asynchronous operation. If you want to know that buffer finish to write, you can use
	 * `andThen()` callback after `write()`.
	 * @param buffer
	 * @return
	 */
	def write(buffer:ByteBuffer):WritableStreamBuffer

	/**
	 * Enqueue specified callback closure.
	 *
	 * 指定された処理 `callback` を出力キューの順序で非同期に実行します。これは `write()` で行った書き込み要求が
	 * 実際の Channel 上で行われた後に行う必要のある処理を記述するためのものです。通常のストリーム操作では flush
	 * 後に行う処理と等価です。
	 *
	 * {{{
	 *   endpoint.out.write(a).write(b).andThen { println("a, b complete") }.write(c)
	 * }}}
	 *
	 * @param callback
	 * @return
	 */
	def andThen(callback: =>Unit):WritableStreamBuffer

}
