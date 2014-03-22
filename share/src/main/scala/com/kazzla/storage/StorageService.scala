/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.storage

import com.kazzla.asterisk.codec.MsgPackCodec
import com.kazzla.asterisk.{Block, Export}
import java.net.URI
import java.util.UUID
import org.msgpack.MessagePack
import scala.concurrent.Future
import com.kazzla._Error

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// StorageService
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
trait StorageService {

	// ==============================================================================================
	// ファイルステータスの参照
	// ==============================================================================================
	/**
	 * 指定されたファイルまたはディレクトリのステータスを参照します。
	 * @throws _Error `EC.FileNotFound`
	 */
	@Export(100)
	def status(path:String):Future[Status]

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
	@Export(101)
	def list(path:String):Future[Status]

	// ==============================================================================================
	// ファイルフラグメントの参照
	// ==============================================================================================
	/**
	 * 指定されたファイルのフラグメントを取得します。
	 */
	@Export(102)
	def location(path:String, offset:Long):Future[Fragment]

	// ==============================================================================================
	// ファイル領域の割り当て
	// ==============================================================================================
	/**
	 * 指定されたファイルに対する領域割り当てを行います。非同期パイプに対して残りのデータサイズ (不明な場合は負の値)
	 * を送信することでリージョンサービスはファイルの新しい領域を割り当てて [[Fragment]] で応答します。クライアント
	 * は割り当てられた領域すべてにデータを書き終えたら残りのデータサイズを送信して次の領域を割り当てます。
	 * @see [[com.kazzla.storage.StorageNode.write()]]
	 */
	@Export(103)
	def allocate(path:String, option:Int):Future[Status]

	// ==============================================================================================
	// ファイルの削除
	// ==============================================================================================
	/**
	 * 指定されたファイルを削除します。
	 * @return ファイルを削除した場合 true、ファイルが存在しなかった場合 false
	 */
	@Export(104)
	def delete(path:String):Future[Boolean]

	// ==============================================================================================
	// ディレクトリの作成
	// ==============================================================================================
	/**
	 * ディレクトリを作成します。
	 */
	@Export(105)
	def mkdir(path:String, force:Boolean):Future[Boolean]

}

object StorageService {
	def toByteArray(i:UUID):Array[Byte] = {
		val msgpack = new MessagePack()
		val packer = msgpack.createBufferPacker()
		packer.write(i.getMostSignificantBits)
		packer.write(i.getLeastSignificantBits)
		packer.toByteArray
	}
	def toUUID(a:Array[Byte]):UUID = {
		val msgpack = new MessagePack()
		val unpacker = msgpack.createBufferUnpacker(a)
		new UUID(unpacker.readLong(), unpacker.readLong())
	}

	object AllocateOption {
		/** ファイルが存在しない場合は新規に割り当てる。すでに存在する場合は失敗する。アトミック操作。 */
		val Create = 1 << 0
		/** ファイルが存在する場合に上書きする。ファイルが存在しない場合は失敗する。アトミック操作。 */
		val OverWrite = 1 << 1
		/** 既存のファイルの続きとして割り当てを行う。指定されていなかった場合は 0 バイト目からの割り当てとなる。 */
		val Append = 1 << 2
	}
}

case class Status(fileId:UUID, path:String, length:Long, createdAt:Long, updatedAt:Long, fstype:Byte, owner:UUID){
	lazy val uri = new URI(path)
}

object Status {
	object Type {
		/** ファイルであることを示します。 */
		val File:Byte = 0
		/** ディレクトリを示します。 */
		val Directory:Byte = 1
		/**
		 * シンボリックリンクを示します。
		 */
		val SymLink = 2
	}
}

case class Location(nodeId:UUID, host:String, port:Int)

/**
 * ファイルのデータフラグメント。
 */
case class Fragment(fileId:UUID, offset:Long, length:Int, blockId:UUID, blockOffset:Int, locations:Seq[Location]) {
	def toByteArray:Array[Byte] = {
		val msgpack = new MessagePack()
		val packer = msgpack.createBufferPacker()
		MsgPackCodec.encode(packer, this)
		packer.toByteArray
	}
}

object Fragment {
	def fromBlock(block:Block):Fragment = {
		val msgpack = new MessagePack()
		val unpacker = msgpack.createBufferUnpacker(block.payload, block.offset, block.length)
		MsgPackCodec.decode(unpacker).asInstanceOf[Fragment]
	}
}
