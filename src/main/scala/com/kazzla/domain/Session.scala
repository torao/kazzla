/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain

import org.apache.log4j.Logger
import java.util.concurrent.atomic.AtomicBoolean
import com.kazzla.domain.async.{Pipeline, PipelineGroup}
import java.lang.reflect.{Method, InvocationHandler}
import com.kazzla.domain.irpc.Alias
import java.util.concurrent._
import java.net.{InetSocketAddress, URI}
import java.nio.channels.SocketChannel

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Session
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * <p>
 * 異なるセッション間で接続が共有されることはありません。
 * </p>
 * @author Takami Torao
 */
class Session private[domain](val domain: Domain, config:Configuration) {
/*
パイプライングループ
リモート呼び出し処理
ローカル呼び出し処理
ローカル呼び出し用スレッドプール
接続中のピア
サービス実装
*/

	// ========================================================================
	// スレッドプール
	// ========================================================================
	/**
	 * このセッション上でサービスの処理を行うためのスレッドプールです。
	 */
	private[this] val executor = new ThreadPoolExecutor(
		config.get("threads.corePoolSize", 5),
		config.get("threads.maximumPoolSize", 5),
		config.get("threads.keepAliveTime", 10 * 1000L),
		TimeUnit.MILLISECONDS,
		new ArrayBlockingQueue[Runnable](
			config.get("threads.queue.capacity", Int.MaxValue),
			config.get("threads.queue.fair", false)
		)
	)

	// ========================================================================
	// パイプライングループ
	// ========================================================================
	/**
	 * このセッション上での非同期入出力を行うパイプライングループです。
	 */
	private[domain] val context = new PipelineGroup()

	// ========================================================================
	// レジストリサービス
	// ========================================================================
	/**
	 * このセッションが使用しているレジストリサービスです。
	 */
	private[this] var _registryService:Option[RegistryService] = None

	// ========================================================================
	// レジストリサービスの参照
	// ========================================================================
	/**
	 * このセッションが使用しているレジストリサービスです。
	 */
	def registryService:RegistryService = {
		_registryService.get match {
			case Some(reg) => reg
			case None =>
				domain.registryServers.foreach { uri =>
					try {

					}
				}
		}
	}

	// ========================================================================
	// レジストリサービスの参照
	// ========================================================================
	/**
	 * このセッションが使用しているレジストリサービスです。
	 */
	private[this] def connect(uri:URI):Pipeline = {

		// ソケット経由の接続を実行
		val host = uri.getHost
		val port = if(uri.getPort >= 0) uri.getPort else Domain.DEFAULT_PORT
		val address = new InetSocketAddress(host, port)
		val channel = SocketChannel.open(address)

		new Pipeline({}){
			def in = channel
			def out = channel
		}
	}

	// ========================================================================
	// リモートサービス
	// ========================================================================
	/**
	 * このセッション上で参照されているリモートサービスの一覧です。
	 */
	private[this] var remoteServices = Map[String,Service]()

	// ========================================================================
	// ========================================================================
	/**
	 * 指定された転送単位を転送します。
	 * 指定されたデータブロックを転送します。
	 */
	def lookupService[T <: Service](name:String, interface:Class[T]):T = {
		if(closed){
			throw new IllegalStateException("session closed")
		}

		remoteServices.get(name) match {
			case Some(service) => service
			case None =>
				domain.registryServers.find{ uri =>

				}
		}
	}

	// ========================================================================
	// ========================================================================
	/**
	 * 指定された転送単位を転送します。
	 * 指定されたデータブロックを転送します。
	 */
	def lookupService[T <: Service](name:String, interface:Class[T]):T = {
		if(closed){
			throw new IllegalStateException("session closed")
		}

		remoteServices.get(name) match {
			case Some(service) => service
			case None =>
				domain.registryServers.find{ uri =>

				}
		}
	}

	// ========================================================================
	// サービスの参照
	// ========================================================================
	/**
	 * 指定された接続先のいずれかを使用するサービスを参照します。
	 */
	private[this] def getService[T <: Service](name:String, addresses:Iterable[String]):T = {

		// 接続の
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
	 * このセッション上で確保されている不必要なリソースを開放するための定期的に呼び出されます。
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