/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node.volume

import java.nio.file._
import java.util._
import org.slf4j._
import com.kazzla.core.io._
import java.nio._
import scala.collection.JavaConversions._
import java.io._
import scala._
import com.kazzla.node.Volume

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Volume
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class VolumeImpl(val dir:Path) extends Volume {
	import VolumeImpl._

	// ==============================================================================================
	// 実体ファイルの参照
	// ==============================================================================================
	/**
	 * 指定された UUID のブロックに対するローカルファイルを参照します。
	 * @return ブロックファイル
	 */
	def lookup():Iterable[UUID] = {
		Files.newDirectoryStream(dir).map{ _.getFileName.toString }.filter{ _.endsWith(blockExtension) }.map{ f =>
			try {
				Some(UUID.fromString(f.substring(0, f.length - blockExtension.length)))
			} catch {
				case ex:IllegalArgumentException =>
					logger.debug("invalid block-file exists: " + f)
					None
			}
		}.filter{ _.isDefined }.map{ _.get }
	}

	// ==============================================================================================
	// 実体ファイルの参照
	// ==============================================================================================
	/**
	 * 指定された UUID のブロックに対するファイルへのパスを参照します。
	 * @param blockId ブロック ID
	 * @return ブロックファイル
	 */
	def getPath(blockId:UUID):Path = {
		dir.resolve(blockId.toString + blockExtension)
	}

	// ==============================================================================================
	// ブロックの作成
	// ==============================================================================================
	/**
	 * 指定されたブロックを作成します。
	 * @param blockId ブロック ID
	 * @param size ブロックサイズ
	 */
	def create(blockId:UUID, size:Long):Unit = {
		using(Files.newByteChannel(getPath(blockId), StandardOpenOption.CREATE, StandardOpenOption.WRITE)){ file =>
			if(file.size() > size){
				file.truncate(size)
			} else if(file.size() < size){
				file.position(size - 1)
				file.write(ByteBuffer.allocate(1))
			}
		}
	}

	// ==============================================================================================
	// ブロックの読み込み
	// ==============================================================================================
	/**
	 * 指定されたブロックの領域を読み込みます。
	 * @param blockId ブロック ID
	 */
	def read(blockId:UUID, pos:Long, buffer:Array[Byte], offset:Int, length:Int):Unit = {
		using(Files.newByteChannel(getPath(blockId), StandardOpenOption.READ)){ in =>
			in.position(pos)
			in.read(ByteBuffer.wrap(buffer, offset, length))
		}
	}

	// ==============================================================================================
	// ブロックの書き込み
	// ==============================================================================================
	/**
	 * 指定されたブロックの領域を書き込みます。
	 * @param blockId ブロック ID
	 */
	def update(blockId:UUID, pos:Long, buffer:Array[Byte], offset:Int, length:Int):Unit = {
		using(Files.newByteChannel(getPath(blockId), StandardOpenOption.WRITE)){ out =>
			out.position(pos)
			out.write(ByteBuffer.wrap(buffer, offset, length))
		}
	}

	// ==============================================================================================
	// ブロックの削除
	// ==============================================================================================
	/**
	 * 指定されたブロックを削除します。
	 * @param blockId ブロック ID
	 */
	def delete(blockId:UUID):Unit = {
		Files.delete(getPath(blockId))
	}

	// ==============================================================================================
	// チェックサムの参照
	// ==============================================================================================
	/**
	 * 指定されたブロックのチェックサムを算出します。
	 * @param blockId ブロック ID
	 */
	def checksum(blockId:UUID, challenge:Array[Byte]):Array[Byte] = {
		using(Files.newInputStream(getPath(blockId), StandardOpenOption.READ)){ in =>
			val is = new SequenceInputStream(new ByteArrayInputStream(challenge), in)
			IO.getMessageDigest(is, com.kazzla.core.protocol.Volume.CHECKSUM_ALGORITHM)
		}
	}

}

object VolumeImpl {
	private[Volume] val logger = LoggerFactory.getLogger(classOf[VolumeImpl])

	val blockExtension = ".kbl"
}