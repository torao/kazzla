/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc

import collection.mutable.HashMap
import java.io.IOException

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Pipe
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Pipe private[drpc](id:Long) {

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
	private[this] var result:Option[Protocol.Result] = None

	// ========================================================================
	// 結果の参照
	// ========================================================================
	/**
	 * 処理をブロックし RPC の実行結果を参照します。リモート側で例外が発生した場合は例外が
	 * スローされます。
	 */
	def apply():Seq[Any] = {
		val result = get(0).get
		result.error match{
			case Some(message) =>
				throw new Exception(message)		// TODO appropriate exception class
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
	def get(timeout:Long):Option[Protocol.Result] = {
		signal.synchronized{
			if(result.isEmpty){
				if(timeout > 0){
					signal.wait(timeout)
				} else {
					signal.wait()
				}
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
	private[drpc] def set(value:Protocol.Result):Unit = {
		signal.synchronized{
			assert(! result.isEmpty)
			result = Some(value)
			signal.notifyAll()
		}
	}

}
