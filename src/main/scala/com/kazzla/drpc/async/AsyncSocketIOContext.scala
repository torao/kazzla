/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// AsyncSocketIOContext: 非同期 I/O コンテキスト
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * SocketChannel を使用した非同期 I/O のためのクラスです。複数の Endpoint の通信処理を
 * 行うワーカースレッドプールを保持しています。
 * @author Takami Torao
 */
class AsyncSocketIOContext {

	// ========================================================================
	// 1スレッドあたりの Endpoint 数
	// ========================================================================
	/**
	 * 1ワーカースレッドが担当する Endpoint 数の上限です。スレッドの担当するソケット数がこの
	 * 数を超えると新しいスレッドが生成されます。
	 */
	var maxEndpointsPerThread = 512

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
	// エンドポイント数
	// ========================================================================
	/**
	 * 接続中のエンドポイント数を参照します。
	 */
	def activeEndpoints:Int = workers.foldLeft(0){ _ + _.activeEndpoints }

	// ========================================================================
	// エンドポイントの追加
	// ========================================================================
	/**
	 * このコンテキストに新しいエンドポイントを参加します。
	 * @param endpoint 参加するエンドポイント
	 */
	def join(endpoint:Endpoint):Unit = synchronized{
		val worker = {
			// 後方の方が空いている可能性が高いので後方から検索
			workers.reverse.find{
				worker => worker.activeEndpoints < maxEndpointsPerThread
			} match {
				case Some(worker) => worker
				case None =>
					logger.debug("creating new worker thread: " + activeEndpoints + " + 1 endpoints")
					val worker = new Worker(readBufferSize)
					worker.start()
					workers ::= worker
					worker
			}
		}
		worker.join(endpoint)
	}

	// ========================================================================
	// エンドポイントの切り離し
	// ========================================================================
	/**
	 * このコンテキストから指定されたエンドポイントを切り離します。
	 * 切り離しを行ったエンドポイントはまだクローズされていません。
	 * @param endpoint 切り離すエンドポイント
	 * @return 指定されたエンドポイントが見つかり切り離された場合 true
	 */
	def leave(endpoint:Endpoint):Boolean = synchronized{
		workers.find{ worker => worker.leave(endpoint) } match {
			case Some(_) => true
			case None => false
		}
	}

	// TODO reboot and takeover thread that works specified times
}
