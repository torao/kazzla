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
	// 送信待ちバッファ
	// ========================================================================
	/**
	 * 送信待機しているデータのバッファです。
	 */
	private[this] val sendBuffer = new RawBuffer()

	// ========================================================================
	// 出力バッファ
	// ========================================================================
	/**
	 * このエンドポイントで出力待機しているデータのバッファです。
	 */
	private var out:Option[ByteBuffer] = None

	// ========================================================================
	// セレクションキー
	// ========================================================================
	/**
	 * このエンドポイントのセレクションキーです。特定のセレクターに登録されている場合に
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
	// 出力データ準備状態フラグ
	// ========================================================================
	/**
	 * サブクラスが出力可能なデータを持っているか表すフラグです。
	 */
	private var _writeDataReady:Boolean = false

	// ========================================================================
	// 出力データ準備フラグ変更 Mutex
	// ========================================================================
	/**
	 * 出力データ準備フラグを変更するための Mutex です。
	 */
	private val writeDataReadyMutex = new Object()

	// ========================================================================
	// リスナー
	// ========================================================================
	/**
	 * 非同期 I/O 発生時に呼び出しを行うリスナーです。
	 */
	private[this] var listeners = List[AsyncSocketListener]()

	// ========================================================================
	// 送信データ準備フラグ設定
	// ========================================================================
	/**
	 * 出力可能なデータが準備できたとき true、出力可能なデータがなくなったとき false を
	 * サブクラスから通知します。
	 * @param ready サブクラスで送信データの準備ができた場合 true
	 */
	protected def sendDataReady(ready:Boolean):Unit = {
		writeDataReadyMutex.synchronized{
			if(ready != _writeDataReady){
				_writeDataReady = ready
				// 送信データの準備ができたらチャネルの書き込み可能状態を監視する
				if(_writeDataReady){
					key.foreach{ k =>
						k.interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE)
						k.selector().wakeup()
					}
				} else {
					// 送信データがなくなった場合でもバッファにデータが残っている可能性があるので
					// ただちに OP_WRITE を OFF にはせず次回の出力時まで待つ
				}
			}
		}
	}

	// ========================================================================
	// 送信データの参照
	// ========================================================================
	/**
	 * 送信用のデータを参照するために呼び出されます。
	 * @return 送信用データ
	 */
	def send():ByteBuffer

	// ========================================================================
	// データ受信の通知
	// ========================================================================
	/**
	 * データ受診時に呼び出されます。パラメータとして渡されたバッファは呼び出し終了後に
	 * クリアされるためサブクラス側で保持できません。
	 */
	def receive(buffer:ByteBuffer, offset:Int, length:Int):Unit

	// ========================================================================
	// データの送信
	// ========================================================================
	/**
	 * このエンドポイントが保持している送信バッファのデータを送信します。送信バッファが空の
	 * 場合はサブクラスからデータを参照します。。
	 */
	private[async] def write():Unit = {

		// 送信バッファの参照
		val buffer = out match {
			case Some(b) => b
			case None =>
				out = Some(send())
				out.get
		}

		// データの送信
		channel.write(buffer)

		// 送信バッファ内のデータを全て送信し終えたらバッファを持たない状態にして次回の呼び出し
		// でサブクラスから取得
		if(buffer.remaining() == 0){
			out = None
			// データが空になりサブクラスのデータもなくなれば OP_WRITE 通知を受けない
			writeDataReadyMutex.synchronized{
				if(! _writeDataReady){
					key.foreach{ _.interestOps(SelectionKey.OP_READ) }
				}
			}
		}
	}

	// ========================================================================
	// データの受信
	// ========================================================================
	/**
	 * 指定されたバッファへデータを読み込みます。
	 */
	private[async] def read(in:ByteBuffer):Unit = synchronized{

		// データの受信
		int len = channel.read(in)

		// サブクラスへ受診したデータを渡してバッファをクリア
		in.flip()
		receive(in, 0, len)
		in.clear()
	}

	// ========================================================================
	// エンドポイントのクローズ
	// ========================================================================
	/**
	 * このエンドポイントをコンテキストから除外しチャネルをクローズします。
	 */
	override def close():Unit = {
		logger.trace(this + " close()")
		try{
			leave()
		} catch {
			case ex:IOException => logger.error("fail to close SelectionKey", ex)
		}
		try {
			channel.close()
		} catch {
			case ex:IOException => logger.error("fail to close SocketChannel", ex)
		}
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 */
	private[async] def join(selector:Selector):SelectionKey = keyMutex.synchronized{
		val selectOption = SelectionKey.OP_READ | (if(_writeDataReady) SelectionKey.OP_WRITE else 0)
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
	// データの送信
	// ========================================================================
	/**
	 * 指定されたバイナリデータを非同期で送信します。
	 */
	def send(buffer:Array[Byte], offset:Int, length:Int) = {

	}

	// ========================================================================
	// リスナの追加
	// ========================================================================
	/**
	 * この非同期ソケットにリスナを追加します。
	 * @param listener 追加するリスナ
	 */
	def addAsyncSocketListener(listener:AsyncSocketListener):Unit = synchronized{
		listeners += listener
	}

	// ========================================================================
	// リスナの削除
	// ========================================================================
	/**
	 * この非同期ソケットから指定されたリスナを削除します。
	 * @param listener 削除するリスナ
	 */
	def removeAsyncSocketListener(listener:AsyncSocketListener):Unit = synchronized{
		listeners -= listener
	}

	// ========================================================================
	// インスタンスの文字列化
	// ========================================================================
	/**
	 * このインスタンスを文字列化します。
	 */
	override def toString = "AsyncSocket(" + channel + ")"

}
