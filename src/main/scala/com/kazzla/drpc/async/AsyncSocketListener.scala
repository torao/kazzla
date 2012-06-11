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
	 * 非同期ソケットがデータを受信した時に呼び出されます。パラメータとして渡されたバッファ
	 * は呼び出し終了後にクリアされるためサブクラス側で保持することはできません。
	 * @param buffer 受信したデータ
	 */
	def asyncDataReceived(buffer:ByteBuffer)

}
