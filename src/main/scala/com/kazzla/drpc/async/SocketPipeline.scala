/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import java.nio.channels.{WritableByteChannel, ReadableByteChannel, SelectableChannel, SocketChannel}
import java.nio.ByteBuffer

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// SocketPipeline
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 * @param sink データ読み出し時に呼び出す関数。パイプラインの入力チャネルが EOF に達して
 *             いる場合は null パラメータで呼び出される。
 */
class SocketPipeline(channel:SocketChannel, sink:(ByteBuffer)=>Unit) extends Pipeline(sink) {

	// ========================================================================
	// 入力元の参照
	// ========================================================================
	/**
	 * このパイプラインの入力元を参照します。
	 */
	def in:SelectableChannel with ReadableByteChannel = channel

	// ========================================================================
	// 出力先の参照
	// ========================================================================
	/**
	 * このパイプラインの出力先を参照します。
	 */
	def out:SelectableChannel with WritableByteChannel = channel

	// ========================================================================
	// パイプラインのクローズ
	// ========================================================================
	/**
	 * このパイプラインをクローズします。
	 */
	override def close() = {
		super.close()
		channel.close()
	}

}
