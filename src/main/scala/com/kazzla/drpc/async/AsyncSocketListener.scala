/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import java.nio.ByteBuffer

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// AsyncSocketListener
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 *
 * @author Takami Torao
 */
trait AsyncSocketListener {

	// ========================================================================
	// データの受信通知
	// ========================================================================
	/**
	 * 非同期ソケットがデータを受診した時に呼び出されます。
	 */
	def dataReceived(buffer:ByteBuffer)

}
