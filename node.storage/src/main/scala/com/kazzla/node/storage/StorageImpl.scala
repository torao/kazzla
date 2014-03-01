/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node.storage

import com.kazzla.asterisk.{Service, Pipe}
import com.kazzla.core.io._
import com.kazzla.node.Storage
import java.io._
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption._
import java.util.UUID
import scala.Some
import org.slf4j.LoggerFactory
import scala.concurrent.{Promise, Future}
import scala.concurrent.ExecutionContext.Implicits.global

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// StorageNodeImpl
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class StorageImpl(dir:File) extends Service with Storage {

	import StorageImpl._

	// ==============================================================================================
	// UUID の参照
	// ==============================================================================================
	/**
	 * このストレージが管理しているブロックの全 UUID をパイプストリームに送出します。
	 */
	def lookup():Future[Unit] = {
		Pipe().foreach{ pipe =>
			logger.debug("lookup()")
			val out = new DataOutputStream(pipe.out)
			lookup(out, dir)
			out.flush()
		}
		Promise.successful(()).future
	}

	// ==============================================================================================
	// 実体ファイルの参照
	// ==============================================================================================
	/**
	 * 指定された UUID のブロックに対するローカルファイルを参照します。
	 * @return ブロックファイル
	 */
	private[this] def lookup(out:DataOutputStream, dir:File):Future[Unit] = {
		dir.listFiles().foreach{ file =>
			if(file.isFile){
				nameToUUID(file.getName) match {
					case Some(uuid) => out.writeUUID(uuid)
					case None => None
				}
			} else if(file.isDirectory){
				lookup(out, file)
			}
		}
		Promise.successful(()).future
	}

	// ==============================================================================================
	// チェックサムの参照
	// ==============================================================================================
	/**
	 * 指定されたブロックのチェックサムを算出します。
	 * @param blockId ブロック ID
	 */
	def checksum(blockId:UUID, algorithm:String, challenge:Array[Byte]):Future[Array[Byte]] = {
		logger.debug(s"checksum($blockId,$algorithm,${challenge.toHexString}})")
		val file = uuidToFile(dir, blockId)
		using(new FileInputStream(file)){ fis =>
			val is1 = new SequenceInputStream(new ByteArrayInputStream(challenge), fis)
			Promise.successful(is1.digest(algorithm)).future
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
	def create(blockId:UUID, size:Long):Future[Unit] = lockDirectory(blockId){
		logger.debug(s"create($blockId,$size)")
		val file = uuidToFile(dir, blockId)
		using(FileChannel.open(file.toPath, CREATE, WRITE)){ f =>
			using(f.lock()){ _ =>
				val buffer = ByteBuffer.allocateDirect(ReadWriteBufferSize)
				var written:Long = 0
				while(written < size){
					buffer.limit(math.min(ReadWriteBufferSize, size - written).toInt)
					buffer.position(0)
					val len = f.write(buffer)
					written += len
				}
			}
		}
		logger.debug(s"create new block: $blockId")
		Promise.successful(()).future
	}

	// ==============================================================================================
	// ブロックの読み込み
	// ==============================================================================================
	/**
	 * 指定されたブロックの領域を読み込みます。
	 * @param blockId ブロック ID
	 */
	def read(blockId:UUID, offset:Long, length:Int):Future[Unit] = Pipe() match {
		case Some(pipe) =>
			logger.debug(s"read($blockId,$offset,$length)")
			using(new FileInputStream(uuidToFile(dir, blockId))){ in =>
				val fc = in.getChannel
				using(fc.lock(offset, length, true)){ _ =>
					val buffer = new Array[Byte](ReadWriteBufferSize)
					in.skip(offset)
					copy(in, pipe.out, buffer, length)
				}
			}
			Promise.successful(()).future
		case None => ???
	}

	// ==============================================================================================
	// ブロックの書き込み
	// ==============================================================================================
	/**
	 * 指定されたブロックの領域を書き込みます。
	 * @param blockId ブロック ID
	 */
	def update(blockId:UUID, offset:Long, length:Int):Future[Unit] = Pipe() match {
		case Some(pipe) =>
			logger.debug(s"update($blockId,$offset,$length)")
			using(new RandomAccessFile(uuidToFile(dir, blockId), "rw")){ f =>
				val fc = f.getChannel
				using(fc.lock(offset, length, false)){ _ =>
					val buffer = new Array[Byte](ReadWriteBufferSize)
					f.seek(offset)
					copy(pipe.in, f, buffer, length)
				}
			}
			Promise.successful(()).future
		case None => ???
	}

	// ==============================================================================================
	// ブロックの削除
	// ==============================================================================================
	/**
	 * 指定されたブロックを削除します。
	 * @param blockId ブロック ID
	 */
	def delete(blockId:UUID):Future[Unit] = lockDirectory(blockId){
		logger.debug(s"delete($blockId)")
		val file = uuidToFile(dir, blockId)
		using(FileChannel.open(file.toPath, WRITE)){ fc =>
			using(fc.lock(0, Long.MaxValue, false)){ _ =>
				fc.truncate(0)
			}
		}
		file.delete()
		logger.debug(s"create new block: $blockId")
		Promise.successful(()).future
	}

	// ==============================================================================================
	// ディレクトリのロック
	// ==============================================================================================
	/**
	 * ブロックファイルの作成と削除のためディレクトリにロックをかけます。
	 */
	private[this] def lockDirectory[T](blockId:UUID)(f: =>T):T = {
		val lock = uuidToCDFile(dir, blockId)
		if(! lock.getParentFile.isDirectory){
			lock.getParentFile.mkdirs()
		}
		using(FileChannel.open(lock.toPath, CREATE, READ, WRITE)){ fc =>
			fc.lock(0, Long.MaxValue, false)
			f
		}
	}

}

object StorageImpl {
	private[StorageImpl] val logger = LoggerFactory.getLogger(classOf[StorageImpl])

	// ==============================================================================================
	// 入出力バッファサイズ
	// ==============================================================================================
	/**
	 * データの入出力用バッファサイズです。
	 */
	val ReadWriteBufferSize = 8 * 1024

	// ==============================================================================================
	// UUID の参照
	// ==============================================================================================
	/**
	 * ファイル名から UUID を参照します。
	 */
	def nameToUUID(name:String):Option[UUID] = try {
		Some(UUID.fromString(name))
	} catch {
		case ex:IllegalArgumentException => None
	}

	// ==============================================================================================
	// ファイルの参照
	// ==============================================================================================
	/**
	 * 指定された UUID に対するファイルを参照します。
	 */
	def uuidToFile(dir:File, uuid:UUID):File = {
		new File(uuidDirectory(dir, uuid), uuid.toString)
	}

	// ==============================================================================================
	// ファイルの参照
	// ==============================================================================================
	/**
	 * 指定された UUID に対するファイルを参照します。
	 */
	def uuidToCDFile(dir:File, uuid:UUID):File = {
		new File(uuidDirectory(dir, uuid), ".lock")
	}

	// ==============================================================================================
	// ディレクトリの参照
	// ==============================================================================================
	/**
	 * 指定された UUID のブロックを格納するディレクトリを参照します。
	 */
	private[this] def uuidDirectory(dir:File, uuid:UUID):File = {
		// UUID の MostSigBits は下位バイトに文字列表記上先頭のバイトが配置されている (リトルエンディアン)
		val h = (uuid.getMostSignificantBits >>> 56) & 0xFF
		val l = (uuid.getMostSignificantBits >>> 48) & 0xFF
		new File(dir, f"$h%02x${File.separator}%s$l%02x")
	}

}