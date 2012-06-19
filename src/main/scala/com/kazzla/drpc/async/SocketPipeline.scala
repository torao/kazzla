/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import java.nio.channels.{WritableByteChannel, ReadableByteChannel, SelectableChannel, SocketChannel}
import com.kazzla.drpc.async.Pipeline

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// SocketPipeline
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class SocketPipeline(channel:SocketChannel) extends PipelineAssembler {

	// ========================================================================
	// 入力元の参照
	// ========================================================================
	/**
	 * 入力元を参照します。
	 */
	def in:SelectableChannel with ReadableByteChannel = channel

	// ========================================================================
	// 出力先の参照
	// ========================================================================
	/**
	 * このパイプラインの出力先を参照します。
	 */
	def out:SelectableChannel with WritableByteChannel = channel

}
