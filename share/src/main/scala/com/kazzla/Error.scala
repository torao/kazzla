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

	// リトライ可能
	case object FileLocked extends _Error(1000, "")

	// クライアント側のエラー
	case object AuthFailure extends _Error(2000, "")
	case object FileNotFound extends _Error(2100, "")
	case object FileExists extends _Error(2101, "")
	case object DiskFull extends _Error(2102, "")
	case object DirectoryNotEmpty extends _Error(2103, "")
	case object NotFile extends _Error(2104, "")
	case object TooManyFileLocks extends _Error(2105, "")
	case object TooManySessionLocks extends _Error(2106, "")
	case object NotDirectory extends _Error(2107, "")
	case object BadLocation extends _Error(2107, "")

	case object PrimaryNodeDown extends _Error(2200, "")

	// ノード側のエラー
	case object BrokenBlock extends _Error(3100, "")
	case object PrematureEOF extends _Error(3101, "")

	// サービス側のエラー
	case object InternalServerError extends _Error(4000, "")
}