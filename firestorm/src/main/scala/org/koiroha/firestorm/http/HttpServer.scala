/*
 * Copyright (c) 2013 BJöRFUAN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.koiroha.firestorm.http

import org.koiroha.firestorm.core.Context

/**
 * HTTP プロトコルを使用するサーバです。
 *
 * {{{
 *   val server = HttpServer(context)
 * }}}
 * @param context
 * @param factory HTTP Worker を生成するファクトリインスタンス
 */
case class HttpServer(context:Context)(factory: => Worker) {

	/**
	 * TCP/IP レベルのサーバ。
	 */
	private val server = Server(context).onAccept {
		(server, endpoint) =>
			val worker:Worker = factory
			endpoint.onArrivalBufferedIn {
				e =>
					worker.arrivalBufferedIn(e, maxStatusLineBytes, headerCharset)
			}.onClose {
				_ =>
					worker.cancel()
					worker.request.close()
			}
	}

	/**
	 * ステータス行として受け入れる長さの最大バイト数。
	 * これより長いステータス行またはヘッダを検出すると Bad Request を返します。
	 */
	var maxStatusLineBytes = 4 * 1024

	/**
	 * リクエストヘッダ/レスポンスヘッダに適用する文字セット。
	 */
	var headerCharset = Charset.forName("iso-8859-1")

	/**
	 * 指定されたポート上で Listen するサーバを構築します。
	 * @param port
	 */
	def listen(port:Int):HttpServer = {
		server.listen(port)
		this
	}

	/**
	 * 指定されたアドレス上で Listen するサーバを構築します。
	 * @param local
	 * @param backlog
	 */
	def listen(local:SocketAddress, backlog:Int):HttpServer = {
		server.listen(local, backlog)
		this
	}

	/**
	 * このサーバインスタンスによる HTTP サービスを終了します。現在実行中の処理は Context がクローズされるまで
	 * 維持されます。
	 */
	def close():Unit = server.close()
}


private[http] object HTTP {

	case class Exception(code:Int, message:String, content:String) extends java.lang.Exception {
		lazy val toByteArray:Array[Byte] = ("HTTP/1.1 %1$s %2$s\r\n" +
			"Connection: close\r\n" +
			"Content-Type: text/plain\r\n" +
			"Cache-Control: no-cache\r\n" +
			"\r\n" +
			"%3$s").format(code, message, content.replaceAll("<", "&lt;")).getBytes("iso-8859-1")
	}

	class BadRequest(content:String) extends Exception(400, "Bad Request", content)

	class RequestURITooLong extends Exception(414, "Request-URI Too Long", "")

}