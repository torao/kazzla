/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.irpc.netty

import org.jboss.netty.handler.codec.oneone.OneToOneEncoder
import org.jboss.netty.channel._
import com.kazzla.irpc.{Session, Frame}
import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}
import org.jboss.netty.handler.codec.frame.FrameDecoder
import org.slf4j.LoggerFactory

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Handler
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */

class IrpcChannelPipelineFactory(factory:(Channel)=>Session) extends ChannelPipelineFactory {
	def getPipeline = {
		val pipeline = Channels.pipeline()
		pipeline.addLast("irpc.frame.encoder", new IrpcFrameEncoder())
		pipeline.addLast("irpc.frame.decoder", new IrpcFrameDecoder())
		pipeline.addLast("irpc.service", new IrpcService(factory))
		pipeline
	}
}

class IrpcService(factory:(Channel)=>Session) extends SimpleChannelHandler {
	private[this] val logger = LoggerFactory.getLogger(classOf[IrpcService])

	override def channelConnected(ctx:ChannelHandlerContext, e:ChannelStateEvent):Unit = {
		logger.trace(s"channelConnected(ctx,$e)")
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
		logger.trace(s"channelClosed(ctx,$e)")
		val session = e.getChannel.getAttachment.asInstanceOf[Session]
		session.close()
		super.channelClosed(ctx, e)
	}
	override def messageReceived(ctx:ChannelHandlerContext, e:MessageEvent):Unit = {
		logger.trace(s"messageReceived(ctx,$e)")
		val session = e.getChannel.getAttachment.asInstanceOf[Session]
		session.frameSink.put(e.getMessage.asInstanceOf[Frame])
		super.messageReceived(ctx, e)
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
