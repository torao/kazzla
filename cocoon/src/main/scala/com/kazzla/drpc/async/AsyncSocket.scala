/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import java.nio.ByteBuffer
import java.io.{Closeable, IOException}
import java.nio.channels._
import org.apache.log4j.Logger
import scala.Some

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// AsyncSocket
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 非同期入出力のチャネルとデータの入出力を管理するクラスです。
 * クローズ済みのチャネルを指定した場合は例外が発生します。
 * @author Takami Torao
 * @param channel 入出力用非同期チャネル
 * @param initialSendBufferSize 初期送信バッファサイズ
 * @param maxSendBufferSize 最大送信バッファサイズ
 */
class AsyncSocket(channel:SelectableChannel with ReadableByteChannel with WritableByteChannel, initialSendBufferSize:Int, maxSendBufferSize:Int) extends Closeable with AutoCloseable{
	import AsyncSocket.logger

	// I/O を非ブロッキングモードに設定
	channel.configureBlocking(false)

	// ========================================================================
	// コンストラクタ
	// ========================================================================
	/**
	 * 初期送信バッファサイズ 4kB、最大送信バッファサイズ 2GB のインスタンスを構築します。
	 */
	def this(channel:SelectableChannel with ReadableByteChannel with WritableByteChannel) = this(channel, 4 * 1024, Int.MaxValue)

	// ========================================================================
	// 送信待ちデータキュー
	// ========================================================================
	/**
	 * このソケットに対して送信待機しているバイナリデータのキューです。
	 */
	private[this] val sendQueue = new RawBuffer(initialSendBufferSize, maxSendBufferSize)

	// ========================================================================
	// 送信中バッファ
	// ========================================================================
	/**
	 * このソケット上で出力中のデータのバッファです。出力中のバッファが存在しない場合は
	 * None となります。
	 */
	private[this] var sendBuffer:Option[ByteBuffer] = None

	// ========================================================================
	// セレクションキー
	// ========================================================================
	/**
	 * この非同期ソケットのセレクションキーです。特定のセレクターに登録されている場合に
	 * Some となります。チャネルに対する Write 可能通知の ON/OFF を切り替えるために使用
	 * します。
	 * キーに対する Selector を握っている Dispatcher スレッドの Take Over が必要なので一意
	 * にしない。
	 */
	private[this] var key:Option[SelectionKey] = None

	// ========================================================================
	// リスナー
	// ========================================================================
	/**
	 * 非同期 I/O 発生時に呼び出しを行うリスナーです。
	 */
	private[this] var listeners = List[AsyncSocketListener]()

	// ========================================================================
	// データの非同期送信
	// ========================================================================
	/**
	 * 指定されたバイナリデータを非同期で送信します。メソッドはすぐに完了しますが、データが
	 * 送信完了している保証はありません。
	 * 未送信のデータと指定されたデータの合計サイズが送信バッファの最大サイズを超える場合、
	 * このメソッドはブロックします。
	 * @param buffer バッファ
	 * @param offset バッファ内での送信データの開始位置
	 * @param length 送信データの長さ
	 */
	def send(buffer:Array[Byte], offset:Int, length:Int):Unit = synchronized{

		// 送信キューに送信データを連結
		val len = sendQueue.synchronized{
			sendQueue.enqueue(buffer, offset, length)
			sendQueue.length
		}
		if(logger.isTraceEnabled){
			logger.trace("enqueued write buffer, request " + length + " bytes, total " + len + " bytes")
		}

		// 送信データの準備ができたらチャネルの書き込み可能状態を監視する
		key.foreach{ k =>
			if(len > 0 && (k.interestOps() & SelectionKey.OP_WRITE) == 0){
				k.interestOps(k.interestOps() | SelectionKey.OP_WRITE)
				k.selector().wakeup()
				if(logger.isTraceEnabled){
					logger.trace("enable write callback")
				}
			}
		}
	}

	// ========================================================================
	// データの非同期送信
	// ========================================================================
	/**
	 * 指定されたバッファに格納さされている全てのデータを非同期で送信します。
	 * @param buffer 送信バッファ
	 */
	def send(buffer:Array[Byte]):Unit = send(buffer, 0, buffer.length)

	// ========================================================================
	// 非同期ソケットのクローズ
	// ========================================================================
	/**
	 * この非同期ソケットをクローズします。
	 * このエンドポイントをコンテキストから除外しチャネルをクローズします。
	 */
	override def close():Unit = {
		logger.trace(this + " close()")

		// セレクターから切り離し
		try{
			bind(None)
		} catch {
			case ex:IOException => logger.error("fail to close SelectionKey", ex)
		}

		// 入出力チャネルのクローズ
		try {
			channel.close()
		} catch {
			case ex:IOException => logger.error("fail to close I/O channel", ex)
		}

		// ソケットのクローズを通知
		listeners.foreach{ listener =>
			listener.asyncSocketClosed(this)
		}
	}

	// ========================================================================
	// 送信キューサイズの参照
	// ========================================================================
	/**
	 * この非同期ソケット上で送信待機中のキューのサイズを参照します。
	 */
	def sendQueueSize:Int = {
		sendQueue.length
	}

	// ========================================================================
	// リスナの追加
	// ========================================================================
	/**
	 * この非同期ソケットにリスナを追加します。
	 * @param listener 追加するリスナ
	 */
	def addAsyncSocketListener(listener:AsyncSocketListener):Unit = synchronized{
		listeners ::= listener
	}

	// ========================================================================
	// リスナの削除
	// ========================================================================
	/**
	 * この非同期ソケットから指定されたリスナを削除します。
	 * @param listener 削除するリスナ
	 */
	def removeAsyncSocketListener(listener:AsyncSocketListener):Unit = synchronized{
		listeners = listeners.filter{ _ != listener }
	}

	// ========================================================================
	// インスタンスの文字列化
	// ========================================================================
	/**
	 * このインスタンスを文字列化します。
	 */
	override def toString = {
		channel match {
			case socketChannel:SocketChannel =>
				val s = socketChannel.socket()
				s.getInetAddress.getHostName + ":" + s.getPort
			case c =>
				c.toString()
		}
	}

	// ========================================================================
	// データの送信
	// ========================================================================
	/**
	 * この非同期ソケットが保持している送信データを送信します。
	 */
	private[async] def channelWrite():Unit = {

		// 送信中のバファを参照
		val buffer = sendBuffer match {
			case Some(b) => b
			case None =>
				// 送信中バッファがなくなり送信キューも空なら、次の送信データが到着するまで
				// OP_WRITE 通知を受けない
				sendQueue.synchronized{
					if(sendQueue.length == 0){
						key.foreach{ k =>
							k.interestOps(k.interestOps() & ~SelectionKey.OP_WRITE)
							if(logger.isTraceEnabled){
								logger.trace("disable writable callback")
							}
						}
						return
					}
					sendBuffer = Some(sendQueue.dequeue())
				}
				sendBuffer.get
		}

		// バイナリデータの送信
		// 送信しきれなかった分は次回出力可能時に続きから出力される
		val len = channel.write(buffer)
		if(logger.isTraceEnabled){
			logger.trace("write %,d bytes".format(len))
		}

		// 送信バッファ内のデータを全て送信し終えたらバッファを持たない状態にして次回の呼び出し
		// で送信キューから取得
		if(buffer.remaining() == 0){
			sendBuffer = None
		}
	}

	// ========================================================================
	// データの受信
	// ========================================================================
	/**
	 * 指定されたバファを使用してデータの読み込みを行います。
	 * @param in 読み込み用に使用するバッファ
	 */
	private[async] def channelRead(in:ByteBuffer):Unit = {

		// データの受信
		val len = this.channel.read(in)

		// 相手からストリームがクローズされた場合
		if(len < 0){
			logger.debug("closed async I/O socket " + this + " by peer")
			close()
			return
		}

		if(logger.isTraceEnabled){
			logger.trace("read " + len + " bytes")
		}

		// 受信したデータをリスナに通知しバッファをクリア
		in.flip()
		listeners.foreach{ listener =>
			listener.asyncDataReceived(in)
		}
		in.clear()
	}

	// ========================================================================
	// セレクターとのバインド
	// ========================================================================
	/**
	 * この非同期ソケットを指定されたセレクターとバインドします。値に null を指定した場合
	 * は既存のセレクターと切り離します。既に切り離されている非同期ソケットに null を指定
	 * しても何も起きません。
	 * @param selector バインドするセレクター
	 */
	private[async] def bind(selector:Option[Selector]):Option[SelectionKey] = synchronized{

		// 既存のセレクターと切り離し
		if(! key.isEmpty){
			key.get.cancel()
			key = None
		}

		// 指定されたセレクターとバインド
		key = selector match {
			case Some(sel) =>
				val selectOption = SelectionKey.OP_READ | (if(sendQueue.length > 0) SelectionKey.OP_WRITE else 0)
				Some(channel.register(sel, selectOption))
			case None =>
				None
		}
		key
	}

}

object AsyncSocket {
	val logger = Logger.getLogger(AsyncSocket.getClass)
}
