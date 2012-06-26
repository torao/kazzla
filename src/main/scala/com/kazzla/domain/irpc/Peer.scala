/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.irpc

import async.{PipelineGroup, Pipeline, RawBuffer}
import java.nio.ByteBuffer
import annotation.tailrec
import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}
import java.lang.reflect.{Proxy, InvocationHandler, Method}
import java.security.cert.{X509Certificate, Certificate}
import collection.mutable.{Queue, HashMap}
import scala.Some
import java.net.InetSocketAddress
import javax.net.SocketFactory

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Peer
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 * @param address このピアの接続先
 */
class Peer private[irpc](address:Peer.Address, group:PipelineGroup, myCert:X509Certificate) {

	// ========================================================================
	// パイプライン
	// ========================================================================
	/**
	 * このピアと通信するためのパイプラインです。
	 */
	private[this] var _pipeline:Option[Pipeline] = None

	// ========================================================================
	// 証明書
	// ========================================================================
	/**
	 * 現在接続中のピアの証明書です。
	 */
	private[this] var _certificate:Option[Certificate] = None

	// ========================================================================
	// 証明書
	// ========================================================================
	/**
	 * 現在接続中のピアの証明書です。
	 */
	def certificate:Option[Certificate] = _certificate

	// ========================================================================
	// パイプライン
	// ========================================================================
	/**
	 * このピアと通信するためのパイプラインです。
	 */
	private[this] def pipeline:Pipeline = {
		_pipeline match {
			case Some(p) =>
				if(p.isClosed()){
					destroy()
					pipeline
				} else {
					p
				}
			case None =>
				val p = address.newPipeline()
				group.begin(p)
				_pipeline = Some(p)
				p
		}

	}

	// ========================================================================
	// パイプライン
	// ========================================================================
	/**
	 * このピアと通信するためのパイプラインです。
	 */
	private[this] def init(){

		// パイプラインの構築
		assert(_pipeline.isEmpty)
		val pipeline = address.newPipeline()
		group.begin(pipeline)
		_pipeline = Some(pipeline)

		// ヘッダの送出
		val header = "Codec: %s\r\nProtocol: %s\r\n\r\n".format().getBytes("UTF-8")

		// メタ情報サービスとバインド
	}

	// ========================================================================
	// パイプライン
	// ========================================================================
	/**
	 * このピアと通信するためのパイプラインです。
	 */
	private[this] def destroy(){
		if(_pipeline.isDefined){
			group.exit(_pipeline.get)
		}
		_pipeline = None
		_certificate = None
	}

	// ========================================================================
	// 処理中パイプ
	// ========================================================================
	/**
	 * このピア上で進行中の処理に対するパイプを保持するマップです。パイプIDによって管理さ
	 * れています。
	 */
	private[this] val pipes = new HashMap[Long, Pipe]()

	// ========================================================================
	// パイプID生成用シーケンス
	// ========================================================================
	/**
	 * このピア上でのパイプIDを生成するためのシーケンスです。パイプIDはピアからの応答がどの
	 * パイプに対するものかを識別するために使用します。
	 */
	private[this] val sequence = new AtomicLong(0)

	// ========================================================================
	// サービス
	// ========================================================================
	/**
	 * このピアに提供されているサービスです。
	 */
	private[this] val services = new HashMap[String, Service]()

	// ========================================================================
	// 証明書
	// ========================================================================
	/**
	 * このピアの証明書です。
	 */
	lazy val certificate:Certificate = {
		val bin = metaInfo.certificate().toList(0).asInstanceOf[Array[Byte]]
		// TODO 証明書の復元方法
		null
	}

	// ========================================================================
	// メソッド
	// ========================================================================
	/**
	 * この端点が提供しているサービスのメソッドです。
	 */
	private[this] val callables = new HashMap[String,(Any*)=>Seq[Any]]()

	// ========================================================================
	// メタ情報
	// ========================================================================
	/**
	 * ピアノメタ情報を取得するためのインスタンスです。
	 */
	private[this] lazy val metaInfo = getInterface[Node.MetaInfo]("_", classOf[Node.MetaInfo])

	// ========================================================================
	// 非同期呼び出しの実行
	// ========================================================================
	/**
	 * このピアに対して指定された名前のサービスを非同期で実行します。
	 * @param name 処理名 (サービス名 + "." + メソッド名)
	 * @param args 処理に対する引数
	 */
	def asyncCall(name:String, args:Any*):Pipe = asyncCall(0, name, args)

	// ========================================================================
	// 非同期呼び出しの実行
	// ========================================================================
	/**
	 * このピアに対して指定された名前のサービスを非同期で実行します。
	 * @param timeout タイムアウト時間 (ミリ秒)
	 * @param name 処理名 (サービス名 + "." + メソッド名)
	 * @param args 処理に対する引数
	 */
	def asyncCall(timeout:Long, name:String, args:Any*):Pipe = {
		postAsyncCall(timeout, name, args)
	}

	// ========================================================================
	// 非同期呼び出しの実行
	// ========================================================================
	/**
	 * このピアに対して指定された名前のサービスを非同期で実行します。
	 * @param timeout タイムアウト時間 (ミリ秒)
	 * @param name 処理名 (サービス名 + "." + メソッド名)
	 * @param args 処理に対する引数
	 */
	@tailrec
	private[this] def postAsyncCall(timeout:Long, name:String, args:Any*):Pipe = {
		// ユニークなパイプIDを採番してリモート呼び出しを実行
		val id = sequence.getAndIncrement
		pipes.synchronized{
			if(! pipes.contains(id)){
				val future = new PipeImpl(id)
				pipes += (id -> future)
				pipeline.write(node.codec.pack(Call(id, timeout, name, args:_*)))
				return future
			}
		}
		postAsyncCall(timeout, name, args)
	}

	// ========================================================================
	// 同期呼び出しの実行
	// ========================================================================
	/**
	 * このピアに対して指定されたリモート処理を実行します。
	 */
	def call(name:String, args:Any*):Seq[Any] = {
		val future = asyncCall(0, name, args:_*)
		future.apply()
	}

	// ========================================================================
	// インターフェースの参照
	// ========================================================================
	/**
	 * このピアが提供するサービスを指定されたインターフェースとバインドします。
	 * @param serviceName サービス名
	 * @param clazz サービスのインターフェース
	 */
	def getInterface[T](serviceName:String, clazz:Class[T]):T = {
		clazz.cast(Proxy.newProxyInstance(
			Thread.currentThread().getContextClassLoader,
			Array(clazz), new DRPCHandler(serviceName)
		))
	}

	// ========================================================================
	// ローカル呼び出し
	// ========================================================================
	/**
	 * 指定された呼び出しをローカルのサービスに対して行います。
	 * @param call 呼び出し
	 */
	private[Peer] def localCall(call:Call):Result = {
		if(logger.isDebugEnabled){
			logger.debug("execute: " + call)
		}

		val callable = getCallable(call) match {
			case Some(callable) => callable
			case None =>
				// 要求のあった呼び出し先が見つからない
				return Result(call.id, Some("not found: " + call.name))
		}

		// サービスの呼び出し
		val result = try {
			callable(call.args)
		} catch {
			case ex:Throwable =>
				logger.error("uncaught exeption in service" + call, ex)
				return Result(call.id, Some(ex.toString))
		}

		// 処理結果を返す
		Result(call.id, None, result:_*)
	}

	// ========================================================================
	// リモート結果参照
	// ========================================================================
	/**
	 * 指定された RPC の実行結果を受け付けます。
	 * @param result RPC 実行結果
	 */
	private[Peer] def remoteResult(result:Result):Unit = {
		if(logger.isDebugEnabled){
			logger.debug("result: " + result)
		}

		pipes.get(result.id) match {
			case Some(future) =>
				future.set(result)
			case None => None
		}
	}

	// ========================================================================
	// ローカルメソッドの参照
	// ========================================================================
	/**
	 *
	 */
	private[this] def getCallable(proc:Call):Option[(Any*)=>Seq[Any]] = callables.synchronized{
		callables.get(proc.name) match {
			case Some(callable) => Some(callable)
			case None =>

				// バインドされているサービス名とメソッドを参照
				val separator = proc.name.lastIndexOf('.')
				val (serviceName, methodName) = if(separator < 0){
					("_", proc.name)
				} else {
					(proc.name.substring(0, separator), proc.name.substring(separator + 1))
				}

				// サービスの参照
				val service:Service = services.getOrElseUpdate(serviceName, {
					node.newService(serviceName, Peer.this) match {
						case Some(service) => service
						case None => return None
					}
				})

				// 呼び出し可能なメソッドを参照
				val callable:(Any*)=>Seq[Any] = try {
					val method:Method = service.getClass.getMethod(methodName, classOf[Seq[Any]])
					val callable = { args:Seq[Any] => method.invoke(service, args).asInstanceOf[Seq[Any]] }
					callable
				} catch {
					case ex:Exception =>
						logger.warn(ex.toString + ": " + methodName + "(Any*)=>Seq[Any]")
						return None
				}

				// メソッドを登録
				callables += (proc.name -> callable)
				Some(callable)
		}
	}

	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Peer
	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	private[this] class DRPCHandler(serviceName:String) extends InvocationHandler {
		def invoke(proxy:AnyRef, method:Method, args:Array[AnyRef]):AnyRef = {
			call(serviceName + "." + method.getName, args:_*)
		}
	}

	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Listener
	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	private[Peer] class Listener {

		// ====================================================================
		// サービス
		// ====================================================================
		/**
		 * このピアに結び付けられているサービスです。
		 */
		private[this] val services = new HashMap[String,Service]()

		// ====================================================================
		// 受信バッファ
		// ====================================================================
		/**
		 * 受信データのバッファです。
		 * TODO バッファの名前
		 */
		private[this] val receiveBuffer = new RawBuffer("peer", 4 * 1024)

		// ====================================================================
		//
		// ====================================================================
		/**
		 *
		 */
		private[this] val queue = new Queue[ByteBuffer]()

		// ====================================================================
		// データの受信通知
		// ====================================================================
		/**
		 * 非同期ソケットがデータを受信した時に呼び出されます。パラメータとして渡されたバッファ
		 * は呼び出し終了後にクリアされるためサブクラス側で保持することはできません。
		 * @param buffer 受信したデータ
		 */
		def asyncDataReceived(buffer:ByteBuffer):Unit = {
			receiveBuffer.enqueue(buffer)
			node.codec.unpack(receiveBuffer).foreach {
				case call:Call =>
					scala.actors.Actor.actor{
						node.codec.pack(localCall(call))
					}
				case result:Result =>
					remoteResult(result)
			}
		}

	}

}

object Peer {

	class ConnectException(msg:String, ex:Seq[Throwable]) extends KazzlaException(msg, null, ex)

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Address
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	abstract class Address {

		// ======================================================================
		// パイプラインの構築
		// ======================================================================
		/**
		 * このアドレスに対して新しいパイプラインを構築します。
		 * @return パイプライン
		 */
		def newPipeline(sink:(ByteBuffer)=>Unit):Pipeline

	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// SocketAddress
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	class SocketAddress(factory:SocketFactory, _addr:SocketAddress, _addrs:SocketAddress*) extends Address {
		private[this] val addresses = scala.util.Random.shuffle(_addr :: _addres.toList)
		private[this] val index = new AtomicInteger(0)

		var connectionTimeout = 0
		var keepAlive = false

		// ======================================================================
		// パイプラインの構築
		// ======================================================================
		/**
		 * このアドレスに対して新しいパイプラインを構築します。
		 * @return パイプライン
		 */
		def newPipeline(sink:(ByteBuffer)=>Unit):Pipeline = {
			var exceptions = List[Throwable]()
			do {

				// 接続先のアドレスを参照
				val addr = addersses(scala.math.abs(index.getAndIncrement) % addresses.size)

				// パイプラインの作成
				try {
					val socket = factory.createSocket()
					socket.connect(addr, connectionTimeout)
					socket.setKeepAlive(keepAlive)
					return Pipeline.newPipeline(socket.getChannel)(sink)
				} catch {
					case ex:Exception => exceptions ::= ex
				}
			} while(exceptions.size < addresses.size)

			// すべてのアドレスに対して接続に失敗した場合
			throw new ConnectException("connection failure: " + addresses.map{ addr =>
				addr match {
					case i:InetSocketAddress => i.getHostName + ":" + i.getPort
					case e => e.toString
				}
			}.mkString("[", ",", "]"), exceptions)
		}

	}
}