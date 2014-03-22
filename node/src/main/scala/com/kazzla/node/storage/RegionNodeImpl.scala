/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node.storage

import com.kazzla._
import com.kazzla.asterisk.Service
import com.kazzla.core.io._
import com.kazzla.storage.RegionNode
import java.util.UUID
import org.slf4j.LoggerFactory
import scala.concurrent.{ExecutionContext, Future}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// StorageNodeImpl
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class RegionNodeImpl(storage:Storage)(implicit ctx:ExecutionContext) extends Service(ctx) with RegionNode {

	import RegionNodeImpl._

	// ==============================================================================================
	// 時刻同期
	// ==============================================================================================
	/**
	 * ノードとサービス間で時刻同期を行います。この機能はサービス側からの死活監視として呼び出されます。
	 *
	 * @param remote サービス側の現在時刻
	 * @return ノード側の現在時刻
	 */
	def sync(remote:Long):Future[Long] = Future(storage.sync(remote))

	// ==============================================================================================
	// UUID の参照
	// ==============================================================================================
	/**
	 * このリージョンノードが管理している全ブロックの UUID をブロック送信します。
	 */
	def lookup():Future[Unit] = withPipe { pipe =>
		logger.trace("lookup()")
		concurrent.future {
			storage.lookup{ uuid => pipe.sink.sendDirect(uuid.toByteArray) }
			pipe.sink.sendEOF()
		}
	}

	// ==============================================================================================
	// チェックサムの算出
	// ==============================================================================================
	/**
	 * 指定されたブロックのチェックサムを算出します。
	 * @param blockId ブロック ID
	 */
	def checksum(blockId:UUID, algorithm:String, challenge:Array[Byte]):Future[Array[Byte]] = {
		logger.debug(s"checksum($blockId,$algorithm,${challenge.toHexString}})")
		concurrent.future {
			storage.checksum(blockId, algorithm, challenge)
		}
	}

	// ==============================================================================================
	// ブロックの作成
	// ==============================================================================================
	/**
	 * 指定されたブロックを作成します。
	 * @param blockId ブロック ID
	 * @param size ブロックサイズ
	 */
	def create(blockId:UUID, size:Long):Future[Unit] = {
		logger.debug(s"create($blockId,$size)")
		concurrent.future {
			storage.create(blockId, size)
		}
	}

	// ==============================================================================================
	// ブロックの削除
	// ==============================================================================================
	/**
	 * 指定されたブロックを削除します。
	 * @param blockId ブロック ID
	 */
	def delete(blockId:UUID):Future[Unit] = {
		logger.debug(s"delete($blockId)")
		concurrent.future {
			storage.delete(blockId)
		}
	}

}

object RegionNodeImpl {
	private[RegionNodeImpl] val logger = LoggerFactory.getLogger(classOf[RegionNodeImpl])
}