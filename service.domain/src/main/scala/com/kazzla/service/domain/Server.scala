/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service.domain

import com.kazzla.core.io._
import com.twitter.finagle.Service
import com.twitter.finagle.builder.ServerBuilder
import com.twitter.finagle.http._
import com.twitter.finagle.http.path._
import com.twitter.util.Future
import java.io._
import java.net.InetSocketAddress
import java.security.cert.{X509Certificate, CertificateFactory}
import java.util.UUID
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.slf4j.LoggerFactory
import scala.collection.JavaConversions._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Server
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object Server {
	private[Server] val logger = LoggerFactory.getLogger(Server.getClass)

	lazy val cacert = {
		val cf = CertificateFactory.getInstance("X.509")
		using(new FileInputStream("service.domain/ca/demoCA/cacert.pem")){ in =>
			cf.generateCertificates(in).toSeq.asInstanceOf[Seq[X509Certificate]]
		}
	}

	// ==============================================================================================
	// ドメインサーバの起動
	// ==============================================================================================
	/**
	 * ドメインサーバを起動します。
	 */
	def main(args:Array[String]):Unit = {
		val service = new Service[Request, Response] {
			def apply(request:Request):Future[Response] = try {
				request.dump()
				val response = Response()
				Path(request.path) match {
					case Root / "api" / "ca" / "cert" =>
						response.content = using(new FileInputStream("service.domain/ca/demoCA/cacert.pem")){ in =>
							ChannelBuffers.copiedBuffer(in.readFully())
						}
						Future.value(response)
					case Root / "api" / "storage" / username / "newdn" =>
						// TODO 新規ノードID用 UUID の発行方法を検討
						val uuid = UUID.randomUUID()
						// TODO C, ST, O を　CA 証明書と合わせる
						response.setContentString(s"CN=$uuid, OU=node, OU=$username, O=Kazzla, ST=Tokyo, C=JP")
						Future.value(response)
					case Root / "api" / "storage" / username / node / "activate" =>
						// POST /api/storage/activate/username
						val csr = request.contentLength match {
							case Some(length) =>
								if(length > 4 * 1024){
									return Future.value(Response(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE))
								}
								val file = File.createTempFile("temp", ".csr")
								file.deleteOnExit()
								using(new FileOutputStream(file)){ out =>
									request.withInputStream{ in =>
										copy(in, out, new Array[Byte](1024))
									}
								}
								file
							case None =>
								return Future.value(Response(HttpResponseStatus.LENGTH_REQUIRED))
						}
						val out = new ByteArrayOutputStream()
						activate(username, csr, out)
						csr.delete()
						val bin = out.toByteArray
						logger.debug(s"CSR: ${bin.length}B: ${new String(bin)}")
						response.contentLength = bin.length
						response.contentType = "application/x-x509-user-cert"
						response.setContent(ChannelBuffers.copiedBuffer(bin))
						Future.value(response)
					case _ =>
						Future.value(Response(HttpResponseStatus.NOT_FOUND))
				}
			} catch {
				case ex:Throwable =>
					System.err.println(new File(".").getCanonicalPath)
					ex.printStackTrace()
					Future.value(Response(HttpResponseStatus.INTERNAL_SERVER_ERROR))
			}
		}
		val server = ServerBuilder()
			.codec(RichHttp[Request](Http()))
			.bindTo(new InetSocketAddress("localhost", 8088))
			.name("HttpServer")
			.build(service)
	}

	// ==============================================================================================
	// 証明書の発行
	// ==============================================================================================
	/**
	 * Storage Node に対して新しい証明書を発行します。
	 */
	private[this] def activate(token:String, csr:File, out:OutputStream):Unit = {
		/*
		using(new FileInputStream("ca/client.crt")){ fin =>
			copy(fin, out, new Array[Byte](1024))
		}
		*/
		val pb = new ProcessBuilder(
			"/usr/bin/openssl", "ca",
			"-config", "./service.domain/ca/openssl.cnf",
			"-batch",
			"-in", csr.getAbsolutePath,
			"-days", "3650",
			"-passin", "pass:kazzla",
			"-keyfile", "service.domain/ca/demoCA/private/cakey.pem")
		val proc = pb.start()
		val buffer = new Array[Byte](1024)
		copy(proc.getInputStream, out, buffer)
		copy(proc.getErrorStream, System.err, buffer)
		proc.waitFor()
		if(proc.exitValue() != 0){
			throw new IOException()
		}
	}

	implicit class RRequest(r:Request){
		def dump():Unit = {
			if(logger.isTraceEnabled){
				logger.trace(s"${r.getMethod()} ${r.getUri()} ${r.getProtocolVersion()}")
				r.getHeaders().foreach{ e => logger.trace(s"  ${e.getKey}: ${e.getValue}") }
			}
		}
	}

}
