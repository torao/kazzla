/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import java.nio.ByteBuffer
import java.io.{Closeable, IOException}
import java.nio.channels.{SelectionKey, Selector, SocketChannel}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// AsyncSocket
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 非同期入出力のチャネルとデータの入出力を管理するクラスです。
 * クローズ済みのチャネルを指定した場合は例外が発生します。
 * @author Takami Torao
 * @param channel このエンドポイントのチャネル
 */
abstract class AsyncSocket(channel:SocketChannel) extends Closeable{
	channel.configureBlocking(false)

	// ========================================================================
	// 送信待ちキュー
	// ========================================================================
	/**
	 * このソケットに対して送信待機しているデータのキューです。
	 */
	private[this] val sendQueue = new RawBuffer()

	// ========================================================================
	// 送信中バッファ
	// ========================================================================
	/**
	 * このソケット上で出力中のデータのバッファです。出力中のバッファが存在しない場合は
	 * None となります。
	 */
	private[this] var out:Option[ByteBuffer] = None

	// ========================================================================
	// セレクションキー
	// ========================================================================
	/**
	 * この非同期ソケットのセレクションキーです。特定のセレクターに登録されている場合に
	 * Some となります。チャネルに対する Write 可能通知の ON/OFF を切り替えるために使用
	 * します。
	 */
	private var key:Option[SelectionKey] = None

	// ========================================================================
	// セレクションキー変更 Mutex
	// ========================================================================
	/**
	 * セレクションキーの値を変更するための Mutex です。
	 */
	private val keyMutex = new Object()

	// ========================================================================
	// リスナー
	// ========================================================================
	/**
	 * 非同期 I/O 発生時に呼び出しを行うリスナーです。
	 */
	private[this] var listeners = List[AsyncSocketListener]()

	// ========================================================================
	// データの送信
	// ========================================================================
	/**
	 * この非同期ソケットが保持している送信データを送信します。
	 */
	private[async] def channelWrite():Unit = {

		// 送信中のバファを参照
		val buffer = out match {
			case Some(b) => b
			case None =>
				// 送信中バッファがなくなり送信キューも空なら、次の送信データが到着するまで
				// OP_WRITE 通知を受けない
				sendQueue.synchronized{
					if(sendQueue.length == 0){
						key.foreach{ _.interestOps(SelectionKey.OP_READ) }
						return
					}
				}
				out = Some(sendQueue.dequeue())
				out.get
		}

		// バイナリデータの送信
		channel.write(buffer)

		// 送信バッファ内のデータを全て送信し終えたらバッファを持たない状態にして次回の呼び出し
		// で送信キューから取得
		if(buffer.remaining() == 0){
			out = None
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
		channel.read(in)

		// 受信したデータをリスナに通知しバッファをクリア
		in.flip()
		listeners.foreach{ listener =>
			listener.asyncDataReceived(in)
		}
		in.clear()
	}

	// ========================================================================
	// データの非同期送信
	// ========================================================================
	/**
	 * 指定されたバイナリデータを非同期で送信します。メソッドはすぐに完了しますが、データが
	 * 送信完了している保証はありません。
	 * @param buffer バッファ
	 * @param offset バッファ内での送信データの開始位置
	 * @param length 送信データの長さ
	 */
	def send(buffer:Array[Byte], offset:Int, length:Int):Unit = sendQueue.synchronized{
		val old = sendQueue.length
		sendQueue.enqueue(buffer, offset, length)

		// 送信データの準備ができたらチャネルの書き込み可能状態を監視する
		if(old == 0 && sendQueue.length > 0){
			key.foreach{ k =>
				k.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE)
				k.selector().wakeup()
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
		// コンテキストから切り離し
		try{
			leave()
		} catch {
			case ex:IOException => logger.error("fail to close SelectionKey", ex)
		}
		// チャネルのクローズ
		try {
			channel.close()
		} catch {
			case ex:IOException => logger.error("fail to close SocketChannel", ex)
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
	//
	// ========================================================================
	/**
	 */
	private[async] def join(selector:Selector):SelectionKey = keyMutex.synchronized{
		val selectOption = SelectionKey.OP_READ | (if(sendQueue.length > 0) SelectionKey.OP_WRITE else 0)
		key = Some(channel.register(selector, selectOption))
		key.get
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 */
	private[async] def leave():Unit = keyMutex.synchronized{
		key.foreach{ _.cancel() }
		key = None
	}

	// ========================================================================
	// インスタンスの文字列化
	// ========================================================================
	/**
	 * このインスタンスを文字列化します。
	 */
	override def toString = "AsyncSocket(" + channel + ")"

}
