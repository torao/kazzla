/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.birpc.netty

import com.kazzla.core.io._
import com.kazzla.birpc.{Pipe, Export, Session}
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.{ClientBootstrap, ServerBootstrap}
import org.jboss.netty.channel.Channel
import org.jboss.netty.channel.socket.nio.{NioClientSocketChannelFactory, NioServerSocketChannelFactory}
import java.io.{FileInputStream, BufferedInputStream, InputStream}
import scala.annotation.tailrec
import javax.net.ssl.{SSLContext, KeyManagerFactory}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Main
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object Main {
	def main(args:Array[String]):Unit = {
		val executor = Executors.newCachedThreadPool()

		val serverSSL = getSSLContext("ca/server.jks", "kazzla", "kazzla")
		val clientSSL = getSSLContext("ca/client.jks", "kazzla", "kazzla")
		val server = {
			val sessionFactory:(Channel)=>Session = { ch =>
				new Session(ch.getRemoteAddress.getName, true, executor, new GreetingImpl())
			}
			val channelFactory = new NioServerSocketChannelFactory()
			val bootstrap = new ServerBootstrap(channelFactory)
			bootstrap.setPipelineFactory(new IrpcChannelPipelineFactory(sessionFactory, serverSSL))
			bootstrap.bind(new InetSocketAddress(7777))
			bootstrap
		}

		val session = new Session("client", false, executor, new GreetingImpl())
		val client = {
			val channelFactory = new NioClientSocketChannelFactory()
			val bootstrap = new ClientBootstrap(channelFactory)
			bootstrap.setPipelineFactory(new IrpcChannelPipelineFactory(session, clientSSL))
			bootstrap.connect(new InetSocketAddress("localhost", 7777))
			bootstrap
		}

		val g = session.getRemoteInterface(classOf[Greeting])
		val max = 1000
		val s = System.nanoTime()
		(0 until max).par.foreach{ _ =>
			g.echo("A")
			/*
			assert(g.reverse("ABCD") == "DCBA")
			assert(g.toLowerCase("ABCD") == "abcd")
			assert(g.reverseAndLowerCase("ABCD") == "dcba")
			*/
		}
		val e = System.nanoTime()
		Console.out.println(f"${(e-s)/max.toDouble}%,.2f nsec")

		val pipe = session.open(14)
		val o = scala.concurrent.ops.future {
			for(i <- 0 until 0xFFFF){
				pipe.out.write(i)
			}
			pipe.out.close()
		}
		val i = scala.concurrent.ops.future {
			val b = new Array[Byte](1024)
			var i:Byte = 0
			var count = 0
			while({
				val len = pipe.in.read(b)
				if(len > 0){
					for(j <- 0 until len){
						assert(b(j) == i)
						i = (i + 1).toByte
					}
					count += len
				}
				len
			} > 0){
				None
			}
			count
		}
		o()
		Console.out.println(i())
		pipe.close("")

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
		@Export(14)
		def echo():Unit
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
		def echo():Unit = {
			Pipe() match {
				case Some(pipe) =>
					@tailrec
					def f(in:InputStream, buffer:Array[Byte]):Unit = {
						val len = in.read(buffer)
						if(len > 0){
							pipe.out.write(buffer, 0, len)
							f(in, buffer)
						} else {
							None
						}
					}
					f(pipe.in, new Array[Byte](1024))
					pipe.out.flush()
				case None => None
			}
		}
	}
}
