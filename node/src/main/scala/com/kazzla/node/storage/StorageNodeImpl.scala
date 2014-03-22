/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node.storage

import com.kazzla.asterisk.Service
import com.kazzla.storage.{StorageNode, Fragment}
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// StorageNodeImpl
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class StorageNodeImpl(storage:Storage)(implicit ctx:ExecutionContext) extends Service(ctx) with StorageNode {

	import StorageNodeImpl._

	// ==============================================================================================
	// ファイルフラグメントの参照
	// ==============================================================================================
	/**
	 * 指定されたフラグメントのデータをパイプストリームに送信します。
	 * @see [[com.kazzla.storage.StorageService.location()]]
	 */
	def read(fragment:Fragment):Future[Unit] = withPipe { pipe =>
		logger.debug(s"read($fragment)")
		// TODO 読み込み権限確認
		concurrent.future {
			storage.read(fragment.blockId, fragment.blockOffset, fragment.length, pipe.out)
		}
	}

	// ==============================================================================================
	// ファイル領域の割り当て
	// ==============================================================================================
	/**
	 * 指定されたファイルに対する領域割り当てを行います。非同期パイプに対して残りのデータサイズ (不明な場合は負の値)
	 * を送信することでリージョンサービスはファイルの新しい領域を割り当てて [[Fragment]] で応答します。クライアント
	 * は割り当てられた領域すべてにデータを書き終えたら残りのデータサイズを送信して次の領域を割り当てます。
	 * @see [[com.kazzla.storage.StorageService.allocate()]]
	 */
	def write(fragment:Fragment):Future[Unit] = withPipe { pipe =>
		logger.debug(s"write($fragment)")
		// TODO 書き込み権限確認
		pipe.useInputStream()
		concurrent.future {
			storage.write(fragment.blockId, fragment.blockOffset, fragment.length, pipe.in)
		}
	}

}

object StorageNodeImpl {
	private[StorageNodeImpl] val logger = LoggerFactory.getLogger(classOf[StorageNodeImpl])
}