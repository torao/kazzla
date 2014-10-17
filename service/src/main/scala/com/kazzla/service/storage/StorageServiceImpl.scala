/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service.storage

import com.kazzla.EC
import com.kazzla.asterisk._
import com.kazzla.service.Context
import com.kazzla.service.domain.Domain
import com.kazzla.service.util._
import com.kazzla.storage.fs.FileType
import com.kazzla.storage.{Fragment, Status, StorageService}
import java.util.concurrent.LinkedBlockingQueue
import org.slf4j.LoggerFactory
import scala.annotation.tailrec
import scala.concurrent.Future

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// StorageService
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class StorageServiceImpl(domain:Domain)(implicit ctx:Context) extends Service(ctx.threadPool) with StorageService {
	implicit val _threadpool = ctx.threadPool

	def startup(session:Session):Unit = try {
		// クライアント証明書からアカウントを特定
		session.accountId
		session.storage
		session.onClosed ++ { _ =>
			session.storage.cleanup()
			ctx.invalidate()
		}
	} catch {
		case ex:Throwable =>
			session.close()
	}

	// ==============================================================================================
	// ファイルステータスの参照
	// ==============================================================================================
	/**
	 * 指定されたファイルまたはディレクトリのステータスを参照します。
	 */
	override def status(path:String):Future[Status] = >> { _.status(path).toStatus }

	// ==============================================================================================
	// ファイル一覧の参照
	// ==============================================================================================
	/**
	 * 指定されたディレクトリ直下に存在するファイルの一覧をストリームで参照します。非同期パイプに対してファイル名の
	 * 文字列をブロック送信します。
	 *
	 * @param path ディレクトリのパス
	 * @return ディレクトリのステータス
	 */
	override def list(path:String):Future[Status] = >>> { (storage, pipe) =>
		val inode = storage.list(path){ name =>
			pipe.sink.sendDirect(name.getBytes("UTF-8"))
		}
		pipe.sink.sendEOF()
		inode.toStatus
	}

	// ==============================================================================================
	// ファイルフラグメントの参照
	// ==============================================================================================
	/**
	 * 指定されたファイルのフラグメントロケーションを取得します。
	 */
	override def location(path:String, offset:Long):Future[Fragment] = >> { storage =>
		storage.location(path, offset)
	}

	// ==============================================================================================
	// ファイル領域の割り当て
	// ==============================================================================================
	/**
	 * 指定されたファイルに対する領域割り当てを行います。非同期パイプに対して残りのデータサイズ (不明な場合は負の値)
	 * を送信することでリージョンサービスはファイルの新しい領域を割り当てて [[com.kazzla.storage.Fragment]] で
	 * 応答します。クライアントは割り当てられた領域すべてにデータを書き終えたら残りのデータサイズを送信して次の領域を
	 * 割り当てます。
	 */
	def allocate(path:String, option:Int):Future[Status] = withPipe { pipe =>
		val queue = new LinkedBlockingQueue[Option[Long]]()
		pipe.src.foreach{ block => queue.put(if(block.isEOF) None else Some(block.toLong)) }
		>>> { (storage, pipe) =>
			val inode = storage.create(path, FileType.File, option)
			@tailrec
			def f():Unit = queue.take() match {
				case Some(size) =>
					storage.allocate(inode.id, inode.size, size){ f => pipe.sink.sendDirect(f.toByteArray) }
					f()
				case None => None
			}
			f()
			pipe.sink.sendEOF()
			inode.toStatus
		}
	}

	// ==============================================================================================
	// ファイルの削除
	// ==============================================================================================
	/**
	 * 指定されたファイルを削除します。
	 * @return ファイルを削除した場合 true、ファイルが存在しなかった場合 false
	 */
	override def delete(path:String):Future[Boolean] = >> { storage =>
		try {
			storage.delete(path)
			true
		} catch {
			case Abort(EC.FileNotFound.code, _, _) => false
			case Abort(EC.DirectoryNotEmpty.code, _, _) => false
		}
	}

	// ==============================================================================================
	// ディレクトリの作成
	// ==============================================================================================
	/**
	 * ディレクトリを作成します。
	 */
	def mkdir(path:String, force:Boolean):Future[Boolean] = >> { storage =>
		try {
			storage.mkdir(path, force)
			true
		} catch {
			case Abort(EC.FileNotFound.code, _, _) => false
			case Abort(EC.DirectoryNotEmpty.code, _, _) => false
		}
	}

	/**
	 * 指定されたファイルまたはディレクトリのステータスを参照します。
	 */

	private[this] def >>> [T](f:(StorageEngine,Pipe)=>T):Future[T] = withPipe{ pipe =>
		concurrent.future {
			ctx.db.trx{ f(pipe.session.storage, pipe) }
		}
	}

	private[this] def >> [T](f:(StorageEngine)=>T):Future[T] = >>> { (storage, _) => f(storage) }

}

object StorageServiceImpl {
	private[StorageServiceImpl] val logger = LoggerFactory.getLogger(classOf[StorageServiceImpl])
}
