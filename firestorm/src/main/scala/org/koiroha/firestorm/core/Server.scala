/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
 */

package org.koiroha.firestorm.core

import java.net.{URL, InetAddress, SocketAddress, InetSocketAddress}
import java.nio.channels.{SocketChannel, ServerSocketChannel, SelectionKey}
import javax.net.ssl.{SSLContext, KeyManagerFactory, SSLServerSocket, SSLServerSocketFactory}
import java.security.KeyStore

/**
 * 指定されたコンテキスト上でサービスを行うサーバを構築します。
 * @param context コンテキスト
 */
class Server(val context:Context) {

	private[this] var selectionKey:Option[SelectionKey] = None
	private[this] var accept:Option[(Server, Endpoint)=>Unit] = None

	private[core] def onAccept(endpoint:Endpoint):Unit = {
		accept.foreach{ _(this, endpoint) }
	}

	/**
	 * このサーバのバインドアドレスおよびポートを参照します。サーバが LISTEN を開始していない場合は None を返します。
	 * @return サーバアドレス
	 */
	def address:Option[InetSocketAddress] = {
		selectionKey match {
			case Some(key) =>
				key.channel().asInstanceOf[ServerSocketChannel].getLocalAddress match {
					case addr:InetSocketAddress => Some(addr)
					case _ => None
				}
			case None => None
		}
	}

	/**
	 * サーバが接続を受け付けたときの処理を指定します。
	 * @param f Accept 時の処理
	 * @return
	 */
	def onAccept(f:(Server, Endpoint)=>Unit):Server = {
		accept = Option(f)
		this
	}

	/**
	 * 指定されたポート上で Listen するサーバを構築します。
	 * @param port
	 */
	def listen(port:Int):Server = listen(new InetSocketAddress(port), -1)

	/**
	 * 指定されたアドレス上で Listen するサーバを構築します。
	 * @param local
	 * @param backlog
	 */
	def listen(local:SocketAddress, backlog:Int):Server = synchronized{
		if (selectionKey.isDefined){
			throw new IllegalStateException("server already listening")
		}
		val channel = createServerSocket(local, backlog)
		selectionKey = Some(context.bind(channel, this))
		context.eachListener { _.onListen(this) }
		this
	}

	/**
	 * 指定されたアドレスにバインドするサーバソケットを構築します。サブクラスでオーバーライドするためのメソッドです。
	 * @param local
	 */
	protected def createServerSocket(local:SocketAddress, backlog:Int):ServerSocketChannel = {
		val channel = ServerSocketChannel.open()
		channel.bind(local, backlog)
		channel
	}

	def close():Unit = synchronized {
		selectionKey.foreach { key =>
			key.interestOps(key.interestOps() & ~ SelectionKey.OP_ACCEPT)
			if (key.selector().isOpen){
				key.selector().wakeup()
			}
			context.eachListener { _.onUnlisten(this) }
			selectionKey = None
		}
	}
}

private[core] case class Cert(keyStore:URL, password:String);

/**
 *
 * @param context コンテキスト
 */
class SSLServer private (override val context:Context, cert:Option[Cert]) extends Server(context){

	def this(context:Context) = this(context, None)
	def this(context:Context, keyStore:URL, password:String) = this(context, Some(Cert(keyStore, password)))

	protected override def createServerSocket(local:SocketAddress, backlog:Int):ServerSocketChannel = {
		val factory = cert match {
			case Some(c) =>
				val keyStore = KeyStore.getInstance("JKS")
				keyStore.load(c.keyStore.openStream(), c.password.toCharArray)

				val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
				keyManagerFactory.init(keyStore, c.password.toCharArray)

				val context = SSLContext.getInstance("TLS")
				context.init(keyManagerFactory.getKeyManagers, null, null)
				context.getServerSocketFactory()
			case None =>
				SSLServerSocketFactory.getDefault
		}
		val serverSocket = factory.createServerSocket()
		serverSocket.getChannel()
	}
}