/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service.domain

import com.kazzla.asterisk.Node
import com.kazzla.asterisk.codec.MsgPackCodec
import com.kazzla.asterisk.netty.Netty
import com.kazzla.core.cert._
import com.kazzla.core.io._
import com.kazzla.service.Context
import com.kazzla.service.storage.StorageServiceImpl
import com.kazzla.storage.RegionNode
import io.netty.bootstrap.ServerBootstrap
import io.netty.buffer.Unpooled
import io.netty.channel._
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http._
import io.netty.handler.ssl.SslHandler
import java.io._
import java.net.InetSocketAddress
import java.security.KeyStore
import java.sql.{DriverManager, Connection}
import java.util.concurrent.{Executors, TimeUnit}
import java.util.logging.Logger
import java.util.{TimerTask, Timer, UUID}
import javax.sql.DataSource
import org.slf4j.LoggerFactory
import scala.Some
import scala.concurrent.ExecutionContext
import scala.util.{Try, Success}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Server
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Server(docroot:File, domain:Domain) {

	import Server._
	import scala.concurrent.ExecutionContext.Implicits.global

	private[this] val port = 8088

	private[this] val useHttps = false
	private[this] val service = new Service(docroot, domain)
	private[this] var node:Option[Node] = None
	private[this] var eventLoop:Option[EventLoopGroup] = None

	private[this] val timer = new Timer("server", true)
	private[this] val pinger = new TimerTask {
		override def run(): Unit = node.foreach { n =>
			n.sessions.foreach{ session =>
				val remote = session.bind(classOf[RegionNode])
				Try(remote.sync(System.currentTimeMillis()))
			}
		}
	}
	timer.scheduleAtFixedRate(pinger, 60 * 1000, 60 * 1000)

	private[this] val sslContext = {
		val jks = KeyStore.getInstance("JKS")
		usingInput(new File("service.domain/domain.jks")){ in => jks.load(in, "000000".toCharArray) }
		jks.getSSLContext("000000".toCharArray)
	}

	private[this] implicit val threads = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
	private[this] implicit val context = new Context(domain.dataSource, threads)

	def start():Unit = {
		assert(eventLoop.isEmpty)
		eventLoop = Some(new NioEventLoopGroup())
		val bootstrap = new ServerBootstrap()
		bootstrap
			.group(eventLoop.get)
			.localAddress(new InetSocketAddress(port))
			.channel(classOf[NioServerSocketChannel])
			.option(ChannelOption.SO_BACKLOG, java.lang.Integer.valueOf(100))
			.childOption(ChannelOption.TCP_NODELAY, java.lang.Boolean.TRUE)
			.childHandler(new ChannelInitializer[SocketChannel](){
				def initChannel(ch:SocketChannel) = {
					val pipeline = ch.pipeline()
					if(useHttps){
						val engine = sslContext.createSSLEngine()
						engine.setUseClientMode(false)
						pipeline.addLast("ssl", new SslHandler(engine))
					}
					pipeline.addLast("http", new HttpServerCodec())
					pipeline.addLast("aggregator", new HttpObjectAggregator(512 * 1024))
					pipeline.addLast("deflator", new HttpContentCompressor())
					pipeline.addLast("handler", new HttpRequestHandler())
				}
			})

		bootstrap.bind().sync()

		logger.info(s"http server start on: $port")

		node = Some(Node("domain")
			.bridge(Netty)
			.codec(MsgPackCodec)
			.serve(service)
			.build())
		node.foreach{
			_.listen(new InetSocketAddress("localhost", 8089), Some(sslContext)){ session =>
				val storage = new StorageServiceImpl(domain)
				session.setAttribute("storage", storage)
				session.onClosed ++ { s =>
					Option(s.sslSession.getValue("sessionId")) match {
						case Some(sessionId:UUID) => domain.closeNodeSession(sessionId)
						case _ => None
					}
				}
				session.wire.tls.onComplete {
					case Success(Some(sslSession)) =>
						sslSession.putValue("sessionId", domain.newUUID())
						storage.startup(session)
					case _ => session.close()
				}
			}
		}
		logger.info(s"node server start on: ${8089}")
	}

	def stop():Unit = {
		eventLoop.foreach{ _.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS) }
		eventLoop = None
		node.foreach{ _.shutdown() }
		node = None
		threads.shutdown()
	}

	private class HttpRequestHandler extends SimpleChannelInboundHandler[HttpRequest] {
		protected def channelRead0(ctx:ChannelHandlerContext, request:HttpRequest) = try {
			val response = service.http(request)
			response.headers().set("Connection", "close")
			ctx.channel().write(response)
			ctx.channel().flush()
			ctx.channel().close()
		} catch {
			case ex:Throwable =>
				httpErrorResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR)
		}
	}

}

object Server {
	private[Server] val logger = LoggerFactory.getLogger(classOf[Server])

	// ==============================================================================================
	// ドメインサーバの起動
	// ==============================================================================================
	/**
	 * ドメインサーバを起動します。
	 */
	def main(args:Array[String]):Unit = {
		val docroot = new File("service.domain/docroot")
		val ca = new Domain.CA(new File("service.domain/ca/demoCA"))
		val domain = new Domain("com.kazzla", ca, new DataSource {
			def setLogWriter(out: PrintWriter): Unit = ???
			def getLoginTimeout: Int = ???
			def setLoginTimeout(seconds: Int): Unit = ???
			def unwrap[T](iface: Class[T]): T = ???
			def isWrapperFor(iface: Class[_]): Boolean = ???
			def getParentLogger: Logger = ???
			def getLogWriter: PrintWriter = ???
			def getConnection(username: String, password: String): Connection = ???
			def getConnection: Connection = {
				DriverManager.getConnection("jdbc:mysql://localhost/kazzla_development", "root", "")
			}
		})
		val server = new Server(docroot, domain)
		server.start()
		// TODO JMX で終了が呼び出されるまで待機
	}

	def httpErrorResponse(status:HttpResponseStatus):FullHttpResponse = {
		httpErrorResponse(status, status.reasonPhrase)
	}

	def httpErrorResponse(status:HttpResponseStatus, message:String):FullHttpResponse = {
		val buf = Unpooled.copiedBuffer(s"${status.code()} ${status.reasonPhrase()}".getBytes("UTF-8"))
		val res = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, status, buf)
		res.headers().set("Cache-Control", "no-cache")
		res.headers().set("Connection", "close")
		res.headers().set("Content-Type", "text/plain; charset=UTF-8")
		res
	}

}
