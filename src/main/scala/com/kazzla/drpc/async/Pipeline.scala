/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import java.nio.channels._
import scala.Some

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Pipeline
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 非同期入出力をサポートするためトレイトです。
 * @author Takami Torao
 */
trait Pipeline {

	// ========================================================================
	// 入力元の参照
	// ========================================================================
	/**
	 * 入力元を参照します。
	 */
	def in:SelectableChannel with ReadableByteChannel

	// ========================================================================
	// 出力先の参照
	// ========================================================================
	/**
	 * このパイプラインの出力先を参照します。
	 */
	def out:SelectableChannel with WritableByteChannel

}
