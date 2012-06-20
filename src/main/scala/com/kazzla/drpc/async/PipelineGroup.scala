/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import org.apache.log4j.Logger
import java.nio.channels.{Selector, SelectionKey}
import java.io.{IOException, Closeable}
import collection.mutable.Queue
import collection.JavaConversions._
import java.nio.ByteBuffer

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// PipelineGroup
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * パイプラインのスレッドプール
 * @author Takami Torao
 */
class PipelineGroup extends Closeable with java.lang.AutoCloseable{
	import PipelineGroup.logger

	// ========================================================================
	// スレッドグループ
	// ========================================================================
	/**
	 * ディスパッチャースレッドを所属させるスレッドグループです。
	 */
	private[async] val threadGroup = new ThreadGroup("PipelineGroup")

	// ========================================================================
	// 1 スレッドあたりのソケット数
	// ========================================================================
	/**
	 * 1スレッドが担当するパイプライン数の上限です。スレッドの担当するパイプライン数がこの数
	 * を超えると新しいスレッドが生成されます。
	 */
	var maxSocketsPerThread = 512

	// ========================================================================
	// 読み込みバッファサイズ
	// ========================================================================
	/**
	 * 1 スレッド内で使用する読み込みバッファサイズです。
	 */
	var readBufferSize = 4 * 1024

	// ========================================================================
	// ディスパッチャースレッド
	// ========================================================================
	/**
	 * 実行中のディスパッチャースレッドです。
	 */
	private[this] var dispatchers = List[Dispatcher]()

	// ========================================================================
	// 起動中のディスパッチャースレッド数
	// ========================================================================
	/**
	 * 実行中のディスパッチャースレッド数を参照します。
	 */
	def activeThreads:Int = dispatchers.foldLeft(0){ (n,w) => n + (if(w.isAlive) 1 else 0) }

	// ========================================================================
	// パイプライン数
	// ========================================================================
	/**
	 * 接続中のパイプライン数を参照します。
	 */
	def activePipelines:Int = dispatchers.foldLeft(0){ _ + _.activePipeilnes }

	// ========================================================================
	// パイプライン処理の開始
	// ========================================================================
	/**
	 * 指定されたパイプラインをこのグループに関連付け非同期入出力処理を開始します。
	 * @param pipeline このグループで処理を開始するパイプライン
	 */
	def begin(pipeline:Pipeline):Unit = synchronized{
		if(dispatchers.find{ _.join(pipeline) }.isEmpty){
			// 既に停止しているスレッドを除去
			dispatchers = dispatchers.filter{ _.isAlive }
			// 新しいスレッドを作成
			logger.debug("creating new worker thread: " + activePipelines + " + 1 sockets")
			val dispatcher = new Dispatcher(this)
			dispatchers ::= dispatcher
			dispatcher.start()
			val success = dispatcher.join(pipeline)
			if(! success){
				throw new IllegalStateException()
			}
		}
	}

	// ========================================================================
	// パイプライン処理の終了
	// ========================================================================
	/**
	 * 指定されたパイプラインをこのグループから切り離し非同期入出力処理を終了します。
	 * 切り離したパイプラインは (クローズされていなければ) 別のグループでパイプライン処理
	 * を続行させることが可能です。
	 * @param pipeline このグループでの処理を終了するパイプライン
	 * @return 指定されたパイプラインが切り離された場合 true
	 */
	def exit(pipeline:Pipeline):Boolean = synchronized{
		dispatchers.find{ _.leave(pipeline) }.isDefined
	}

	// ========================================================================
	// パイプライン処理のクローズ
	// ========================================================================
	/**
	 * このグループを終了します。
	 */
	override def close():Unit = synchronized {
		dispatchers.foreach{ worker =>
			worker.closeAll()
			worker.interrupt()
		}
	}

	// ========================================================================
	// パイプライン処理のクローズ
	// ========================================================================
	/**
	 * このグループを終了します。
	 */
	private[async] def takeOver(dispatcher:Dispatcher):Unit = {

		// ディスパッチャーをリストから除去
		synchronized{
			dispatchers = dispatchers.filter{ _.ne(dispatcher) }
		}

		// スレッドが終了する前に全ての処理中の非同期ソケットを別のスレッドに割り当て
		dispatcher.pipelines.foreach{ begin(_) }
	}

	// TODO reboot and takeover thread that works specified times

}

object PipelineGroup {
	private[async] val logger = Logger.getLogger(classOf[PipelineGroup])

	private[async] def sk2s(key:SelectionKey):String = {
		val opt = key.readyOps()
		Seq(
			if((opt & SelectionKey.OP_READ) != 0) "READ" else null,
			if((opt & SelectionKey.OP_WRITE) != 0) "WRITE" else null,
			if((opt & SelectionKey.OP_ACCEPT) != 0) "ACCEPT" else null,
			if((opt & SelectionKey.OP_CONNECT) != 0) "CONNECT" else null
		).filter{ _ != null }.mkString("|")
	}

}
