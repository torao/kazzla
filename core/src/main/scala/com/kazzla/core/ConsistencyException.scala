/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// ConsistencyException
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class ConsistencyException(msg:String, ex:Throwable) extends Exception(msg, ex) {
	def this(msg:String) = this(msg, null)
	def this(ex:Throwable) = this(null, ex)
	def this() = this(null, null)
}
