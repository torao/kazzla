/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service

import com.kazzla.asterisk.Export
import scala.concurrent.Future

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
	 * サービスと接続した直後にクライアントから呼び出します。それぞれのバージョン番号は Int で表されており
	 * (version/10000, (version/100)%100, version%100) がそれぞれメジャーバージョン、マイナーバージョン、
	 * マイナーサブバージョンを表します。
	 *
	 * @param version クライアントの仕様バージョン
	 * @return サービスの仕様バージョン
	 */
	@Export(10)
	def handshake(version:Int):Future[Int]

}

case class Version(major:Int, minor:Int, minorSub:Int) {
	assert(major >= 0 && minor >= 0 && minor <= 100 && minorSub >= 0 && minorSub <= 100)
	def toInt:Int = (major * 10000) + ((minor % 100) * 100) + (minorSub % 100)
}
object Version {
	def apply(v:Int):Version = Version(v / 10000, (v / 100) % 100, v % 100)
}