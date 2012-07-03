/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.irpc

import com.kazzla.domain.async.RawBuffer
import java.nio.{ByteOrder, ByteBuffer}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Codec
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * プロシジャーコールの転送単位をバイナリ化するトレイトです。
 * インスタンスはスレッドセーフです。
 * @author Takami Torao
 */
trait class Codec {

	// ========================================================================
	// バッファの作成
	// ========================================================================
	/**
	 * 指定された転送単位をバイナリに変換します。
	 */
	def pack(unit:Transferable):Array[Byte]

	// ========================================================================
	// バッファの復元
	// ========================================================================
	/**
	 * 指定されたバッファから転送単位を復元します。バッファにオブジェクトを復元可能なデータ
	 * が揃っていない場合は None を返します。
	 */
	def unpack(buffer:RawBuffer):Option[Transferable]

}

object Codec {

	// ========================================================================
	// ファクトリ
	// ========================================================================
	/**
	 * 名前にマッピングされたコーデックのインスタンスです。
	 */
	private[this] var codecs = Map[String,Codec]()

	// ========================================================================
	// コーデックの登録
	// ========================================================================
	/**
	 * 指定された名前に対するコーデックを登録します。
	 * @param name コーデック名
	 * @param codec コーデック
	 */
	def register(name:String, codec:Codec):Unit = synchronized{
		codecs += (name.toLowerCase() -> codec)
	}

	// ========================================================================
	// コーデックの参照
	// ========================================================================
	/**
	 * 指定された名前に対するコーデックを参照します。コーデック名は大文字と小文字を区別し
	 * ません。名前に該当するコーデックが定義されていない場合は None を返します。
	 * @param name コーデック名
	 * @return コーデック
	 */
	def getCodec(name:String):Option[Codec] = codecs.get(name.toLowerCase())

}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// JavaCodec
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * プロシジャーコールの転送単位をバイナリ化する抽象クラスです。
 * インスタンスはスレッドセーフです。
 * @author Takami Torao
 */
object JavaCodec extends Codec {
	import java.io._
	Codec.register("java-serialize", this)

	// ========================================================================
	// バッファの作成
	// ========================================================================
	/**
	 * 指定された転送単位をバイナリに変換します。
	 */
	def pack(unit:Transferable):Array[Byte] = {

		// 先頭に 4 バイト追加
		val buffer = new ByteArrayOutputStream()
		for(i <- 0 until 4){ buffer.write(0) }

		// オブジェクトのシリアライズ
		val out = new ObjectOutputStream(buffer)
		out.writeObject(out)
		out.flush()

		// 先頭に長さを設定
		val binary = buffer.toByteArray
		val len = binary.length
		for(i <- 0 until 4){
			binarh(i) = ((len >> (8 * i)) & 0xFF).toByte
		}

		binary
	}

	// ========================================================================
	// バッファの復元
	// ========================================================================
	/**
	 * 指定されたバッファから転送単位を復元します。バッファにオブジェクトを復元可能なデータ
	 * が揃っていない場合は None を返します。
	 */
	def unpack(buffer:RawBuffer):Option[Transferable] = {

		// 先頭の 4 バイトで長さを参照
		if(buffer.size() < 4){

			return None
		}
		val buffer = new ByteArrayOutputStream()
		(0 until 4).foreach { buffer.write(0) }

		// オブジェクトのシリアライズ
		val out = new ObjectOutputStream(buffer)
		out.writeObject(out)
		out.flush()

		// 先頭に長さを設定
		val binary = buffer.toByteArray
		val len = binary.length
		for(i <- 0 until 4){
			binarh(i) = ((len >> (8 * i)) & 0xFF).toByte
		}

		binary
	}

}
