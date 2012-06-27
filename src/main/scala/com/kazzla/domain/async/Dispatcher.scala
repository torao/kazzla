/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.irpc.async

import org.apache.log4j.Logger
import java.nio.channels.{Selector, SelectionKey}
import java.io.{IOException, Closeable}
import collection.mutable.Queue
import collection.JavaConversions._
import java.nio.ByteBuffer

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Dispatcher: ディスパッチャースレッド
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 複数のパイプラインに対する非同期入出力処理を行うスレッドです。
 * @author Takami Torao
 */
private[async] class Dispatcher(group:PipelineGroup) extends Thread(group.threadGroup, "PipelineDispatcher") {
	import Dispatcher.logger

	// ========================================================================
	// セレクター
	// ========================================================================
	/**
	 * このインスタンスが使用するセレクターです。
	 */
	private[this] val selector = Selector.open()

	// ========================================================================
	// 接続キュー
	// ========================================================================
	/**
	 * このディスパッチャーに新しいパイプラインを追加するためのキューです。
	 */
	private[this] val joinQueue = new Queue[Pipeline]()

	// ========================================================================
	// イベントループ中フラグ
	// ========================================================================
	/**
	 * このディスパッチャーがイベントループ中かどうかを表すフラグです。
	 */
	private[this] val inEventLoop = new java.util.concurrent.atomic.AtomicBoolean(true)

	// ========================================================================
	// パイプライン数の参照
	// ========================================================================
	/**
	 * このディスパッチャーが担当しているパイプライン数を参照します。
	 */
	def activePipeilnes:Int = selector.keys().size()

	// ========================================================================
	// パイプラインの参照
	// ========================================================================
	/**
	 * このディスパッチャーが担当しているパイプラインを参照します。
	 */
	def pipelines:Seq[Pipeline] = selector.keys().map{ key => key.attachment().asInstanceOf[Pipeline] }.toSeq.distinct

	// ========================================================================
	// パイプラインの追加
	// ========================================================================
	/**
	 * このディスパッチャーにパイプラインを追加します。パイプライン数が 1 スレッドあたり
	 * の上限に達している場合は何も行わず false を返します。
	 * @param pipeline 追加するパイプライン
	 * @return 追加できなかった場合 false
	 */
	def join(pipeline:Pipeline):Boolean = {
		// イベントループが開始していない場合や担当ソケットがいっぱいの場合は false を返す
		if(! inEventLoop.get() || ! isAlive || activePipeilnes >= group.maxSocketsPerThread){
			return false
		}
		// スレッドのイベントループ内で追加しないと例外が発生する
		joinQueue.synchronized{
			joinQueue.enqueue(pipeline)
		}
		selector.wakeup()
		true
	}

	// ========================================================================
	// パイプラインの切り離し
	// ========================================================================
	/**
	 * 指定されたパイプラインをこのスレッドから切り離します。
	 * 該当するパイプラインが存在しない場合は false を返します。
	 * @param pipeline 除去するパイプライン
	 * @return パイプラインを除去した場合 true
	 */
	def leave(pipeline:Pipeline):Boolean = {
		selector.keys().foreach{ key =>
			val pipeline = key.attachment().asInstanceOf[Pipeline]
			if(pipeline.eq(pipeline)){
				pipeline.register(None)
				return true
			}
		}
		false
	}

	// ========================================================================
	// パイプラインの全クローズ
	// ========================================================================
	/**
	 * このディスパッチャーが管理している全てのパイプラインをクローズします。
	 */
	def closeAll():Unit = {
		selector.keys().foreach{ key =>
			val pipeline = key.attachment().asInstanceOf[Pipeline]
			try {
				pipeline.close()
			} catch {
				case ex:Exception => logger.error("fail to close pipeline: " + pipeline, ex)
			}
		}
	}

	// ========================================================================
	// スレッドの実行
	// ========================================================================
	/**
	 * イベントディスパッチループを開始します。
	 */
	override def run():Unit = {
		inEventLoop.set(true)
		logger.debug("start async pipeline I/O dispatcher")

		// データ入力用バッファを作成 (全パイプライン共用)
		val inBuffer = ByteBuffer.allocate(group.readBufferSize)

		while({ select(); inEventLoop.get() }){

			val keys = selector.selectedKeys()
			val it = keys.iterator()
			while(it.hasNext){

				// 入出力可能になっパイプラインを参照
				val key = it.next()
				it.remove()
				val pipeline = key.attachment().asInstanceOf[Pipeline]
				if(logger.isTraceEnabled){
					logger.trace("selection state: %s -> %s".format(pipeline, Dispatcher.sk2s(key)))
				}

				if(key.isReadable){
					// データ入力処理の実行
					try {
						pipeline.read(inBuffer)
					} catch {
						case ex:Exception =>
							logger.error("uncaught exception in read operation, closing connection", ex)
							pipeline.close()
					}
				} else if (key.isWritable){
					// データ出力処理の実行
					try {
						pipeline.write()
					} catch {
						case ex:Exception =>
							logger.error("uncaught exception in write operation, closing connection", ex)
							pipeline.close()
					}
				} else {
					logger.warn("unexpected selection key state: 0x%X (%s)".format(key.readyOps(), Dispatcher.sk2s(key)))
				}
			}
		}

		group.takeOver(this)
		logger.debug("exit async pipeline I/O dispatcher")
	}

	// ========================================================================
	// 入出力可能チャネルの待機
	// ========================================================================
	/**
	 * このディスパッチャーが管理しているパイプラインのいずれかが入出力可能になるまで待機
	 * します。イベントループの終了を検知した場合は inEventLoop を false に設定し終了
	 * します。
	 */
	private def select():Unit = {

		// チャネルが送受信可能になるまで待機
		try {
			selector.select()
			if(logger.isTraceEnabled){
				logger.trace("async pipeline I/O dispatcher awake")
			}
		} catch {
			case ex:IOException =>
				logger.fatal("select operatin failure: " + select, ex)
				inEventLoop.set(false)
				return
		}

		// スレッドが割り込まれていたら終了
		if(Thread.currentThread().isInterrupted){
			logger.debug("async pipeline I/O dispatcher interrupted")
			inEventLoop.set(false)
			return
		}

		// このスレッドへ参加するパイプラインを取り込み
		joinQueue.synchronized {
			while(! joinQueue.isEmpty){
				val pipeline = joinQueue.dequeue()
				try {
					pipeline.register(Some(selector))
					logger.debug("join new async socket in dispatcher")
				} catch {
					case ex:IOException =>
						logger.error("register operation failed, ignore and close socket: " + pipeline, ex)
						pipeline.close()
				}
			}
		}
	}

}

object Dispatcher {
	private[Dispatcher] val logger = Logger.getLogger(classOf[Dispatcher])

	private[Dispatcher] def sk2s(key:SelectionKey):String = {
		val opt = key.readyOps()
		Seq(
			if((opt & SelectionKey.OP_READ) != 0) "READ" else null,
			if((opt & SelectionKey.OP_WRITE) != 0) "WRITE" else null,
			if((opt & SelectionKey.OP_ACCEPT) != 0) "ACCEPT" else null,
			if((opt & SelectionKey.OP_CONNECT) != 0) "CONNECT" else null
		).filter{ _ != null }.mkString("|")
	}

}
