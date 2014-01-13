/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service

import com.kazzla.asterisk.Export

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Domain
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
trait Domain {

	// ==============================================================================================
	// ハンドシェイク
	// ==============================================================================================
	/**
	 * サーバと接続した直後に呼び出されます。
	 */
	@Export(10)
	def handshake():Unit

}
