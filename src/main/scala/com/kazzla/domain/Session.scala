/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain

import org.apache.log4j.Logger
import java.util.concurrent.atomic.AtomicBoolean
import com.kazzla.domain.async.PipelineGroup
import java.lang.reflect.{Method, InvocationHandler}
import com.kazzla.domain.irpc.Alias

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Session
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * <p>
 * 異なるセッション間で接続が共有されることはありません。
 * </p>
 * @author Takami Torao
 */
class Session private[domain](val domain: Domain) {
/*
パイプライングループ
リモート呼び出し処理
ローカル呼び出し処理
ローカル呼び出し用スレッドプール
接続中のピア
サービス実装
*/

	// ========================================================================
	// パイプライングループ
	// ========================================================================
	/**
	 * このセッション上での非同期入出力を行うパイプライングループです。
	 */
	private[domain] val context = new PipelineGroup()

	// ========================================================================
	// ========================================================================
	/**
	 * 指定された転送単位を転送します。
	 * 指定されたデータブロックを転送します。
	 */
	def lookupService[T <: Service](name:String, interface:Class[T]): T = {
		if(closed){
			throw new IllegalStateException("session closed")
		}
		// TODO
		null
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


	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	//
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * このドメインのサービス処理を行うスレッドプールです。
	 */
	private[Session] class ServiceInvoker(serviceName:String) extends InvocationHandler {
		override def invoke(proxy:Any, method:Method, args:Array[AnyRef]):AnyRef = {

			// メソッド名の参照
			val alias = Option(method.getAnnotation(classOf[Alias])).getOrElse(method.getName)
			
		}
	}

}