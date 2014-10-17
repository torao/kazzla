/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node

import com.kazzla.asterisk
import com.kazzla.asterisk.Session
import com.kazzla.asterisk.codec.MsgPackCodec
import com.kazzla.asterisk.netty.Netty
import com.kazzla.cert._
import com.kazzla.node.storage.{StorageNodeImpl, Storage}
import com.kazzla.service.Version
import java.io.{File, IOException}
import java.net.URL
import java.security.Security
import java.util.concurrent.Executors
import javax.net.ssl._
import org.slf4j.LoggerFactory
import scala.Some
import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContextExecutorService, ExecutionContext}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Node
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Node(_args:Array[String]) extends com.kazzla.shell.ShellExecutable("node", _args) {

	import Node._

	case class Config(regions:Seq[URL], data:File, certs:Option[Seq[KeyManager]], trusts:Option[Seq[TrustManager]]) {
		lazy val sslContext = (certs, trusts) match {
			case (Some(c), Some(t)) =>
				val context = SSLContext.getInstance("TLS")
				context.init(c.toArray, t.toArray, null)
				context
			case (Some(c), None) =>
				val context = SSLContext.getInstance("TLS")
				context.init(c.toArray, null, null)
				context
			case (None, Some(t)) =>
				val context = SSLContext.getInstance("TLS")
				context.init(null, t.toArray, null)
				context
			case (None, None) => SSLContext.getDefault
		}
	}
	val config = {
		val parser = new scopt.OptionParser[Config](getClass.getName) {
			opt[File]('d', "dir").text("directory to store data blocks").action{case(f,c)=>c.copy(data=f)}
			opt[(String,String)]('c', "cert").text("client certification file to connect to region server").action{ case((f,p),c) =>
				val ks = load(new File(f), p)
				val algorithm = Option(Security.getProperty("ssl.KeyManagerFactory.algorithm")).getOrElse("SunX509")
				val kmf = KeyManagerFactory.getInstance(algorithm)
				kmf.init(ks, p.toCharArray)
				c.copy(certs = Some(kmf.getKeyManagers.toSeq))
			}
			opt[(String,String)]('t', "trust").text("server certification file to connect to region server").action{ case((f,p),c) =>
				val ks = load(new File(f), p)
				val tmf = TrustManagerFactory.getInstance("SunX509")
				tmf.init(ks)
				c.copy(trusts = Some(tmf.getTrustManagers.toSeq))
			}
			help("help").text("prints this usage text")
			arg[URL]("<url>...").unbounded().required().action{case(u,c)=>c.copy(regions=c.regions:+u)}
		}
		val default = Config(Seq(), new File("data"), None, None)
		parser.parse(_args, default).getOrElse{
			parser.showUsageAsError
			System.exit(1)
			default
		}
	}

	val domain = new Domain(config.regions)
	val storage = new Storage(config.data)

	case class State(node:asterisk.Node, threads:ExecutionContextExecutorService)
	var state:Option[State] = None

	override protected def apply(args:Array[String]):Unit = {
		val node = asterisk.Node("storage")
			.bridge(Netty)
			.codec(MsgPackCodec)
			.serve(new StorageNodeImpl(storage))
			.build()
		val threads = ExecutionContext.fromExecutorService(Executors.newCachedThreadPool())
		state = Some(State(node, threads))
		logger.info(s"activate kazzla node: [${config.regions.mkString(",")}]")
		val session = connect()
		val remote = session.bind(classOf[com.kazzla.service.Domain])
		Await.result(remote.handshake(Version(1, 0, 0).toInt), Duration.Inf)
	}

	override protected def destroy():Unit = {
		state.foreach { case State(node, exec) =>
			node.shutdown()
			exec.shutdown()
		}
	}

	@tailrec
	private[this] def connect():Session = state match {
		case Some(State(node, _)) =>
			val addr = domain.pickup()
			logger.debug(s"connecting to: $addr")
			val future = node.connect(addr, Some(config.sslContext))
			try {
				val session = Await.result(future, Duration.Inf)
				logger.info(s"connection success to server: ${session.wire.peerName}")
				return session
			} catch {
				case ex:Exception =>
					logger.error("fail to connect server", ex)
					connect()
			}
		case None =>
			throw new IOException("node is not started")
		}
	}

}

object Node {
	private[Node] val logger = LoggerFactory.getLogger(classOf[Node])
	def main(args:Array[String]):Unit = { new Node(args).apply() }
}