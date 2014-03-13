/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service.domain

import com.kazzla.asterisk.{Pipe, Session}
import com.kazzla.core.io._
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http._
import java.io.{FileInputStream, IOException, FileNotFoundException, File}
import java.lang.String
import org.slf4j.LoggerFactory
import scala.Some
import scala.collection.JavaConversions._
import scala.concurrent.{Promise, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import sun.misc.BASE64Decoder
import com.kazzla.service.Version

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Service
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Service(docroot:File, domain:Domain)
	extends com.kazzla.asterisk.Service with com.kazzla.service.Domain {

	import Service._

	def handshake(version:Int):Future[Int] = withPipe { pipe =>
		val session = pipe.session
		logger.debug(s"handshake(${version/10000}.${(version/100)%100}.${version%100})")
		Version(version) match {
			case Version(major, minor, _) =>
		}
		domain.openNodeSession(session.nodeId, session.sessionId, Array(session.wire.peerName))
		Promise.successful(1 * 100 + 0).future
	}

	// ==============================================================================================
	// リクエストの実行
	// ==============================================================================================
	/**
	 * 指定されたリクエストを実行します。
	 */
	def http(request:HttpRequest):HttpResponse = try {
		request.dump()

		val _api_cert_nodeid = "/api/certs/([^/]*)".r
		request.path match {
			case "/api/regions" if request.getMethod == HttpMethod.GET  =>
				val servers = "localhost:8089".getBytes("UTF-8")
				val content = Unpooled.copiedBuffer(servers)
				val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content)
				response.headers().set("Content-Type", "text/plain")
					.set("Content-Length", servers.length)
					.set("Cache-Control", "no-cache")
					.set("Connection", "close")
				response
			case "/api/certs/domain" if request.getMethod == HttpMethod.GET  =>
				val cacert = domain.ca.rawCert
				val content = Unpooled.copiedBuffer(cacert)
				val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content)
				response.headers().set("Content-Type", "application/x-x509-ca-cert")
					.set("Content-Length", cacert.length)
					.set("Cache-Control", "no-cache")
				response
			case "/api/certs/newdn" if request.getMethod == HttpMethod.GET  =>
				// TODO 新規ノードID用 UUID の発行方法を検討
				// TODO C, ST, O を　CA 証明書と合わせる
				auth(request.headers().get("Authorization")) match {
					case Some(account) =>
						val c = domain.newCertificateDName(account).getBytes(UTF8)
						val content = Unpooled.copiedBuffer(c)
						val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content)
						response.headers().set("Content-Type", s"text/plain; charset=$UTF8")
							.set("Content-Length", c.length)
							.set("Cache-Control", "no-cache")
						response
					case None =>
						Unauthorized
				}
			case _api_cert_nodeid(nodeid) if request.getMethod == HttpMethod.POST =>
				auth(request.headers().get("Authorization")) match {
					case Some(account) =>
						issue(request, account, nodeid)
					case None =>
						Unauthorized
				}
			case _ =>
				web(request)
		}
	} catch {
		case ex:Throwable =>
			logger.error(s"unexpected server error", ex)
			Server.httpErrorResponse(HttpResponseStatus.INTERNAL_SERVER_ERROR)
	}

	// ==============================================================================================
	// 認証処理の実行
	// ==============================================================================================
	/**
	 */
	private[this] def auth(authorization:String):Option[Account] = {
		// Basic 認証情報を取得
		Option(authorization) match {
			case Some(BasicAuth(credentials)) =>
				new String(new BASE64Decoder().decodeBuffer(credentials)) match {
					case UserPass(u, p) =>
						domain.authenticate(u, p) match {
							case Some(a) => Some(a)
							case None =>
								logger.debug(s"authorization failure")
								None
						}
					case unknownCredentials =>
						logger.debug(s"unsupported basic authorization credentials: $unknownCredentials")
						None
				}
			case Some(unknownCredentials) =>
				logger.debug(s"unsupported authorization credentials: $unknownCredentials")
				None
			case None =>
				logger.debug(s"authorization not presents")
				None
		}
	}

	// ==============================================================================================
	// ノード証明書の発行
	// ==============================================================================================
	/**
	 * ノード証明書の発行を行います。
	 */
	private[this] def issue(r:HttpRequest, account:Account, nodeid:String):HttpResponse = r match {
		case request:FullHttpRequest =>

			// 受信データのサイズを取得
			val length = Option(request.headers().get("Content-Length")) match {
				case Some(slen) =>
					try {
						val len = slen.toLong
						if(len < 0){
							logger.debug(s"negative Content-Length request header: $slen")
							return Server.httpErrorResponse(HttpResponseStatus.BAD_REQUEST)
						} else if(len > MaxCSRFileSize){
							logger.debug(f"too large csr file size: $len%,d / $MaxCSRFileSize%,d")
							return Server.httpErrorResponse(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE)
						}
						len
					} catch {
						case ex:NumberFormatException =>
							logger.debug(s"unexpected Content-Length request header: $slen")
							return Server.httpErrorResponse(HttpResponseStatus.BAD_REQUEST)
					}
				case None =>
					logger.debug("Content-Length request header was not present")
					return Server.httpErrorResponse(HttpResponseStatus.LENGTH_REQUIRED)
			}

			// 送信された CSR ファイルを読み出して一時ファイルに保存
			val csr = {
					val csr = domain.createTempFileAndWrite("temp", ".csr"){ out =>
						val buffer = request.content.nioBuffer()
						out.write(buffer.array(), buffer.arrayOffset(), buffer.remaining())
					}
					csr.deleteOnExit()
					csr
			}

			// CSR に署名し新しいノード証明書を作成
			val cert = domain.ca.issue(csr)

			// CSR ファイルを削除
			csr.delete()

			// TODO 証明書の内容が正しいか確認 (nodeid, user)
			// TODO 新規ノード証明書をアカウントに登録

			// ノード証明書を登録
			domain.registerNodeCertificate(account, cert)

			// ノード証明書を送信
			val binary = cert.getEncoded
			val content = Unpooled.copiedBuffer(binary)
			val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content)
			response.headers().set("Content-Type", "application/x-x509-user-cert")
			response.headers().set("Content-Length", binary.length)
			response.headers().set("Cache-Control", "no-cache")
			response
		case _ =>
			logger.error(s"message body is not contained: ${r.getClass.getSimpleName}")
			BadRequest(r.getUri)
	}

	// ==============================================================================================
	// Web 処理の実行
	// ==============================================================================================
	/**
	 * 通常の Web 処理を行います。
	 */
	private[this] def web(request:HttpRequest):HttpResponse = {
		val file = {
			val f = new File(docroot, request.path)
			if(f.isDirectory) new File(f, "index.html") else f
		}
		try {
			// TODO ファイル内容のキャッシュ化
			// TODO ファイルの非同期読み込み
			// TODO Conditional GET (If-Modified-Since) の対応
			val c = using(new FileInputStream(file)){ _.readFully() }
			val content = Unpooled.copiedBuffer(c)
			val response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, content)
			response.headers().set("Content-Type", contentType(request.fileExtension))
			response.headers().set("Content-Length", c.length)
			response.headers().set("Cache-Control", "no-cache")
			response
		} catch {
			case ex:FileNotFoundException =>
				logger.debug(s"specified file not found: $file")
				NotFound(request.path)
			case ex:IOException =>
				logger.warn(s"cannot read file: $file", ex)
				Server.httpErrorResponse(HttpResponseStatus.FORBIDDEN)
		}
	}

	private[this] def NotFound(uri:String):HttpResponse = {
		Server.httpErrorResponse(HttpResponseStatus.NOT_FOUND, s"resource not found: $uri")
	}

	private[this] def BadRequest(uri:String):HttpResponse = {
		Server.httpErrorResponse(HttpResponseStatus.BAD_REQUEST, s"bad request: $uri")
	}

	private[this] def Unauthorized:HttpResponse = {
		val res = Server.httpErrorResponse(HttpResponseStatus.UNAUTHORIZED)
		res.headers().set("WWW-Authentication", s"""Basic realm="${domain.id}""")
		res
	}

}

object Service {
	private[Service] val logger = LoggerFactory.getLogger(classOf[Service])

	val MaxCSRFileSize = 8 * 1024

	private[Service] val BasicAuth = "(?i)Basic\\s+(.+)".r
	private[Service] val UserPass = "([^:]*):(.*)".r
	private[this] val PathWithoutQuery = """([^\?]*)\??.*""".r

	private[Service] val UTF8 = "UTF-8"

	implicit class RRequest(r:HttpRequest){
		def dump():Unit = {
			if(logger.isTraceEnabled){
				logger.trace(s"${r.getMethod} ${r.getUri} ${r.getProtocolVersion}")
				r.headers().foreach{ e => logger.trace(s"  ${e.getKey}: ${e.getValue}") }
			}
		}
		def path:String = Option(r.getUri) match {
			case Some(PathWithoutQuery(path)) => path
			case Some(path) => path
			case None => "/"
		}
		def fileExtension:String = {
			val pos = path.lastIndexOf('.')
			if(pos < 0){
				""
			} else {
				path.substring(pos + 1)
			}
		}
	}

	// ==============================================================================================
	// Content-Type の参照
	// ==============================================================================================
	/**
	 * 指定された拡張子に対する Content-Type を参照します。
	 */
	private[Service] def contentType(ext:String):String = ext.toLowerCase match {
		case "html" => "text/html"
		case "css"  => "text/css"
		case "js"   => "application/javascript"
		case "png"  => "image/png"
		case "jpeg" => "image/jpeg"
		case "gif"  => "image/gif"
		case _      => "application/octet-stream"
	}

}