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

import java.io._
import java.nio.ByteBuffer
import java.nio.channels.Pipe
import java.nio.charset.Charset
import org.koiroha.firestorm.core.ReadableStreamBuffer
import scala.Some
import scala.annotation.tailrec

/**
 * ヘッダーフィールドを表すクラスです。
 * @param name ヘッダ名
 * @param value ヘッダ値
 */
case class HeaderField(name:String, value:String) {
	def this(name:String, value:Int) = this(name, value.toString)

	def this(name:String, value:Long) = this(name, value.toString)

	/**
	 * Refer value as Int.
	 * @return int value
	 */
	def intValue:Int = value.toInt

	/**
	 * Refer value as Long.
	 * @return long value
	 */
	def longValue:Long = value.toLong
}

/**
 * ヘッダーを表すクラスです。
 * @param fields ヘッダーフィールドのリスト
 */
sealed class Header private[http](protected var fields:List[HeaderField]) {

	/**
	 *
	 * @param name
	 * @return
	 */
	def apply(name:String):Option[String] = {
		fields.find {
			_.name.equalsIgnoreCase(name)
		} match {
			case Some(h) => Some(h.value)
			case None => None
		}
	}

}

/**
 * HTTP リクエストを表すクラスです。
 * @param method メソッド
 * @param uri リクエスト URI
 * @param version HTTP バージョン
 */
class Request private[http](val method:String, val uri:String, val version:String) {

	/**
	 * リクエストヘッダ
	 */
	lazy val header = new Header(rawHeader.get.split("\r\n?|\n").filter {
		_.trim().length() > 0
	}.foldLeft(List[String]()) {
		(header, line) =>
		// 分割された複数行フィールドを一つにまとめる
			if(header.length > 0 && Character.isWhitespace(line.charAt(0))) {
				val (head, tail) = header.splitAt(header.length - 1)
				head :+ (tail(0) + "\r\n" + line)
			} else {
				header :+ line
			}
	}.map {
		line =>
		// ヘッダーフィールドを参照
			val sep = line.indexOf(':')
			if(sep < 0) {
				HeaderField("", line.trim())
			} else {
				HeaderField(line.substring(sep).trim(), line.substring(sep).trim())
			}
	})

	def isBad:Boolean = {
		method == ""
	}

	/**
	 * 解析されていない状態のヘッダ。必要に応じて遅延評価により解析される。
	 */
	private[http] var rawHeader:Option[String] = None

	/**
	 * リクエストボディを参照するためのパイプ。
	 */
	private lazy val pipe:Pipe = Pipe.open()

	/**
	 * リクエストボディを参照するための SinkChannel
	 */
	private[http] lazy val sink = pipe.sink()

	/**
	 * リクエストボディを渡すための SourceChannel
	 */
	private[http] lazy val source = pipe.source()

	def close() {
		sink.close()
	}
}

private[http] object Request {

	/**
	 * エンドポイントからステータス行を読み込んでリクエストを作成します。
	 * @param in
	 * @return
	 */
	def create(in:ReadableStreamBuffer, charset:Charset):Option[Request] = {
		readLine(in, charset) match {
			case Some(line) =>
				line.trim().split(" ") match {
					case Array(method, uri, version) =>
						Some(new Request(method, uri, version))
					case _ =>
						throw new HTTP.BadRequest(line)
				}
			case None => None
		}
	}

	def readLine(in:ReadableStreamBuffer, charset:Charset):Option[String] = {
		@tailrec
		def h(i:Int, buffer:ByteBuffer):Int = {
			if(i >= buffer.remaining()) {
				-1
			} else if(buffer.get(buffer.position() + i) == '\n') {
				i + 1
			} else {
				h(i + 1, buffer)
			}
		}
		in.slice {
			buffer => h(0, buffer)
		} match {
			case Some(buffer) =>
				val array = new Array[Byte](buffer.remaining())
				buffer.get(array)
				Some(new String(array, charset))
			case None => None
		}
	}

	def readHeader(in:ReadableStreamBuffer, charset:Charset):Option[String] = {
		@tailrec
		def h(i:Int, buffer:ByteBuffer):Int = {
			if(i >= buffer.remaining()) {
				-1
			} else if(buffer.get(buffer.position() + i) == '\n'
				&& i + 2 < buffer.remaining()
				&& buffer.get(buffer.position() + i + 1) == '\r'
				&& buffer.get(buffer.position() + i + 2) == '\n') {
				i + 2
			} else if(buffer.get(buffer.position() + i) == '\n'
				&& i + 1 < buffer.remaining()
				&& buffer.get(buffer.position() + i + 1) == '\n') {
				i + 1
			} else {
				h(i + 1, buffer)
			}
		}
		in.slice {
			buffer => h(0, buffer)
		} match {
			case Some(buffer) =>
				val array = new Array[Byte](buffer.remaining())
				buffer.get(array)
				Some(new String(array, charset))
			case None => None
		}
	}

}


/**
 * レスポンスヘッダーを表すクラスです。
 */
final class MutableHeader private[http]() extends Header(List[HeaderField]()) {
	def update(name:String, value:String):Unit = {
		if(value == null || name == null) {
			throw new NullPointerException(name + " = " + value)
		}
		fields ::= HeaderField(name, value)
	}

	private[http] def foreach(f:(HeaderField) => Unit) = fields.foreach {
		field => f(field)
	}
}

class Response private[http](sink:(ByteBuffer) => Unit) {
	var code:Int = 200
	var version:String = "HTTP/1.1"
	var message:String = "OK"
	val header = new MutableHeader()

	def sendResponseCode(version:String, code:Int, msg:String):Unit = {
		this.code = code
		this.version = version
		this.message = msg
	}

	def write(f:(OutputStream) => Unit) {
		sink(version + " " + code + " " + message + "\r\n")
		header.foreach {
			field =>
				sink(field.name + ": " + field.value + "\r\n")
		}
		sink("\r\n")

		val out = new BufferedOutputStream(new OutputStream() {
			override def write(b:Array[Byte]) = write(b, 0, b.length)

			override def write(b:Array[Byte], offset:Int, length:Int):Unit = {
				val buffer = ByteBuffer.allocate(length)
				buffer.put(b, offset, length)
				buffer.flip()
				sink(buffer)
			}

			def write(b:Int) {
				val buffer = ByteBuffer.allocate(1)
				buffer.put(b.toByte)
				buffer.flip()
				sink(buffer)
			}
		})
		f(out)
		out.flush()
	}

	def print(f:(PrintWriter) => Unit):Unit = print(Charset.forName("UTF-8"))(f)

	def print(charset:Charset)(f:(PrintWriter) => Unit):Unit = {
		write {
			os =>
				val out = new PrintWriter(new OutputStreamWriter(os, charset))
				f(out)
				out.flush()
		}
	}

	private def sink(text:String):Unit = {
		sink(ByteBuffer.wrap(text.getBytes("iso-8859-1")))
	}
}

