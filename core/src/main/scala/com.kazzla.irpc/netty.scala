/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.irpc

import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.frame.FrameDecoder
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder
import org.slf4j.LoggerFactory
import com.kazzla.core.io._
import scala.Some
import java.io.{FileInputStream, BufferedInputStream}
import javax.net.ssl.{TrustManagerFactory, SSLContext, KeyManagerFactory}
import org.jboss.netty.handler.ssl.SslHandler
import java.util.concurrent.atomic.AtomicBoolean

package object netty {

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Handler
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */

	class IrpcChannelPipelineFactory(factory:(Channel)=>Session, client:Boolean = false, sslContext:Option[SSLContext] = None) extends ChannelPipelineFactory {
		private[this] val logger = LoggerFactory.getLogger(classOf[IrpcChannelPipelineFactory])
		def this(session:Session) = this({ _ => session })
		def this(session:Session, sslContext:SSLContext) = this({ _ => session }, true, Some(sslContext))
		def this(f:(Channel)=>Session, sslContext:SSLContext) = this(f, false, Some(sslContext))
		def getPipeline = {
			val pipeline = Channels.pipeline()
			sslContext.foreach{ s =>
				val engine = s.createSSLEngine()
				engine.setUseClientMode(client)
				engine.setNeedClientAuth(true)
				if(logger.isTraceEnabled){
					logger.trace(s"CipherSuites: ${engine.getEnabledCipherSuites.mkString(",")}")
					logger.trace(s"Protocols: ${engine.getEnabledProtocols.mkString(",")}")
				}
				pipeline.addLast("tls", new SslHandler(engine))
			}
			pipeline.addLast("irpc.frame.encoder", new IrpcFrameEncoder())
			pipeline.addLast("irpc.frame.decoder", new IrpcFrameDecoder())
			pipeline.addLast("irpc.service", new IrpcService(factory))
			pipeline
		}
	}

	class IrpcService(factory:(Channel)=>Session) extends SimpleChannelHandler {
		private[this] val logger = LoggerFactory.getLogger(classOf[IrpcService])

		override def channelConnected(ctx:ChannelHandlerContext, e:ChannelStateEvent):Unit = {
			val session = factory(e.getChannel)
			session.frameSource.onPut {
				val frame = session.frameSource.take().get
				val future = Channels.future(e.getChannel)
				ctx.sendDownstream(new DownstreamMessageEvent(ctx.getChannel, future, frame, e.getChannel.getRemoteAddress))
			}
			e.getChannel.setAttachment(session)
			super.channelConnected(ctx, e)
		}
		override def channelClosed(ctx:ChannelHandlerContext, e:ChannelStateEvent):Unit = {
			val session = e.getChannel.getAttachment.asInstanceOf[Session]
			session.close()
			super.channelClosed(ctx, e)
		}
		private val authed = new Once()
		override def messageReceived(ctx:ChannelHandlerContext, e:MessageEvent):Unit = {
			authed{
				Option(e.getChannel.getPipeline.get(classOf[SslHandler])).foreach { s =>
					val session = s.getEngine.getSession
					if(logger.isTraceEnabled){
						logger.trace(s"CipherSuite   : ${session.getCipherSuite}")
						logger.trace(s"LocalPrincipal: ${session.getLocalPrincipal}")
						logger.trace(s"PeerHost      : ${session.getPeerHost}")
						logger.trace(s"PeerPort      : ${session.getPeerPort}")
						logger.trace(s"PeerPrincipal : ${session.getPeerPrincipal}")
					}
				}
			}

			e.getChannel.getAttachment match {
				case s:Session =>
					s.frameSink.put(e.getMessage.asInstanceOf[Frame])
				case _ => None
			}
			super.messageReceived(ctx, e)
		}
		override def exceptionCaught(ctx:ChannelHandlerContext, e:ExceptionEvent){
			logger.debug("exception caught", e.getCause)
			e.getChannel.getAttachment match {
				case s:Session => s.close()
				case _ => None
			}
		}
	}

	class IrpcFrameEncoder extends OneToOneEncoder {
		def encode(ctx:ChannelHandlerContext, channel:Channel, msg:Any):AnyRef = msg match {
			case packet:Frame =>
				val buffer = Frame.encode(packet)
				ChannelBuffers.copiedBuffer(buffer)
			case unknown:AnyRef => unknown
		}
	}

	class IrpcFrameDecoder extends FrameDecoder {
		def decode(ctx:ChannelHandlerContext, channel:Channel, b:ChannelBuffer):AnyRef = {
			val buffer = b.toByteBuffer
			Frame.decode(buffer) match {
				case Some(frame) =>
					b.skipBytes(buffer.position())
					frame
				case None =>
					null
			}
		}
	}

	def getSSLContext(file:String, ksPassword:String, pkPassword:String):SSLContext = {
		import java.security._
		val algorithm = Option(Security.getProperty("ssl.KeyManagerFactory.algorithm")).getOrElse("SunX509")
		val keyStore = KeyStore.getInstance("JKS")
		using(new BufferedInputStream(new FileInputStream(file))){ in =>
			keyStore.load(in, ksPassword.toCharArray)

			val kmf = KeyManagerFactory.getInstance(algorithm)
			kmf.init(keyStore, pkPassword.toCharArray)

			/*
			val tmf = TrustManagerFactory.getInstance(algorithm)
			tmf.init(keyStore)
			*/

			val context = SSLContext.getInstance("TLS")
			context.init(kmf.getKeyManagers, /* tmf.getTrustManagers */ null, null)
			context
		}
	}

	class Once {
		private val first = new AtomicBoolean(true)
		def apply(f: =>Unit) = if(first.compareAndSet(true, false)){
			f
		}
	}

}
