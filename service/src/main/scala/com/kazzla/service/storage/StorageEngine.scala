/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service.storage

import com.kazzla.EC
import com.kazzla.service.Context
import com.kazzla.service.util._
import com.kazzla.storage.fs.{FileType, Permission, WriteOption, Path}
import com.kazzla.storage.{Fragment, Location}
import java.sql.ResultSet
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// StorageEngine
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class StorageEngine(accountId:UUID, sessionId:UUID)(implicit ctx:Context) {

	/**
	 * このアカウントのルートノード。
	 */
	private[this] val root = {
		ctx.db.single("id from if_inodes where account_id=? and parent_id is null", accountId){ _.getUUID(0) } match {
			case Some(id) => id
			case None => throw EC.InternalServerError("filesystem not found")
		}
	}

	/**
	 * このセッションが保持しているロック数。
	 */
	private[this] val lockCount = new AtomicInteger(0)

	// ==============================================================================================
	// セッション終了時処理
	// ==============================================================================================
	/**
	 * セッションが終了したときにクリーンアップ処理を行います。
	 */
	def cleanup():Unit = ctx.db.trx {
		// セッションに関連するロックの削除
		ctx.db.deleteFrom("fs_locks where session_id=?", sessionId)
	}

	// ==============================================================================================
	// inode の参照
	// ==============================================================================================
	/**
	 * 指定されたファイルまたはディレクトリの inode を参照します。
	 */
	def status(path:String):Inode = {
		finger(path){ case (parentId, name) =>
			selectInode("*", parentId, name, forUpdate = false)(inodeMapper(Path.canonical(path))) match {
				case Some(inode) => inode
				case None => throw EC.FileNotFound(s"file or directory not found: $path")
			}
		}
	}

	// ==============================================================================================
	// ファイル名一覧の参照
	// ==============================================================================================
	/**
	 * 指定されたディレクトリ直下に存在するファイル名の一覧を参照します。
	 *
	 * @param path ディレクトリのパス
	 * @return ディレクトリのステータス
	 */
	def list(path:String)(cont:(String)=>Unit):Inode = {
		val inode = status(path)
		if(! inode.isDirectory){
			throw EC.NotDirectory()
		}
		ctx.db.select("name from fs_inodes where parent_id=?", inode.id){ rs =>
			cont(rs.getString(1))
		}
		inode
	}

	// ==============================================================================================
	// ファイルフラグメントの参照
	// ==============================================================================================
	/**
	 * 指定されたファイルのフラグメントロケーションを取得します。
	 */
	def location(path:String, offset:Long):Fragment = {
		val inode = status(path)
		ctx.db.single("offset,length,block_id,block_offset from fs_fragments where inode_id=? and offset>=? order by offset limit 1", inode.id, offset){ rs =>
			val blockId = rs.getUUID("block_id")
			val nodeIds = ctx.db.select("node_id from storage_blocks where id=?", blockId){ _.getUUID(1) } ++
				ctx.db.select("node_id from storage_replications where block_id=?", blockId){ _.getUUID(1) }
			val locations:Seq[Location] = nodeIds.map{ nodeId =>
				ctx.db.select("endpoints,proxy from node_sessions where node_id=?", nodeId){ r =>
					Option(r.getString("proxy")).getOrElse(r.getString("endpoints")).split("\\s,\\s").map{ i =>
						val Array(host, port) = i.split(":", 2)
						Location(nodeId, host, port.toInt)
					}
				}.flatten
			}.flatten.toSeq
			// fileId:UUID, blockId:UUID, offset:Long, length:Int, locations:Seq[Location]
			val diff = (rs.getLong("offset") - offset).toInt
			Fragment(inode.id, offset, rs.getInt("length") - diff, blockId, rs.getInt("block_offset"), locations)
		} match {
			case Some(f) => f
			case None => throw EC.BadLocation()
		}
	}

	// ==============================================================================================
	// inode の作成
	// ==============================================================================================
	/**
	 * 指定されたパスの inode を作成します。
	 */
	def create(path:String, ftype:Char, option:Int):Inode = {
		val (parent, name) = split(path)
		selectForUpdate(parent){ inode =>
			selectInode("*", inode.id, name, forUpdate = true){ inodeMapper(path) } match {
				case Some(i) =>
					if((option & WriteOption.CreateNew) != 0){
						throw EC.FileExists(path)
					}
					// 既存の内容を削除
					if(ftype == FileType.File || (option & WriteOption.Append) == 0){
						ctx.db.deleteFrom("fs_fragments where inode_id=?", i.id)
					}
					i
				case None =>
					if((option & WriteOption.Overwrite) != 0){
						throw EC.FileNotFound(path)
					}
					// 新規の inode を作成
					val newId = ctx.newInodeId
					val now = System.currentTimeMillis()
					val i = Inode(id = newId, path = Path.canonical(path), accountId = accountId,
						parentId = inode.id, ftype = ftype, name = name, size = 0L,
						permission = (Permission.Read | Permission.Write | (if(ftype == FileType.Directory) Permission.Execute else 0)).toByte,
						ctime = now, utime = now, atime = now, lock = None, lockShared = 0 )
					ctx.db.insertInto("fs_inodes",
						"id" -> i.id, "account_id" -> i.accountId, "parent_id" -> i.parentId, "ftype" -> i.ftype,
						"name" -> i.name, "size" -> i.size, "permission" -> i.permission,
						"ctime" -> i.ctime, "utime" -> i.utime, "atime" -> i.atime,
						"lock_session" -> None, "lock_timestamp" -> None, "lock_shared" -> i.lockShared)
					i
			}
		}
	}

	// ==============================================================================================
	// ファイル領域の割り当て
	// ==============================================================================================
	/**
	 * 指定されたファイルに対する領域割り当てを行います。非同期パイプに対して残りのデータサイズ (不明な場合は負の値)
	 * を送信することでリージョンサービスはファイルの新しい領域を割り当てて [[Fragment]] で応答します。クライアント
	 * は割り当てられた領域すべてにデータを書き終えたら残りのデータサイズを送信して次の領域を割り当てます。
	 */
	def allocate(inodeId:UUID, offset:Long, length:Long)(cont:(Fragment)=>Unit):Unit = {
		var allocated = 0
		var index = 0
		ctx.db.select(
			"storage_blocks.id as block_id, count(*) as frags, sum(length) as used" +
				" from storage_blocks left outer join fs_fragments" +
				" on storage_blocks.id=fs_fragments.block_id" +
				" where storage_blocks.account_id=?" +
				" group by storage_blocks.id" +
				" order by frags, used desc", accountId, Region.BlockSize) {
			rs =>
				val blockId = rs.getUUID("block_id")
				if (index == 0 && rs.getInt("used") == Region.BlockSize) {
					throw EC.DiskFull()
				}

				// ブロックをロックするため独立トランザクションを開始
				val fragment = ctx.db.separatedTrx {
					// プライマリノードの接続先を参照 (ブロックをロック)
					val addrs = ctx.db.single("node_id from storage_blocks where block_id=? for update", blockId) { _.getUUID(1) } match {
						case Some(nodeId) =>
							ctx.db.single(s"proxy, endpoints from node_sessions where id=?", nodeId) {
								r =>
									Option(r.getString("proxy")).getOrElse(r.getString("endpoints")).split("\\s*,\\s*").map {
										hp =>
											val Array(host, port) = hp.split(":", 2)
											Location(nodeId, host, port.toInt)
									}
							} match {
								case Some(a) => a
								case None => throw EC.PrimaryNodeDown()
							}
						case None => throw EC.InternalServerError()
					}

					// ブロック内の一番大きな空き領域を決定
					var tail = 0
					var maxSpace = (0, 0)
					ctx.db.select("block_offset, length from fs_fragments where block_id=? order by block_offset", blockId) {
						r2 =>
							val offset = rs.getInt("block_offset")
							val space = offset - tail
							if (maxSpace._2 < space) {
								maxSpace = (tail, space)
							}
							tail = offset + rs.getInt("length")
					}
					if (maxSpace._2 < Region.BlockSize - tail) {
						maxSpace = (tail, Region.BlockSize - tail)
					}

					// 一番大きな空き領域に領域を割り当て
					val len = math.min(math.min(length - allocated, Region.AllocationUnit), maxSpace._2).toInt
					ctx.db.insertInto("fs_fragments",
						"inode_id" -> inodeId,
						"offset" -> (offset + allocated),
						"length" -> len,
						"block_id" -> blockId,
						"block_offset" -> maxSpace._1,
						"fixed" -> 0
					)

					// fileId:UUID, offset:Long, length:Int, blockId:UUID, blockOffset:Int, locations:Seq[Location]
					val frag = Fragment(inodeId, offset + allocated, len, blockId, maxSpace._1, addrs)
					allocated += len
					frag
				}

				cont(fragment)

				// 要求されたすべてを割り当てたら終了
				if (allocated == length) {
					// TODO 末尾再帰になっていない
					return
				}
				index += 1
		}

		// すべてのブロックに割り当てても残っている場合は再実行
		allocate(inodeId, offset + allocated, length - allocated)(cont)
	}

	// ==============================================================================================
	// inode の削除
	// ==============================================================================================
	/**
	 * 指定されたパスのファイルまたはディレクトリを削除します。
	 */
	def delete(path:String):Unit = {
		lock(path, share = false)
		val (parent, name) = split(path)
		selectForUpdate(parent){ parentInode =>
			selectInode("*", parentInode.id, name, forUpdate = true){ inodeMapper(path) } match {
				case Some(i) =>
					i.ftype match {
						case FileType.File =>
							ctx.db.deleteFrom("fs_fragments where node_id=?", i.id)
						case FileType.Directory =>
							if(ctx.db.scalar("count(*) from fs_inodes where parent_id=?", i.id) > 0){
								throw EC.DirectoryNotEmpty(path)
							}
					}
					ctx.db.deleteFrom("fs_locks where inode_id=?", i.id)
					ctx.db.deleteFrom("fs_inodes where id=?", i.id)
				case None => throw EC.FileNotFound(path)
			}
		}
	}

	// ==============================================================================================
	// ディレクトリの作成
	// ==============================================================================================
	/**
	 * 指定されたディレクトリを作成します。
	 */
	def mkdir(path:String, force:Boolean):Inode = if(force) {
		var inode:Inode = null
		Path.canonicalSplit(path).foldLeft(""){ case (parent, name) =>
			val current = s"$parent${Path.Separator}$name"
			inode = create(current, FileType.Directory, WriteOption.Default)
			current
		}
		inode
	} else {
		create(path, FileType.Directory, WriteOption.Default)
	}

	// ==============================================================================================
	// ファイルのロック
	// ==============================================================================================
	/**
	 * 指定されたファイルをロックします。
	 */
	def lock(path:String, share:Boolean):UUID = selectForUpdate(path){ inode =>
		if(inode.ftype != FileType.File){
			throw EC.NotFile(path)
		}
		if(lockCount.getAndIncrement >= Region.MaxLocksForSession){
			lockCount.decrementAndGet()
			throw EC.TooManySessionLocks()
		}
		val locks = ctx.db.select("exclusive, session_id from fs_locks where inode_id=?", inode.id){ rs =>
			(rs.getUUID("session_id"), rs.getByte("exclusive") != 0)
		}
		if((share && locks.exists{ _._2 }) || (! share && locks.size > 0)){
			throw EC.FileLocked()
		}
		if(locks.size >= Region.MaxLocksForFile){
			throw EC.TooManyFileLocks()
		}
		val newId = ctx.newLockId
		ctx.db.insertInto("fs_locks",
			"id" -> newId,
			"inode_id" -> inode.id,
			"session_id" -> sessionId,
			"exclusive" -> (if(share) 0 else 1).toByte,
			"created_at" -> System.currentTimeMillis()
		)
		newId
	}

	// ==============================================================================================
	// ロックの解除
	// ==============================================================================================
	/**
	 * 指定されたロックを解除します。
	 */
	def unlock(id:UUID):Boolean = {
		ctx.db.deleteFrom("fs_locks where id=? and session_id=?", id, sessionId) == 1
	}

	// ==============================================================================================
	// inode のレコードロック
	// ==============================================================================================
	/**
	 * 指定された inode にレコードロックを行います。
	 */
	private[this] def selectForUpdate[T](path:String)(f:(Inode)=>T):T = ctx.db.trx {
		finger(path){ case (parentId, name) =>
			selectInode("*", parentId, name, forUpdate = true)(inodeMapper(Path.canonical(path))) match {
				case Some(i) => f(i)
				case None => throw EC.FileNotFound(s"file not found: $path")
			}
		}
	}

	// ==============================================================================================
	// inode 位置の特定
	// ==============================================================================================
	/**
	 * 指定されたパスの親ディレクトリ UUID を取得します。
	 */
	private[this] def finger[T](path:String)(f:(UUID, String)=>T):T = ctx.db.trx {
		val cmps = Path.canonicalSplit(path)
		val lastName = cmps.last
		val pid = cmps.dropRight(1).foldLeft(root){ case (parent, name) =>
			selectInode("id", parent, name, forUpdate = false){ _.getUUID(1) } match {
				case None => throw EC.FileNotFound(s"directory not found: $path")
				case Some(nodeId) => nodeId
			}
		}
		f(pid, lastName)
	}

	// ==============================================================================================
	// inode 位置の特定
	// ==============================================================================================
	/**
	 * 指定されたパスの親ディレクトリ UUID を取得します。
	 */
	private[this] def selectInode[T](columns:String, parentId:UUID, name:String, forUpdate:Boolean)(mapper:(ResultSet)=>T):Option[T] = {
		ctx.db.single(columns + " from fs_inodes" +
			" where account_id=? and parent_id=? and name=?" +
			(if(forUpdate) " for update" else ""),
			accountId, parentId, name)(mapper)
	}

	private[this] def inodeMapper(path:String)(rs:ResultSet):Inode = {
		// id:UUID, path:String, accountId:UUID, parentId:UUID, ftype:Char, name:String, size:Long, permission:Byte, ctime:Long, utime:Long, atime:Long
		val lockSession = rs.getInt("lock_session")
		val lock = if(rs.wasNull()){
			None
		} else {
			Some(Inode.Lock(lockSession, rs.getLong("lock_timestamp")))
		}
		Inode(
			id = rs.getUUID("id"),
			path = path,
			accountId = rs.getUUID("account_id"),
			parentId = rs.getUUID("parent_id"),
			ftype = rs.getChar("ftype"),
			name = rs.getString("name"),
			size = rs.getLong("size"),
			permission = rs.getByte("permission"),
			ctime = rs.getLong("ctime"),
			utime = rs.getLong("utime"),
			atime = rs.getLong("atime"),
			lock = lock,
			lockShared = rs.getInt("lock_shared")
		)
	}

	private[this] def split(path:String):(String,String) = {
		val cmps = Path.canonicalSplit(path)
		(cmps.dropRight(1).mkString(Path.Separator, Path.Separator, ""), cmps.last)
	}

}
