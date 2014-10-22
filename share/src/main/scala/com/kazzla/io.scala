/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla

import java.io._
import java.net.{InetSocketAddress, SocketAddress}
import java.security.{DigestInputStream, MessageDigest}
import java.util.UUID
import org.slf4j._
import scala.annotation.tailrec

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// io
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
package object io {

	import scala.language.reflectiveCalls

	private[this] val logger = LoggerFactory.getLogger("com.kazzla.io")

	// ==============================================================================================
	// クローズ
	// ==============================================================================================
	/**
	 * `close()` メソッドを実装しているオブジェクトに対して例外なしでクローズ操作をおこないます。
	 */
	def close[T <% { def close():Unit }](cs:T*):Unit = cs.filter{ _ != null }.foreach { c =>
		try {
			c.close()
		} catch {
			case ex:IOException =>
				logger.warn(s"fail to close resource: $c", ex)
		}
	}

	// ==============================================================================================
	// リソースのスコープ設定
	// ==============================================================================================
	/**
	 * 指定されたクローズ可能なオブジェクトに対してラムダ実行後にクローズの実行を保証します。
	 */
	def using[T <% { def close():Unit }, U](resource:T)(f:(T)=>U):U = try {
		f(resource)
	} finally {
		close(resource)
	}

	// ==============================================================================================
	// リソースのスコープ設定
	// ==============================================================================================
	/**
	 * 指定されたクローズ可能なオブジェクトに対してラムダ実行後にクローズの実行を保証します。
	 */
	def usingOutput[T](file:File)(f:(OutputStream)=>T):T = using(new FileOutputStream(file))(f)
	def usingInput[T](file:File)(f:(InputStream)=>T):T = using(new FileInputStream(file))(f)

	// ==============================================================================================
	// ストリームのコピー
	// ==============================================================================================
	/**
	 * 指定されたバッファを用いてストリームのコピーを行います。
	 */
	@tailrec
	def copy[T <% { def write(b:Array[Byte],o:Int,l:Int):Unit }](src:InputStream, dst:T, buffer:Array[Byte], length:Int = Int.MaxValue):Unit = {
		if(length > 0){
			val len = src.read(buffer, 0, math.min(buffer.length, length))
			if(len > 0){
				dst.write(buffer, 0, len)
				copy(src, dst, buffer, length - len)
			}
		}
	}

	def copy[T <% { def write(b:Array[Byte],o:Int,l:Int):Unit }](src:InputStream, dst:T):Unit = {
		copy(src, dst, new Array[Byte](1024))
	}

	implicit class RichSocketAddress(addr:SocketAddress) {
		def getName:String = addr match {
			case i:InetSocketAddress =>
				s"${i.getAddress.getHostAddress}:${i.getPort}"
			case s => s.toString
		}
	}

	implicit class RichInputStream(in:InputStream) {

		/**
		 * メッセージダイジェストの算出
		 */
		def digest(algorithm:String):Array[Byte] = {
			val md = MessageDigest.getInstance(algorithm)
			val is = new DigestInputStream(in, md)
			val buffer = new Array[Byte](1024)
			while(is.read(buffer) > 0){
				None
			}
			md.digest()
		}

		/**
		 *
		 */
		def readFully():Array[Byte] = {
			val out = new ByteArrayOutputStream()
			val buffer = new Array[Byte](1024)
			copy(in, out, buffer)
			out.toByteArray
		}

		/**
		 *
		 */
		def readText(enc:String):String = new String(readFully(), enc)

	}

	/**
	 *
	 */
	implicit class RichDataOutputStream(out:DataOutputStream){
		def writeUUID(uuid:UUID):Unit = {
			out.writeLong(uuid.getMostSignificantBits)
			out.writeLong(uuid.getLeastSignificantBits)
		}
	}

	implicit class RByteArray(array:Array[Byte]){
		def toHexString:String = {
			array.map{ b => f"${b & 0xFF}%02X" }.mkString
		}
	}

}

