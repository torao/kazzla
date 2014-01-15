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
import java.io._
import java.net.InetSocketAddress
import java.security.KeyStore
import java.sql.{DriverManager, Connection}
import java.util.UUID
import java.util.concurrent.{Executors, LinkedBlockingQueue, TimeUnit, ThreadPoolExecutor}
import java.util.logging.Logger
import javax.sql.DataSource
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.channel._
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.ssl.SslHandler
import org.slf4j.LoggerFactory
import scala.Some
import scala.util.Success

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Server
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Server(docroot:File, domain:Domain) {

	import Server._
	import scala.concurrent.ExecutionContext.Implicits.global

	private[this] val useHttps = false
	private[this] val service = new Service(docroot, domain)
	private[this] var httpServer:Option[Channel] = None
	private[this] var node:Option[Node] = None

	private[this] val threads
		= new ThreadPoolExecutor(10, 10, 60, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable]())

	private[this] val sslContext = {
		val jks = KeyStore.getInstance("JKS")
		usingInput(new File("service.domain/domain.jks")){ in => jks.load(in, "000000".toCharArray) }
		jks.getSSLContext("000000".toCharArray)
	}

	def start():Unit = {

		val cf = new NioServerSocketChannelFactory(
			Executors.newCachedThreadPool(),
			Executors.newCachedThreadPool()
		)

		val bootstrap = new ServerBootstrap(cf)
		bootstrap.setPipelineFactory(new ChannelPipelineFactory {
			def getPipeline: ChannelPipeline = {
				val pipeline = Channels.pipeline()
				if(useHttps){
					val engine = sslContext.createSSLEngine()
					engine.setUseClientMode(false)
					pipeline.addLast("ssl", new SslHandler(engine))
				}
				pipeline.addLast("decoder", new HttpRequestDecoder())
				pipeline.addLast("encoder", new HttpResponseEncoder())
				pipeline.addLast("deflator", new HttpContentCompressor())
				pipeline.addLast("handler", new HttpRequestHandler())
				pipeline
			}
		})

		httpServer = Some(bootstrap.bind(new InetSocketAddress(8088)))
		logger.info(s"http server start on: ${8088}")

		node = Some(Node("domain")
			.bridge(Netty)
			.codec(MsgPackCodec)
			.runOn(threads)
			.serve(service)
			.build())
		node.foreach{
			_.listen(new InetSocketAddress("localhost", 8089), Some(sslContext)){ session =>
				session.onClosed ++ { s =>
					Option(s.sslSession.getValue("sessionId")) match {
						case Some(sessionId:UUID) => domain.closeNodeSession(sessionId)
						case _ => None
					}
				}
				session.wire.tls.onComplete {
					case Success(Some(sslSession)) =>
						sslSession.putValue("sessionId", domain.newUUID())
					case _ => session.close()
				}
			}
		}
		logger.info(s"node server start on: ${8089}")
	}

	def stop():Unit = {
		httpServer.foreach{
			_.close().awaitUninterruptibly(10 * 1000)
		}
		httpServer = None
		node.foreach{ _.shutdown() }
		node = None
	}

	private class HttpRequestHandler extends SimpleChannelUpstreamHandler {
		override def messageReceived (ctx: ChannelHandlerContext, e: MessageEvent) = e.getMessage match {
			case request:HttpRequest =>
				ctx.getChannel.write( try {
					val r = service(request)
					r.setHeader("Connection", "close")
					r
				} catch {
					case ex:Throwable =>
						httpErrorResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR)
				})
			case _ =>
				super.messageReceived(ctx, e)
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

	def httpErrorResponse(status:HttpResponseStatus):HttpResponse = {
		httpErrorResponse(status, status.getReasonPhrase)
	}

	def httpErrorResponse(status:HttpResponseStatus, message:String):HttpResponse = {
		val res = new DefaultHttpResponse(HttpVersion.HTTP_1_1, status)
		res.setHeader("Cache-Control", "no-cache")
		res.setHeader("Connection", "close")
		res.setHeader("Content-Type", "text/plain; charset=UTF-8")
		res.setContent(ChannelBuffers.copiedBuffer(s"${status.getCode} ${status.getReasonPhrase}".getBytes("UTF-8")))
		res
	}

}
