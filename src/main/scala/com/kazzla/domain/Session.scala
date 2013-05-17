/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain

import org.apache.log4j.Logger
import java.util.concurrent.atomic.AtomicBoolean
import com.kazzla.domain.async.{Pipeline, PipelineGroup}
import java.lang.reflect.{Method, InvocationHandler}
import com.kazzla.domain.irpc._
import java.util.concurrent._
import java.net._
import java.nio.channels._
import javax.security.cert.X509Certificate
import java.nio.ByteBuffer
import scala.Some

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Session
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * <p>
 * ドメインに対する一覧の処理を行うための状態を表すセッションです。
 * 異なるセッション間で接続が共有されることはありません。
 * </p>
 * @author Takami Torao
 */
class Session private[domain](val domain: Domain, val myCertification:X509Certificate, config:Configuration) {
/*
ノード証明書(ローカル)
パイプライングループ
スレッドプール
ローカルサービス(共通)
*/

	// ========================================================================
	// スレッドプール
	// ========================================================================
	/**
	 * このセッション上でサービスの処理を行うためのスレッドプールです。
	 */
	private[this] val executor = new ThreadPoolExecutor(
		config("threads.corePoolSize", 5),
		config("threads.maximumPoolSize", 5),
		config("threads.keepAliveTime", 10 * 1000L),
		TimeUnit.MILLISECONDS,
		new ArrayBlockingQueue[Runnable](
			config("threads.queue.capacity", Int.MaxValue),
			config("threads.queue.fair", false)
		)
	)

	// ========================================================================
	// サービス
	// ========================================================================
	/**
	 * このセッション上の全接続で共通して提供されるサービスです。
	 */
	private[this] var services = Map[String,Service]()

	// ========================================================================
	// サービスの設定
	// ========================================================================
	/**
	 * 指定された名前に対する新しいサービスをバインドします。この変更は既に生成されている
	 * `Peer` には影響しません。既に同じ名前に別のサービスがバインドされている場合は新しい
	 * サービスに置き換えられます。
	 */
	def bind(name:String, service:Service):Unit = synchronized{
		services += (name -> service)
	}

	// ========================================================================
	// サービスの削除
	// ========================================================================
	/**
	 * 指定された名前にバインドされているサービスを削除します。この変更は既に生成されている
	 * `Peer` には影響しません。名前に対するサービスがバインドされていない場合はなにも起き
	 * ません。
	 */
	def unbind(name:String):Unit = synchronized{
		services -= name
	}

	// ========================================================================
	// パイプライングループ
	// ========================================================================
	/**
	 * このセッション上での非同期入出力を行うためのパイプライングループです。
	 */
	private[domain] val context = new PipelineGroup()
	context.maxSocketsPerThread = config("pipelines.maxSocketsPerThread", Int.MaxValue)
	context.readBufferSize = config("pipelines.readBufferSize", 8 * 1024)

	// ========================================================================
	// レジストリサービス
	// ========================================================================
	/**
	 * このセッションが使用しているレジストリサービスです。
	 */
	// private[this] var _registryService:Option[RegistryService] = None

	// ========================================================================
	// レジストリサービスの参照
	// ========================================================================
	/**
	 * このセッションが使用しているレジストリサービスです。
	 */
	/*
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
	*/

	// ========================================================================
	// ピア接続の実行
	// ========================================================================
	/**
	 * 指定された URL のピアと接続します。
	 */
	def connect(uri:URI):Peer = {

		// ストリームプロトコルの作成
		val stream = connect(uri, { addr:SocketAddress =>
			val channel = SocketChannel.open()
			channel.connect(addr)
			channel
		})

		// バルクプロトコルの作成
		// TODO UDP チャネルの生成が遅延評価で行われること
		val bulk =connect(uri, { addr:SocketAddress =>
			val channel = DatagramChannel.open()
			channel.connect(addr)
			channel
		})

		// ピアの作成
		new Peer(uri, myCertification, stream, bulk, services, executor)
	}

	// ========================================================================
	// 接続の実行
	// ========================================================================
	/**
	 * このセッション上で指定された URI のノードと接続します。
	 */
	private[this] def connect(uri:URI, f:(SocketAddress)=>SelectableChannel with ReadableByteChannel with WritableByteChannel):Protocol = {
		def factory:((ByteBuffer)=>Unit)=>Pipeline = { dispatcher =>
			val host = uri.getHost
			val port = if(uri.getPort >= 0) uri.getPort else Domain.DEFAULT_PORT
			val address = new InetSocketAddress(host, port)
			val channel = f(address)
			new Pipeline(dispatcher){
				def in = channel
				def out = channel
			}
		}
		new DefaultProtocol(factory)
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
	 */
	private[Session] class ServiceInvoker(serviceName:String) extends InvocationHandler {
		override def invoke(proxy:Any, method:Method, args:Array[AnyRef]):AnyRef = {

			// メソッド名の参照
			val alias = Option(method.getAnnotation(classOf[Alias])).getOrElse(method.getName)

		}
	}

}