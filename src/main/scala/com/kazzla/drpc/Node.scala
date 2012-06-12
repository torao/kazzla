/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc

import com.kazzla.drpc.async.AsyncSocketContext
import java.net.Socket
import java.util.concurrent.Executor
import java.util.{TimerTask, Timer}
import collection.mutable.HashMap
import java.security.cert.Certificate
import com.kazzla.drpc.Node.MetaInfo

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Node
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Node(val protocol:Protocol, val threadPool:Executor, cert:Certificate) {

	// ========================================================================
	// コンテキスト
	// ========================================================================
	/**
	 * このノード上で非同期 I/O 処理を行うコンテキストです。
	 */
	private[drpc] val context = new AsyncSocketContext()

	// ========================================================================
	// サービス
	// ========================================================================
	/**
	 * このノードにバインドされているサービスです。
	 */
	private[this] val factories = new HashMap[String,(String,Peer)=>Service]()
	factories += ("_" -> { (name:String, peer:Peer) => new MetaInfoImpl(name, peer) })

	// ========================================================================
	// プロパティ
	// ========================================================================
	/**
	 * このノードのプロパティです。
	 */
	private var properties = Map[String,String]()

	// ========================================================================
	// シャットダウンフラグ
	// ========================================================================
	/**
	 * このノードがシャットダウン中かを表すフラグです。
	 */
	@volatile
	private var closing = false

	// ========================================================================
	// タイムアウトタスク
	// ========================================================================
	/**
	 * 実行中のタスクです。
	 */
	private val tasks = new HashMap[Protocol.Call,Task]()

	// ========================================================================
	// コンテキスト
	// ========================================================================
	/**
	 * このノード上で非同期 I/O 処理を行うコンテキストです。
	 */
	private val timer = new Timer("RPCTimeoutWatchdog", true)

	timer.scheduleAtFixedRate(new TimerTask {
		def run() {
			val now = System.currentTimeMillis()
			tasks.synchronized {
				tasks.keys.foreach{ proc =>
					val task = tasks(proc)
					if(task.start + proc.timeout < now){
						task.thread.interrupt()
						tasks.remove(proc)
					}
				}
			}
		}
	}, 3000, 3000)

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	private[drpc] def addTimeout(proc:Protocol.Call, thread:Thread){
		tasks.synchronized {
			tasks += (proc -> new Task(System.currentTimeMillis(), thread))
		}
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	private[drpc] def removeTimeout(proc:Protocol.Call){
		tasks.synchronized {
			tasks -= proc
		}
	}

	// ========================================================================
	// ノードのシャットダウン
	// ========================================================================
	/**
	 * このノードのサービスをシャットダウンします。
	 */
	def shutdown(){
		// TODO コンテキストのクローズ
	}

	// ========================================================================
	// サービスのバインド
	// ========================================================================
	/**
	 * このノードにサービスをバインドします。既に同じ名前に対してサービスがバインドされて
	 * いる場合は上書きされます。
	 * @param name サービス名
	 * @param service バインドするサービス
	 */
	def bind(name:String, service:(String,Peer)=>Service){
		factories += (name -> service)
	}

	// ========================================================================
	// サービスのアンバインド
	// ========================================================================
	/**
	 * 指定された名前に対するサービスをこのノードから切り離します。名前に対するサービスが
	 * バインドされていない場合は何も起きません。
	 * @param name 切り離すサービス名
	 */
	def unbind(name:String){
		factories -= name
	}

	// ========================================================================
	// プロパティの設定
	// ========================================================================
	/**
	 * このノードにプロパティを設定します。
	 * @param name プロパティ名
	 * @param value プロパティ値
	 */
	def setProperty(name:String, value:String){
		properties += (name -> value)
	}

	// ========================================================================
	// ソケットのアタッチ
	// ========================================================================
	/**
	 * 指定されたソケットをこのノードにアタッチします。
	 * @param socket ノードにアタッチするソケット
	 * @return ピア (通信相手) を表すインスタンス
	 */
	def attach(socket:Socket):Peer = {
		new Peer(this, socket.getChannel)
	}

	// ========================================================================
	// サービスの参照
	// ========================================================================
	/**
	 * 指定されたピアに対してこのノードが提供するサービスを参照します。名前に対するサービス
	 * が定義されていない場合やピアにサービスを提供できない場合は None を返します。
	 * @param name サービス名
	 * @param peer ピア
	 * @return サービス
	 */
	private[drpc] def newService(name:String, peer:Peer):Option[Service] = {
		factories.get(name) match {
			case Some(factory) => Some(factory(name, peer))
			case None => None
		}
	}

	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// MetaInfo
	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	private[drpc] class MetaInfoImpl(name:String, peer:Peer) extends Service(name, peer) with Node.MetaInfo {

		def version(args:Any*):Seq[Any] = List(1, 0)

		def ping(args:Any*):Seq[Any] = List("ok")

		// ========================================================================
		// ノード情報の参照
		// ========================================================================
		/**
		 * このノードの情報を参照します。
		 */
		def lookup(args:Any*):Seq[Any] = {
			List(factories.keys, properties)
		}

		// ========================================================================
		// 証明書の参照
		// ========================================================================
		/**
		 * このノードの証明書を参照します。
		 */
		def certificate(args:Any*):Seq[Any] = {
			List(cert.getEncoded)
		}

	}

	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Peer
	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	private case class Task(start:Long, thread:Thread)

}

object Node {

	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// MetaInfo
	// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	trait MetaInfo {

		def version(args:Any*):Seq[Any]

		def ping(args:Any*):Seq[Any]

		// ========================================================================
		// ノード情報の参照
		// ========================================================================
		/**
		 * このノードの情報を参照します。
		 */
		def lookup(args:Any*):Seq[Any]

		// ========================================================================
		// 証明書の参照
		// ========================================================================
		/**
		 * このノードの証明書を参照します。
		 */
		def certificate(args:Any*):Seq[Any]

	}

}