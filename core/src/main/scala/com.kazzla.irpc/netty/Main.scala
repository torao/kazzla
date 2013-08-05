/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.irpc.netty

import org.jboss.netty.channel.socket.nio.{NioClientSocketChannelFactory, NioServerSocketChannelFactory}
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.{ClientBootstrap, ServerBootstrap}
import org.jboss.netty.channel.{Channel, ChannelPipeline, ChannelPipelineFactory}
import com.kazzla.irpc.{Export, Session}
import com.kazzla.core.io._
import java.net.InetSocketAddress

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Main
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object Main {
	def main(args:Array[String]):Unit = {
		val executor = Executors.newCachedThreadPool()
		def sessionFactory(ch:Channel):Session = {
			new Session(getName(ch.getRemoteAddress), true, executor, new GreetingImpl())
		}

		val server = {
			val channelFactory = new NioServerSocketChannelFactory()
			val bootstrap = new ServerBootstrap(channelFactory)
			bootstrap.setPipelineFactory(new IrpcChannelPipelineFactory(sessionFactory))
			bootstrap.bind(new InetSocketAddress(7777))
			bootstrap
		}

		val session = new Session("client", false, executor, new GreetingImpl());
		val client = {
			val channelFactory = new NioClientSocketChannelFactory()
			val bootstrap = new ClientBootstrap(channelFactory)
			bootstrap.setPipelineFactory(new IrpcChannelPipelineFactory({_ => session }))
			bootstrap.connect(new InetSocketAddress(7777))
			bootstrap
		}

		val g = session.getRemoteInterface(classOf[Greeting])
		val s = System.nanoTime()
		(0 until 1000).par.foreach{ _ =>
			g.echo("A")
			/*
			assert(g.reverse("ABCD") == "DCBA")
			assert(g.toLowerCase("ABCD") == "abcd")
			assert(g.reverseAndLowerCase("ABCD") == "dcba")
			*/
		}
		val e = System.nanoTime()
		Console.out.println(f"${(e-s)/1000.0}%,f nsec")

		server.shutdown()
		client.shutdown()
		executor.shutdown()
	}

	trait Greeting {
		@Export(10)
		def reverse(text:String):String
		@Export(11)
		def toLowerCase(text:String):String
		@Export(12)
		def reverseAndLowerCase(text:String):String
		@Export(13)
		def echo(text:String):String
	}

	class GreetingImpl extends Greeting {
		def reverse(text:String) = new StringBuilder(text).reverse.toString
		def toLowerCase(text:String):String = text.toLowerCase()
		def reverseAndLowerCase(text:String):String = {
			Session() match {
				case Some(session) => session.getRemoteInterface(classOf[Greeting]).reverse(toLowerCase(text))
				case None => null
			}
		}
		def echo(text:String):String = text
	}
}
