/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain

import collection.mutable.HashMap
import org.apache.log4j.Logger
import com.kazzla.irpc.async.PipelineGroup
import com.kazzla.irpc._
import com.kazzla.domain.Session.Processing
import java.util.concurrent.atomic.AtomicBoolean

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Session
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * <p>
 * 異なるセッション間で接続が共有されることはありません。
 * </p>
 * @author Takami Torao
 */
class Session private[irpc](val domain: Domain) {

	// ========================================================================
	// パイプライングループ
	// ========================================================================
	/**
	 * このノード上での非同期入出力を行うパイプライングループです。
	 */
	private[irpc] val context = new PipelineGroup()

	// ========================================================================
	// 呼び出し処理
	// ========================================================================
	/**
	 * このセッション上で現在実行中の呼び出し処理です。
	 */
	private[this] var localProcessing = Map[Call,Session.Processing]()

	// ========================================================================
	// ========================================================================
	/**
	 * 指定された転送単位を転送します。
	 * 指定されたデータブロックを転送します。
	 */
	def lookupService[T <: Service](name: String, interface: Class[T]): T = {
		if(closed){
			throw new IllegalStateException("session closed")
		}
	}

	// ========================================================================
	// ========================================================================
	/**
	 * 指定された転送単位を転送します。
	 * 指定されたデータブロックを転送します。
	 */
	def lookupNode()

	// ========================================================================
	// クローズフラグ
	// ========================================================================
	/**
	 * このセッションがクローズされているかを表すフラグです。
	 */
	private[this] val _closed = new AtomicBoolean(false)

	// ========================================================================
	// クローズ判定
	// ========================================================================
	/**
	 * このセッションがクローズされているかを判定します。
	 */
	def closed = _closed.get()

	// ========================================================================
	// セッションのクローズ
	// ========================================================================
	/**
	 * このセッションをクローズし使用していたリソースを全て開放します。
	 */
	def close() {
		_closed.set(true)
		domain.remove(this)
	}

	// ========================================================================
	// セッションのクリーンアップ
	// ========================================================================
	/**
	 * このセッション上で確保されている不必要なリソースを開放します。
	 */
	private[domain] def cleanup(): Unit = {
		// TODO タイムアウトした処理の停止
	}

}

object Session {
	private[Session] val logger = Logger.getLogger(classOf[Session])

	private[Session] case class Processing(timeout:Long, thread:Thread)
}