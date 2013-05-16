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

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.util.concurrent.Executor
import org.koiroha.firestorm.core.{ReadableStreamBuffer, Endpoint}
import scala.annotation.tailrec


abstract class Worker {

	private[http] var _request:Option[Request] = None

	def request:Request = _request.get

	private[http] var _response:Option[Response] = None

	def response:Response = _response.get

	private[http] var _endpoint:Option[Endpoint] = None

	var maxKeepAliveInMillis:Option[Int] = None

	def run():Unit

	/**
	 * 通信が切断された場合に呼び出されます。
	 */
	def cancel():Unit = {}

	/**
	 * リクエストボディを受信したときに呼び出されます。リクエストボディが必要な場合は実装側でバッファ内のデータを
	 * 保持する必要があります。
	 * @param buffer 受信内容
	 */
	def body(buffer:ByteBuffer):Unit = { }

	/**
	 * Endpoint に読み出し可能なデータが到着したときに呼び出されます。
	 * リクエストステータス行、ヘッダ、Body を読み込みます。
	 * @param e
	 */
	private[http] def arrivalBufferedIn(e:Endpoint, maxStatusLineBytes:Int, charset:Charset):Unit = try {
		// ステータス行、ヘッダ、リクエストボディの読み込みを段階的に行う再起用関数
		@tailrec
		lazy val parseRequest:(ReadableStreamBuffer) => Unit = {
			in =>
				_request match {
					case None => Request.create(in, charset) match {
						case Some(request) =>
							// ステータス行の読み出し完了
							_request = Some(request)
						case None =>
							if(in.length > maxStatusLineBytes) {
								throw new HTTP.RequestURITooLong()
							}
							// ステータス行受信未完了
							return
					}
					case Some(request) => if(request.rawHeader.isDefined) {
						// リクエストボディを設定
						body(in.slice {
							_.remaining()
						}.get)
						return
					} else {
						Request.readHeader(in, charset) match {
							case Some(h) =>
								request.rawHeader = Some(h)
								_response = Some(new Response({
									b => e.out.write(b)
								}))
								_endpoint = Some(e)
								// 処理の開始
								run()
							case None =>
								// ヘッダ受信未完了
								return
						}
					}
				}
				parseRequest(in)
		}
		parseRequest(e.in)
	} catch {
		case ex:HTTP.Exception =>
			e.out.write(ex.toByteArray)
			e.close()
	}

}

abstract class ConcurrentWorker(executor:Executor) extends Worker {
	var thread:Option[Thread] = None

	final def run() {
		executor.execute(new Runnable() {
			def run() = execute()
		})
	}

	final override def cancel() = {
		thread.foreach {
			_.interrupt()
		}
	}

	final override def body(buffer:ByteBuffer) {
		request.sink.write(buffer)
	}

	private def execute():Unit = {
		thread = Some(Thread.currentThread())
		if(maxKeepAliveInMillis.isEmpty) {
			response.header("Connection") = "close"
		}
		syncRun()
		if(maxKeepAliveInMillis.isEmpty) {
			_endpoint.get.close()
		}
	}

	def syncRun():Unit
}
