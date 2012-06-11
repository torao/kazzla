/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc

import async.{RawBuffer, AsyncSocket, AsyncSocketListener}
import java.nio.channels.SocketChannel
import java.nio.ByteBuffer
import annotation.tailrec
import java.util.concurrent.atomic.AtomicLong
import java.lang.reflect.{Proxy, InvocationHandler, Method}
import java.security.cert.Certificate
import com.kazzla.drpc.Node.MetaInfo
import collection.mutable.{Queue, HashMap}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Peer
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Peer private[drpc](node:Node, protocol:Protocol, channel:SocketChannel) {

	// ========================================================================
	// 非同期ソケット
	// ========================================================================
	/**
	 * このピアと通信するための非同期ソケットです。
	 */
	private[this] val socket = new AsyncSocket(channel)

	// ========================================================================
	// 処理中タスクマップ
	// ========================================================================
	/**
	 * このピアに対して処理中のタスクを保持するマップです。タスク番号によって管理されます。
	 */
	private[this] val tasks = new HashMap[Long, Future[Seq[Any]]]()

	// ========================================================================
	// タスク番号生成用シーケンス
	// ========================================================================
	/**
	 * ピアに対するタスク番号を生成するためのシーケンスです。タスク番号はピアからの応答が
	 * どのタスクに対するものかを識別するために使用します。
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
	// 非同期呼び出しの実行
	// ========================================================================
	/**
	 * このピアに対して指定された名前のサービスを非同期で実行します。
	 * @param name 処理名 (サービス名 + "." + メソッド名)
	 * @param args 処理に対する引数
	 */
	def asyncCall(name:String, args:Any*):Future[Seq[Any]] = asyncCall(0, name, args)

	// ========================================================================
	// 非同期呼び出しの実行
	// ========================================================================
	/**
	 * このピアに対して指定された名前のサービスを非同期で実行します。
	 * @param timeout タイムアウト時間 (ミリ秒)
	 * @param name 処理名 (サービス名 + "." + メソッド名)
	 * @param args 処理に対する引数
	 */
	def asyncCall(timeout:Long, name:String, args:Any*):Future[Seq[Any]] = {
		@tailrec
		def exec(){

			// タスク ID を採番してリモート呼び出しを実行
			val id = sequence.getAndIncrement
			tasks.synchronized{
				if(! tasks.contains(id)){
					val future = new Future[Seq[Any]](id)
					tasks += (id -> future)
					endpoint.localCall(Protocol.Call(id, timeout, name, args:_*))
					return future
				}
			}
			exec()
		}
		exec()
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

	// ========================================================================
	// ローカル呼び出し
	// ========================================================================
	/**
	 * 指定された呼び出しをローカルのサービスに対して行います。
	 * @param call 呼び出し
	 */
	private[Peer] def localCall(call:Protocol.Call):Protocol.Result = {
		if(logger.isDebugEnabled){
			logger.debug("execute: " + proc)
		}

		val method = getLocalMethod(call) match {
			case Some(method) => method
			case None =>
				// 要求のあった呼び出し先が見つからない
				return Protocol.Result(call.id, Some("not found: " + call.name))
		}

		// サービスの呼び出し
		val result = try {
			method(proc.args)
		} catch {
			case ex:Throwable =>
				logger.error("uncaught exeption in service" + call, ex)
				return Protocol.Result(call.id, Some(ex.toString()))
		}

		// 処理結果を返す
		Protocol.Result(call.id, None, result:_*)
	}

	// ========================================================================
	// リモート結果参照
	// ========================================================================
	/**
	 * リモートからの RPC 実行結果を受け付けます。
	 */
	private[Peer] def remoteResult(result:Protocol.Result):Unit = {
		if(logger.isDebugEnabled){
			logger.debug("result: " + result)
		}

		tasks.get(result.id) {
			case Some(future) =>
				future.set
			case None =>

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
	private[Peer] class Listener extends AsyncSocketListener {

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
		private[this] val receiveBuffer = new RawBuffer(4 * 1024)

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

		// ========================================================================
		// データの受信通知
		// ========================================================================
		/**
		 * 非同期ソケットがデータを受信した時に呼び出されます。パラメータとして渡されたバッファ
		 * は呼び出し終了後にクリアされるためサブクラス側で保持することはできません。
		 * @param buffer 受信したデータ
		 */
		def asyncDataReceived(buffer:ByteBuffer):Unit = {
			receiveBuffer.enqueue(buffer)
			protocol.unpack(receiveBuffer).foreach {
				case call:Protocol.Call =>
					// TODO スレッドプール化
					socket.send(protocol.pack(localCall(call)))
				case result:Protocol.Result =>
					removeResult(result)
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
