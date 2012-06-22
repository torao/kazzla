/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc

import java.util.concurrent.atomic.AtomicBoolean

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Pipe
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Pipe private[drpc](id:Long, codec:Codec) {

	// ========================================================================
	// シグナル
	// ========================================================================
	/**
	 * RPC 呼び出し結果が到着したことを通知するシグナルです。
	 */
	private[this] val signal = new Object()

	// ========================================================================
	// 呼び出し結果
	// ========================================================================
	/**
	 * RPC 呼び出し結果です。
	 */
	private[this] var result:Option[Result] = None

	// ========================================================================
	// キャンセルフラグ
	// ========================================================================
	/**
	 * このパイプがキャンセルされたかを表すフラグです。パイプのキャンセルはこちら側または
	 * 相手側によって行われます。
	 */
	private[this] val canceled = new AtomicBoolean(false)

	// ========================================================================
	// 結果の参照
	// ========================================================================
	/**
	 * 処理をブロックし RPC の実行結果を参照します。リモート側で例外が発生した場合は例外が
	 * スローされます。
	 * @param timeout 応答までのタイムアウト時間 (ミリ秒)
	 * @throws RemoteException リモート側で例外が発生した場合
	 * @throws CancelException 待機中に処理がキャンセルされた場合
	 */
	def apply(timeout:Long = 0):Seq[Any] = {
		val result = get(timeout).get
		result.error match{
			case Some(message) =>
				throw new RemoteException(message)
			case None =>
				result.result
		}
	}

	// ========================================================================
	// 結果の参照
	// ========================================================================
	/**
	 * 結果を参照します。指定されたタイムアウトまでに結果のリターンがなかった場合は None
	 * を返します。指定された待ち時間までに応答がなかった場合は None を返します。
	 * 待ち時間に 0 を指定した場合、応答があるまで永遠に待機します。この場合 None が返る
	 * ことはありません。
	 * @param timeout 応答待ち時間 (ミリ秒)
	 * @return RPC 実行結果
	 */
	def get(timeout:Long):Option[Result] = {
		signal.synchronized{
			if(! canceled.get() && result.isEmpty){
				if(timeout > 0){
					signal.wait(timeout)
				} else {
					signal.wait()
				}
			}
			if(canceled.get() || result.isEmpty){
				throw new CancelException("operation canceled")
			}
			result
		}
	}

	// ========================================================================
	// 結果の設定
	// ========================================================================
	/**
	 * RPC 実行結果を設定します。
	 * @param value 実行結果
	 */
	private[drpc] def set(value:Result):Unit = {
		signal.synchronized{
			assert(! result.isEmpty)
			result = Some(value)
			signal.notifyAll()
		}
	}

	// ========================================================================
	// 処理のキャンセル
	// ========================================================================
	/**
	 * このパイプを使用して行われている処理をキャンセルします。
	 */
	def cancel():Unit = {
		signal.synchronized{
			canceled.set(true)
			signal.notify()
		}
		// TODO 相手へキャンセルを通知
	}

	// ========================================================================
	// キャンセルの判定
	// ========================================================================
	/**
	 * このパイプが自分または相手側によってキャンセルされているかを判定します。
	 * @return キャンセルされている場合 true
	 */
	def isCanceled:Boolean = canceled.get()

}
