/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node.storage

import com.kazzla.asterisk.Node
import com.kazzla.asterisk.codec.MsgPackCodec
import com.kazzla.asterisk.netty.Netty
import com.kazzla.core.io._
import com.kazzla.core.tools.ShellTools
import com.kazzla.node.Domain
import java.io.File
import java.net.{URI, HttpURLConnection, URL}
import org.slf4j.LoggerFactory

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Server
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Server(dataDir:File, regionServices:Seq[URL]) {
	var node:Option[Node] = None

	val domain = new Domain(regionServices)

	// ==============================================================================================
	// 実体ファイルの参照
	// ==============================================================================================
	/**
	 * 指定された UUID のブロックに対するローカルファイルを参照します。
	 * @return ブロックファイル
	 */
	def start():Unit = {

		node = Some(Node("storage")
			.bridge(Netty)
			.codec(MsgPackCodec)
			.serve(new StorageImpl(dataDir))
			.build())
	}

	def stop():Unit = {
		node.foreach{ _.shutdown() }
	}

}

object Server {
	private[Server] val logger = LoggerFactory.getLogger(classOf[Server])

	// ==============================================================================================
	// 実体ファイルの参照
	// ==============================================================================================
	/**
	 * 指定された UUID のブロックに対するローカルファイルを参照します。
	 * @return ブロックファイル
	 */
	def main(args:Array[String]):Unit = {

		// コマンドライン引数の解析
		var dir:File = new File(".")
		var region = List[URL]()
		lazy val parse:(List[String])=>Unit = {
			case "-d" :: d :: rest =>
				dir = new File(d)
				parse(rest)
			case url :: rest =>
				region ::= new URL(url)
				parse(rest)
			case List() => None
		}
		parse(args.toList)

	}

}
