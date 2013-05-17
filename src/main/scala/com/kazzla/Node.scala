/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla

import com.kazzla.domain.irpc.{MsgPackCodec, Codec}
import java.io.File
import com.kazzla.domain.Configuration

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Node
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Node {

	// ========================================================================
	// ワークスペース
	// ========================================================================
	/**
	 * このノードのワークスペースです。
	 */
	private[this] var _workspace = new File(".")

	// ========================================================================
	// ノード設定
	// ========================================================================
	/**
	 * このノードの設定です。
	 */
	var config:Configuration = new Configuration(Map())

	// ========================================================================
	// ノードディレクトリの設定
	// ========================================================================
	/**
	 * このノードのワークスペースを指定します。
	 */
	def workspace_=(dir:File) = {
		_workspace = dir
		config =
	}

}

object Node {

	// ========================================================================
	// アプリケーションの実行
	// ========================================================================
	/**
	 * ノードを起動します。
	 * @param args コマンドライン引数
	 */
	def main(args:Array[String]):Unit = {

		// コマンドラインパラメータの解析
		var path = ""
		def parse:(List[String])=>Unit = {
			case "--help" :: rest => help()
			case "-h" :: rest => help()
			case path :: rest =>
				path = path
				parse(rest)
			case List() =>
				/* */
			case _ => help()
		}
		parse(args.toList)

		// アプリケーションの起動
		val conf = Configuration.newInstance(path)
	}

	// ========================================================================
	// ヘルプの表示
	// ========================================================================
	/**
	 * ヘルプを表示します。
	 */
	private[this] def help(){
		System.err.println(
			"""
					| java com.kazzla.Node [dir]
	""".stripMargin)
		System.exit(0)
	}

}
