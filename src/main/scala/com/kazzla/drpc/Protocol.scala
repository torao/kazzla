/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc

import java.nio.ByteBuffer

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Protocol
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * プロシジャコールのためのプロトコルを実装するためのトレイトです。
 * インスタンスは 1 接続に対するスコープを持つため内部にバッファリングすることができます。
 * @author Takami Torao
 */
trait Protocol {

	// ========================================================================
	// バッファの作成
	// ========================================================================
	/**
	 * RPC のための呼び出し要求用のバイナリを作成します。
	 */
	def pack(call:Protocol.Call):ByteBuffer

	// ========================================================================
	// バッファの作成
	// ========================================================================
	/**
	 * RPC 呼び出し結果用のバイナリを作成します。
	 */
	def pack(call:Protocol.Result):ByteBuffer

	// ========================================================================
	// バッファの評価
	// ========================================================================
	/**
	 * 指定されたバッファから次の転送オブジェクトを参照します。
	 */
	def unpack(buffer:ByteBuffer):Option[Protocol.Transferable]

}

object Protocol {
	class Transferable private[drpc]()
	case class Call(id:Long, timeout:Long, name:String, args:Any*) extends Transferable
	case class Result(id:Long, error:String, result:Any*) extends Transferable
}