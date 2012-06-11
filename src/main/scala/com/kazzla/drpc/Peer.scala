/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc

import java.nio.channels.SocketChannel
import com.kazzla.drpc.async.Endpoint
import java.nio.ByteBuffer
import annotation.tailrec
import java.util.concurrent.atomic.AtomicLong
import java.lang.reflect.{Proxy, InvocationHandler, Method}
import java.security.cert.Certificate
import com.kazzla.drpc.Node.MetaInfo
import collection.mutable.{Queue, HashMap}
import java.io.ByteArrayOutputStream

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Peer
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Peer private[drpc](node:Node, protocol:Protocol, channel:SocketChannel) {

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	private[drpc] val endpoint = new DRPCEndpoint()

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	private[this] val tasks = new HashMap[Long, Future[Seq[Any]]]()

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	private[this] val sequence = new AtomicLong(0)

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	private[this] val metaInfo = getServiceInterface[MetaInfo]("_", classOf[MetaInfo])

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	lazy val certificate:Certificate = {
		val bin = metaInfo.certificate().toList(0).asInstanceOf[Array[Byte]]
		// TODO 証明書の復元方法
		null
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	def asyncCall(name:String, args:Any*):Future[Seq[Any]] = asyncCall(0, name, args)

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	def asyncCall(timeout:Long, name:String, args:Any*):Future[Seq[Any]] = {
		while(true){
			val id = sequence.getAndIncrement
			tasks.synchronized{
				if(! tasks.contains(id)){
					val future = new Future[Seq[Any]](id)
					tasks += (id -> future)
					endpoint.localCall(Protocol.Call(id, timeout, name, args:_*))
					return future
				}
			}
		}
		null
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	def call(name:String, args:Any*):Seq[Any] = {
		val future = asyncCall(0, name, args:_*)
		future.apply()
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	def getServiceInterface[T](serviceName:String, clazz:Class[T]):T = {
		clazz.cast(Proxy.newProxyInstance(
			Thread.currentThread().getContextClassLoader,
			Array(clazz), new DRPCHandler(serviceName)
		))
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
	// Peer
	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	private[drpc] class DRPCEndpoint extends Endpoint(channel:SocketChannel) {

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
		 */
		private[this] var readBuffer = ByteBuffer.allocate(1024)

		// ====================================================================
		// メソッド
		// ====================================================================
		/**
		 * このピアに結び付けられているサービスのメソッドです。
		 */
		private[this] val methods = new HashMap[String,(Any*)=>Seq[Any]]()

		// ====================================================================
		//
		// ====================================================================
		/**
		 *
		 */
		private[this] val queue = new Queue[ByteBuffer]()

		// ====================================================================
		// 送信データの参照
		// ====================================================================
		/**
		 * 送信用のデータを参照するために呼び出されます。
		 * @return 送信用データ
		 */
		def send():ByteBuffer = {
			synchronized{
				if(queue.size == 1){
					sendDataReady(false)
				}
				queue.dequeue()
			}
		}

		// ====================================================================
		// データ受信の通知
		// ====================================================================
		/**
		 * データ受診時に呼び出されます。パラメータとして渡されたバッファは呼び出し終了後に
		 * クリアされるためサブクラス側で保持できません。
		 */
		def receive(buffer:ByteBuffer, offset:Int, length:Int):Unit = {
			readBuffer.write(buffer.array())
			protocol.unpack(ByteBuffer.wrap(readBuffer.toByteArray)) match {
				case Some(x) =>
				case None =>
			}
			@tailrec
			def procedureCall(){
				protocol.nextCall() match {
					case Some(proc) =>
						if(logger.isTraceEnabled){
							logger.trace("accept rpc: " + proc)
						}
						node.threadPool.execute(new Runnable() {
							def run() { localCall(proc) }
						})
						procedureCall()
					case None => None
				}
			}
			@tailrec
			def callbackResult(){
				protocol.nextResult() match {
					case Some(result) =>
						if(logger.isTraceEnabled){
							logger.trace("accept rpc: " + result)
						}
						localResult(result)
						callbackResult()
					case None => None
				}
			}
			protocol.unpack(buffer)
			procedureCall()
			true
		}

		// ========================================================================
		//
		// ========================================================================
		/**
		 *
		 */
		private[drpc] def remoteCall(proc:Protocol.Call){
			if(logger.isDebugEnabled){
				logger.debug("execute: " + proc)
			}
			queue.enqueue(protocol.pack(proc))
			sendDataReady(true)
		}

		// ========================================================================
		//
		// ========================================================================
		/**
		 *
		 */
		private[drpc] def localCall(proc:Protocol.Call){
			if(logger.isDebugEnabled){
				logger.debug("execute: " + proc)
			}

			val method = getLocalMethod(proc) match {
				case Some(method) => method
				case None =>
					// TODO 呼び出し先が見つからないエラー
					return
			}

			// サービスの呼び出し
			try {
				method(proc.args)
			} catch {
				case ex:Exception =>
				// TODO 呼び出し失敗エラー
			}
		}

		// ========================================================================
		//
		// ========================================================================
		/**
		 *
		 */
		private[drpc] def localResult(result:Protocol.Result){
			if(logger.isDebugEnabled){
				logger.debug("callback: " + result)
			}

			tasks.get(result.id) match {
				case Some(future) =>
					tasks.synchronized{ tasks -= result.id }
					future.set(result.result)
				case None => None
			}
		}

		// ========================================================================
		//
		// ========================================================================
		/**
		 *
		 */
		private[this] def getLocalMethod(proc:Protocol.Call):Option[(Any*)=>Seq[Any]] = methods.synchronized{
			Some(methods.getOrElseUpdate(proc.name, {

					// バインドされているサービス名とメソッドを参照
					val separator = proc.name.lastIndexOf('.')
					val (serviceName, methodName) = if(separator < 0){
						("_", proc.name)
					} else {
						(proc.name.substring(0, separator), proc.name.substring(separator + 1))
					}

					// サービスの参照
					val service = services.getOrElseUpdate(serviceName, {
							node.newService(serviceName, Peer.this) match {
								case Some(service) => service
								case None => return None
							}
					})

					// 呼び出し可能なメソッドを参照
					val callable = try {
						service.getClass.getMethod(methodName, classOf[Seq[Any]])
					} catch {
						case ex:Exception =>
							return None
					}

					// メソッドを登録
					{ args:Seq[Any] => callable.invoke(service, args).asInstanceOf[Seq[Any]] }
			}))
		}

	}

}
