/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import collection.mutable.{ArrayBuffer, Buffer}
import collection.JavaConversions._
import java.nio.ByteBuffer
import java.io.IOException
import java.nio.channels.Selector

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Worker: ワーカー
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 複数のエンドポイントの受送信処理を担当するスレッドです。
 * @author Takami Torao
 * @param readBufferSize 受信用に使用するバッファのサイズ
 */
private[async] class Worker(readBufferSize:Int) extends Thread {

	// ========================================================================
	// セレクター
	// ========================================================================
	/**
	 * このインスタンスが使用するセレクターです。
	 */
	private val selector = Selector.open()

	// ========================================================================
	// 接続キュー
	// ========================================================================
	/**
	 * このワーカーに新しい接続先を追加するためのキューです。
	 */
	private val queue:Buffer[Endpoint] = new ArrayBuffer[Endpoint]()

	// ========================================================================
	// ソケット数の参照
	// ========================================================================
	/**
	 * このワーカーが担当しているソケット数を参照します。
	 */
	def socketCount:Int = selector.keys().size()

	// ========================================================================
	// ピアの追加
	// ========================================================================
	/**
	 * このワーカーが入出力処理を行うピアを追加します。
	 */
	def join(peer:Endpoint):Unit = {
		queue.synchronized{
			queue.append(peer)
		}
		selector.wakeup()
	}

	// ========================================================================
	// エンドポイントの除去
	// ========================================================================
	/**
	 * このワーカーが担当しているエンドポイントから指定されたものを除去します。
	 * 該当するエンドポイントが存在しない場合は false を返します。
	 * @param endpoint 除去するエンドポイント
	 * @return エンドポイントを除去した場合 true
	 */
	def leave(endpoint:Endpoint):Boolean = {
		selector.keys.foreach{ key =>
			val peer = key.attachment().asInstanceOf[Endpoint]
			if(peer == endpoint){
				endpoint.leave()
				return true
			}
		}
		false
	}

	// ========================================================================
	// スレッドの実行
	// ========================================================================
	/**
	 * データの送受信が可能になったエンドポイントに対してイベントループを実行します。
	 */
	override def run():Unit = {

		// データ受信用バッファを作成 (全エンドポイント共用)
		val readBuffer = ByteBuffer.allocate(readBufferSize)
		while(select()){

			// 送受信可能になったエンドポイントを参照
			val keys = selector.selectedKeys()
			val it = keys.iterator()
			while(it.hasNext){
				val key = it.next()
				it.remove()
				val endpoint = key.attachment().asInstanceOf[Endpoint]

				// データの受信処理を実行
				if(key.isReadable){
					try {
						endpoint.read(readBuffer)
					} catch {
						case ex:Exception =>
							logger.error("uncaught exception in read operation, closing connection", ex)
							endpoint.close()
					}
				}

				// データの送信処理を実行
				if (key.isWritable){
					try {
						endpoint.write()
					} catch {
						case ex:Exception =>
							logger.error("uncaught exception in write operation, closing connection", ex)
							endpoint.close()
					}
				} else {
					logger.warn("unexpected key status: " + key)
				}
			}
		}
	}

	// ========================================================================
	// 送受信可能チャネルの待機
	// ========================================================================
	/**
	 * チャネルのいずれかが送受信可能になるまで待機します。
	 * @return イベントループを続行する場合 true
	 */
	private def select():Boolean = {

		// チャネルが送受信可能になるまで待機
		try {
			selector.select()
		} catch {
			case ex:IOException =>
				logger.fatal("select operatin failure: " + select, ex)
				return false
		}

		// スレッドが割り込まれていたら終了
		if(Thread.currentThread().isInterrupted){
			return false
		}

		// このスレッドへ参加するエンドポイントを取り込み
		queue.synchronized {
			while(! queue.isEmpty){
				val endpoint = queue.remove(0)
				try {
					val key = endpoint.join(selector)
					key.attach(endpoint)
				} catch {
					case ex:IOException =>
						logger.error("register operation failed, ignore and close endpoint: " + endpoint, ex)
						endpoint.close()
				}
			}
		}
		true
	}

}
