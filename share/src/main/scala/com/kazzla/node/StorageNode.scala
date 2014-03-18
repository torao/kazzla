/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node

import com.kazzla.asterisk.Export
import com.kazzla.service.Fragment
import scala.concurrent.Future

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// StorageNode
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
trait StorageNode {

	// ==============================================================================================
	// ファイルフラグメントの参照
	// ==============================================================================================
	/**
	 * 指定されたフラグメントのデータをパイプストリームに送信します。
	 * @see [[com.kazzla.service.StorageService.location()]]
	 */
	@Export(102)
	def read(fragment:Fragment):Future[Unit]

	// ==============================================================================================
	// ファイル領域の割り当て
	// ==============================================================================================
	/**
	 * 指定されたファイルに対する領域割り当てを行います。非同期パイプに対して残りのデータサイズ (不明な場合は負の値)
	 * を送信することでリージョンサービスはファイルの新しい領域を割り当てて [[Fragment]] で応答します。クライアント
	 * は割り当てられた領域すべてにデータを書き終えたら残りのデータサイズを送信して次の領域を割り当てます。
	 * @see [[com.kazzla.service.StorageService.allocate()]]
	 */
	@Export(103)
	def write(fragment:Fragment):Future[Unit]

}

object StorageNode {
	val Read:Short = 102
	val Write:Short = 103
}
