/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service.domain

import com.kazzla.core.io._
import com.twitter.finagle.http.path.{Root, Path, /}
import com.twitter.finagle.http.{Method, Response, Request}
import com.twitter.util.Future
import java.io.{FileInputStream, IOException, FileNotFoundException, File}
import java.lang.String
import java.util.UUID
import org.jboss.netty.buffer.ChannelBuffers
import org.jboss.netty.handler.codec.http.HttpResponseStatus
import org.slf4j.LoggerFactory
import scala.Some
import scala.collection.JavaConversions._
import sun.misc.BASE64Decoder

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Service
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Service(docroot:File, domain:Domain) extends com.twitter.finagle.Service[Request, Response] {

	import Service._

	// ==============================================================================================
	// リクエストの実行
	// ==============================================================================================
	/**
	 * 指定されたリクエストを実行します。
	 */
	def apply(request:Request):Future[Response] = try {
		request.dump()

		// Basic 認証情報を取得
		val (user, _) = request.authorization match {
			case Some(BasicAuth(credentials)) =>
				new String(new BASE64Decoder().decodeBuffer(credentials)) match {
					case UserPass(u, p) => (u, p)
					case unknownCredentials =>
						logger.debug(s"unsupported basic authorization credentials: $unknownCredentials")
						return Future.value(Unauthorized)
				}
			case Some(unknownCredentials) =>
				logger.debug(s"unsupported authorization credentials: $unknownCredentials")
				return Future.value(Unauthorized)
			case None =>
				logger.debug(s"authorization not presents")
				return Future.value(Unauthorized)
		}

		Path(request.path) match {
			// TODO case Method.Get -> Root / "api" / "certs" / "domain" だと何故か一致しない
			case Root / "api" / "certs" / "domain" if request.method == Method.Get  =>
				val cacert = domain.ca.rawCert
				val response = Response()
				response.content = ChannelBuffers.copiedBuffer(cacert)
				response.contentType = "application/x-x509-ca-cert"
				response.contentLength = cacert.length
				response.cacheControl = "no-cache"
				Future.value(response)
			case Root / "api" / "certs" / "newdn" if request.method == Method.Get  =>
				// TODO 新規ノードID用 UUID の発行方法を検討
				// TODO C, ST, O を　CA 証明書と合わせる
				val nodeid = UUID.randomUUID()
				val response = Response()
				val c = s"CN=$nodeid, OU=node, OU=$user, O=Kazzla, ST=Tokyo, C=JP".getBytes(UTF8)
				response.content = ChannelBuffers.copiedBuffer(c)
				response.contentType = s"text/plain; charset=$UTF8"
				response.contentLength = c.length
				response.cacheControl = "no-cache"
				Future.value(response)
			case Root / "api" / "certs" / nodeid if request.method == Method.Post =>
				issue(request, user, nodeid)
			case _ =>
				web(request)
		}
	} catch {
		case ex:Throwable =>
			logger.error(s"unexpected server error", ex)
			Future.value(Response(HttpResponseStatus.INTERNAL_SERVER_ERROR))
	}

	// ==============================================================================================
	// ノード証明書の発行
	// ==============================================================================================
	/**
	 * ノード証明書の発行を行います。
	 */
	private[this] def issue(request:Request, user:String, nodeid:String):Future[Response] = {

		// 送信された CSR ファイルを読み出して一時ファイルに保存
		val csr = request.contentLength match {
			case Some(length) =>
				if(length > MaxCSRFileSize){
					logger.debug(f"too large csr file size: $length%,d / $MaxCSRFileSize%,d")
					return Future.value(Response(HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE))
				}
				val csr = domain.createTempFileAndWrite("temp", ".csr"){ out =>
					request.withInputStream{ in => copy(in, out) }
				}
				csr.deleteOnExit()
				csr
			case None =>
				logger.debug("length request header was not present")
				return Future.value(Response(HttpResponseStatus.LENGTH_REQUIRED))
		}

		// CSR に署名し新しいノード証明書を作成
		val cert = domain.ca.issue(csr)
		csr.delete()

		// TODO 証明書の内容が正しいか確認 (nodeid, user)
		// TODO 新規ノード証明書をアカウントに登録

		val binary = cert.getEncoded
		val response = Response()
		response.content = ChannelBuffers.copiedBuffer(binary)
		response.contentType = "application/x-x509-user-cert"
		response.contentLength = binary.length
		response.cacheControl = "no-cache"
		Future.value(response)
	}

	// ==============================================================================================
	// Web 処理の実行
	// ==============================================================================================
	/**
	 * 通常の Web 処理を行います。
	 */
	private[this] def web(request:Request):Future[Response] = {
		val file = {
			val f = new File(docroot, request.path)
			if(f.isDirectory) new File(f, "index.html") else f
		}
		try {
			// TODO ファイル内容のキャッシュ化
			// TODO ファイルの非同期読み込み
			// TODO Conditional GET (If-Modified-Since) の対応
			val content = using(new FileInputStream(file)){ _.readFully() }
			val response = Response()
			response.content = ChannelBuffers.copiedBuffer(content)
			response.contentType = contentType(request.fileExtension)
			response.contentLength = content.length
			response.cacheControl = "no-cache"
			Future.value(response)
		} catch {
			case ex:FileNotFoundException =>
				logger.debug(s"specified file not found: $file")
				Future.value(Response(HttpResponseStatus.NOT_FOUND))
			case ex:IOException =>
				logger.warn(s"cannot read file: $file", ex)
				Future.value(Response(HttpResponseStatus.FORBIDDEN))
		}
	}

	private[this] lazy val Unauthorized:Response = {
		val response = Response(HttpResponseStatus.UNAUTHORIZED)
		response.wwwAuthenticate = "Basic realm=\"" + domain.id + "\""
		response.cacheControl = "no-cache"
		response
	}
}

object Service {
	private[Service] val logger = LoggerFactory.getLogger(classOf[Service])

	val MaxCSRFileSize = 8 * 1024

	private[Service] val BasicAuth = "(?i)Basic\\s+(.+)".r
	private[Service] val UserPass = "([^:]*):(.*)".r

	private[Service] val UTF8 = "UTF-8"

	implicit class RRequest(r:Request){
		def dump():Unit = {
			if(logger.isTraceEnabled){
				logger.trace(s"${r.getMethod()} ${r.getUri()} ${r.getProtocolVersion()}")
				r.getHeaders().foreach{ e => logger.trace(s"  ${e.getKey}: ${e.getValue}") }
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