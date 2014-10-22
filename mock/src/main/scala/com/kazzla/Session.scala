/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla

import java.security.cert.Certificate
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Session
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Session(val id:UUID, val auth:Certificate, val domain:Domain){
	private[this] val closed = new AtomicBoolean(false)

	/**
	 * このセッションがオープンされている場合 true
	 */
	def isOpen:Boolean = ! closed.get()

	/**
	 * このセッションをクローズします。
	 */
	def close():Unit = if(closed.compareAndSet(false, true)){
		domain.close(id)
	}

}
