/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node.storage

import com.kazzla._
import com.kazzla.core.io._
import java.io._
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.nio.file.StandardOpenOption._
import java.util.UUID
import org.slf4j.LoggerFactory
import scala.Some

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Storage
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Storage(dir:File) {

	import Storage._

	/**
	 * サービスとのクロック差異です。remote = local + diff で表されます。
	 */
	private[this] var diff = 0L

	// ==============================================================================================
	// 時刻同期
	// ==============================================================================================
	/**
	 * ノードとサービス間で時刻同期を行います。この機能はサービス側からの死活監視として呼び出されます。
	 *
	 * @param remote サービス側の現在時刻
	 * @return ノード側の現在時刻
	 */
	def sync(remote:Long):Long = {
		val local = System.currentTimeMillis()
		diff = remote - local
		logger.debug(f"sync remote clock: remote = local + $diff%,d[msec]")
		local
	}

	// ==============================================================================================
	// UUID の参照
	// ==============================================================================================
	/**
	 * このストレージ内の全ブロック UUID をコールバックします。
	 */
	def lookup(f:(UUID)=>Unit):Unit = uuidTraverse(dir)(f)

	// ==============================================================================================
	// チェックサムの算出
	// ==============================================================================================
	/**
	 * 指定されたブロックのチェックサムを算出します。
	 *
	 * @param blockId ブロック ID
	 */
	def checksum(blockId:UUID, algorithm:String, challenge:Array[Byte]):Array[Byte] = {
		val file = uuidToFile(dir, blockId)
		using(new FileInputStream(file)){ fis =>
			val is1 = new SequenceInputStream(new ByteArrayInputStream(challenge), fis)
			is1.digest(algorithm)
		}
	}

	// ==============================================================================================
	// ブロックの作成
	// ==============================================================================================
	/**
	 * 指定されたブロックを作成します。
	 *
	 * @param blockId ブロック ID
	 * @param size ブロックサイズ
	 */
	def create(blockId:UUID, size:Long):Unit = lockDirectory(blockId) {
		val file = uuidToFile(dir, blockId)
		using(FileChannel.open(file.toPath, CREATE, WRITE)) { f =>
			using(f.lock()) { _ =>
				val buffer = ByteBuffer.allocateDirect(ReadWriteBufferSize)
				var written:Long = 0
				while (written < size) {
					buffer.limit(math.min(ReadWriteBufferSize, size - written).toInt)
					buffer.position(0)
					val len = f.write(buffer)
					written += len
				}
			}
		}
		logger.info(f"create new block: $blockId%s ($size%,dB)")
	}

	// ==============================================================================================
	// ブロックの削除
	// ==============================================================================================
	/**
	 * 指定されたブロックを削除します。
	 *
	 * @param blockId ブロック ID
	 */
	def delete(blockId:UUID):Unit = lockDirectory(blockId){
		val file = uuidToFile(dir, blockId)
		using(FileChannel.open(file.toPath, WRITE)){ fc =>
			using(fc.lock(0, Long.MaxValue, false)){ _ =>
				fc.truncate(0)
			}
		}
		file.delete()
		logger.info(s"delete block: $blockId")
	}

	// ==============================================================================================
	// データの読み込み
	// ==============================================================================================
	/**
	 * 指定されたブロック上の領域をストリームに出力します。
	 */
	def read(blockId:UUID, offset:Long, length:Int, out:OutputStream):Unit = {
		val block = uuidToFile(dir, blockId)
		using(FileChannel.open(block.toPath, StandardOpenOption.READ)){ in =>
			using(in.lock(offset, length, true)){ _ =>
				in.position(offset)
				val bufferSize = math.min(ReadWriteBufferSize, length)
				val outBuffer = new Array[Byte](bufferSize)
				val inBuffer = ByteBuffer.allocateDirect(bufferSize)
				var remain = length
				while(remain > 0) {
					inBuffer.clear()
					val len = in.read(inBuffer)
					if (len < 0) {
						throw EC.PrematureEOF(s"premature end of file")
					}
					inBuffer.flip()
					inBuffer.get(outBuffer, 0, len)
					out.write(outBuffer, 0, len)
					remain -= len
				}
			}
		}
	}

	// ==============================================================================================
	// データの書き込み
	// ==============================================================================================
	/**
	 * 指定された入力ストリームから読み出した内容をブロックに書き込みます。
	 */
	def write(blockId:UUID, offset:Long, length:Int, in:InputStream):Unit = {
		val block = uuidToFile(dir, blockId)
		using(FileChannel.open(block.toPath, StandardOpenOption.WRITE)){ out =>
			using(out.lock(offset, length, false)){ _ =>
				out.position(offset)
				val bufferSize = math.min(ReadWriteBufferSize, length)
				val inBuffer = new Array[Byte](bufferSize)
				val outBuffer = ByteBuffer.allocateDirect(bufferSize)
				var remain = length
				while(remain > 0){
					val len = in.read(inBuffer, 0, math.min(inBuffer.length, remain))
					if(len < 0){
						throw EC.PrematureEOF(s"premature end of file")
					}
					outBuffer.clear()
					outBuffer.put(inBuffer, 0, len)
					outBuffer.flip()
					out.write(outBuffer)
					remain -= len
				}
			}
		}
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

object Storage {
	private[Storage] val logger = LoggerFactory.getLogger(classOf[Storage])

	// ==============================================================================================
	// 入出力バッファサイズ
	// ==============================================================================================
	/**
	 * データの入出力用バッファサイズです。
	 */
	val ReadWriteBufferSize = 8 * 1024

	// ==============================================================================================
	// 保存階層数
	// ==============================================================================================
	/**
	 * ブロックデータの保存階層数です。inode を使用している Unix 系のファイルシステムでは、一つのディレクトリに格納
	 * できるファイル数が 32k 個程度の制限があるためです。
	 */
	private[this] val Depth = 2

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
	// ロックファイルの参照
	// ==============================================================================================
	/**
	 * 指定された UUID に対するロックファイルを参照します。
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
		new File(dir, uuid.toByteArray.take(Depth).map{ b => f"$b%02x" }.mkString(File.separator))
	}

	// ==============================================================================================
	// ブロックの参照
	// ==============================================================================================
	/**
	 * このインスタンスが保持しているブロックの UUID を参照します。
	 */
	def uuidTraverse(dir:File)(f:(UUID)=>Unit):Unit = {
		def rec(d:File, depth:Int):Unit = if(depth < Depth){
			d.listFiles().filter{ _.getName.matches("\\p{XDigit}{2}") }.filter{ _.isDirectory }.foreach{ h =>
				rec(h, depth + 1)
			}
		} else {
			d.listFiles().filter{ _.isFile }.flatMap { f => nameToUUID(f.getName) }.foreach{ f }
		}
		rec(dir, 0)
	}

}