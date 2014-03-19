/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla

import com.kazzla.asterisk.Abort

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Error
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
sealed abstract class _Error(val code:Int, val defaultMessage:String) {
	def apply() = Abort(code, defaultMessage)
	def apply(msg:String) = Abort(code, msg)
}

object EC {
	case object BrokenBlock extends _Error(100, "")
}