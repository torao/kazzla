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
import collection.mutable.{Queue, HashMap}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Peer
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Peer private[drpc](node:Node, channel:SocketChannel) {

	// ========================================================================
	// 非同期ソケット
	// ========================================================================
	/**
	 * このピアと通信するための非同期ソケットです。
	 */
	private[this] val socket = new AsyncSocket(channel)
	node.context.join(socket)

	// ========================================================================
	// 処理中タスクマップ
	// ========================================================================
	/**
	 * このピアに対して進行中のタスクを保持するマップです。タスク番号によって管理されます。
	 */
	private[this] val tasks = new HashMap[Long, Future]()

	// ========================================================================
	// タスク番号生成用シーケンス
	// ========================================================================
	/**
	 * ピアに対するタスク番号を生成するためのシーケンスです。タスク番号はピアからの応答が
	 * どのタスクに対するものかを識別するために使用します。
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
	def asyncCall(name:String, args:Any*):Future = asyncCall(0, name, args)

	// ========================================================================
	// 非同期呼び出しの実行
	// ========================================================================
	/**
	 * このピアに対して指定された名前のサービスを非同期で実行します。
	 * @param timeout タイムアウト時間 (ミリ秒)
	 * @param name 処理名 (サービス名 + "." + メソッド名)
	 * @param args 処理に対する引数
	 */
	def asyncCall(timeout:Long, name:String, args:Any*):Future = {
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
	private[this] def postAsyncCall(timeout:Long, name:String, args:Any*):Future = {
		// タスク ID を採番してリモート呼び出しを実行
		val id = sequence.getAndIncrement
		tasks.synchronized{
			if(! tasks.contains(id)){
				val future = new Future(id)
				tasks += (id -> future)
				socket.send(node.protocol.pack(Protocol.Call(id, timeout, name, args:_*)))
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
	private[Peer] def localCall(call:Protocol.Call):Protocol.Result = {
		if(logger.isDebugEnabled){
			logger.debug("execute: " + call)
		}

		val callable = getCallable(call) match {
			case Some(callable) => callable
			case None =>
				// 要求のあった呼び出し先が見つからない
				return Protocol.Result(call.id, Some("not found: " + call.name))
		}

		// サービスの呼び出し
		val result = try {
			callable(call.args)
		} catch {
			case ex:Throwable =>
				logger.error("uncaught exeption in service" + call, ex)
				return Protocol.Result(call.id, Some(ex.toString))
		}

		// 処理結果を返す
		Protocol.Result(call.id, None, result:_*)
	}

	// ========================================================================
	// リモート結果参照
	// ========================================================================
	/**
	 * 指定された RPC の実行結果を受け付けます。
	 * @param result RPC 実行結果
	 */
	private[Peer] def remoteResult(result:Protocol.Result):Unit = {
		if(logger.isDebugEnabled){
			logger.debug("result: " + result)
		}

		tasks.get(result.id) match {
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
	private[this] def getCallable(proc:Protocol.Call):Option[(Any*)=>Seq[Any]] = callables.synchronized{
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
			node.protocol.unpack(receiveBuffer).foreach {
				case call:Protocol.Call =>
					scala.actors.Actor.actor{
						node.protocol.pack(localCall(call))
					}
				case result:Protocol.Result =>
					remoteResult(result)
			}
		}

	}

}
