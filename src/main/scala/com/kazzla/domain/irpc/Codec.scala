/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.irpc

import java.io._
import com.kazzla.domain.async.RawBuffer
import org.msgpack.MessagePack
import org.msgpack.packer.Packer
import org.msgpack.`type`._
import org.msgpack.unpacker.Unpacker

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Codec
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * プロシジャーコールの転送単位をバイナリ化するトレイトです。
 * インスタンスはスレッドセーフです。
 * @author Takami Torao
 */
trait Codec {

	// ========================================================================
	// ストリームヘッダの参照
	// ========================================================================
	/**
	 * このコーデックを使用したストリームのヘッダを参照します。
	 */
	def header:Array[Byte] = Array[Byte]()

	// ========================================================================
	// ストリームヘッダの参照
	// ========================================================================
	/**
	 * このコーデックを使用したストリームのヘッダを参照します。
	 */
	def footer:Array[Byte] = Array[Byte]()

	// ========================================================================
	// ユニットセパレータの参照
	// ========================================================================
	/**
	 * このコーデックを使用したストリーム転送の各転送ユニットごとの区切りを参照します。
	 */
	def separator:Array[Byte] = Array[Byte]()

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
// MsgPackCodec
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * MessagePack 形式のコーデックです。
 * @author Takami Torao
 */
object MsgPackCodec extends Codec {
	Codec.register("messagepack", this)

	val VERSION:Byte = 0

	val CONTROL:Byte = 0
	val OPEN:Byte = 1
	val CLOSE:Byte = 2
	val BLOCK:Byte = 3

	private[this] val EMPTY = Array[Byte](0, 0, 0, 0)

	// ========================================================================
	// バッファの作成
	// ========================================================================
	/**
	 * 指定された転送単位をバイナリに変換します。
	 */
	def pack(unit:Transferable):Array[Byte] = {
		val out = new ByteArrayOutputStream()

		// 先頭に設定する長さの分だけスキップ
		out.write(EMPTY)

		// 転送ユニットをシリアライズ
		val mpack = new MessagePack()
		val packer = mpack.createPacker(out)
		packer.write(VERSION)
		unit match {
			case open:Open =>
				packer.write(OPEN)
				packer.write(open.id)
				packer.write(open.timeout)
				packer.write(open.callback)
				packer.write(open.name)
				pack(packer, open.args:_*)
			case close:Close =>
				packer.write(CLOSE)
				packer.write(close.id)
				packer.write(close.code.id.toByte)
				packer.write(close.message)
				pack(packer, close.args:_*)
			case block:Block =>
				packer.write(BLOCK)
				packer.write(block.id)
				packer.write(block.sequence)
				packer.write(block.binary)
			case control:Control =>
				packer.write(CONTROL)
				packer.write(control.id)
				packer.write(control.code)
				pack(packer, control.args:_*)
			case unknown =>
				throw new RemoteException("unsupported transfer-unit: %s".format(unknown))
		}
		packer.flush()
		val binary = out.toByteArray

		// ビッグエンディアンで 4 バイトの長さを設定
		val len = binary.length - 4
		for(i <- 0 until 4){
			binary(3 - i) = ((len >> (i * 8)) & 0xFF).toByte
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
		if(buffer.length < 4){
			return None
		}

		// 転送単位の長さを取得
		val len =
			((buffer.raw(buffer.offset + 0) & 0xFF) << 24) |
			((buffer.raw(buffer.offset + 1) & 0xFF) << 16) |
			((buffer.raw(buffer.offset + 2) & 0xFF) <<  8) |
			((buffer.raw(buffer.offset + 3) & 0xFF) <<  0)

		// まだすべてのデータを受信していなければ何もしない
		if(buffer.length < len + 4){
			return None
		}

		val mpack = new MessagePack()
		val packer = mpack.createBufferUnpacker(buffer.raw, buffer.offset, buffer.length)
		val version = packer.readByte()
		val unit = packer.readByte() match {
			case OPEN =>
				val id = packer.readLong()
				val timeout = packer.readLong()
				val callback = packer.readBoolean()
				val name = packer.readString()
				val args = unpackArgs(packer)
				Open(id, timeout, callback, name, args:_*)
			case CLOSE =>
				val id = packer.readLong()
				val code = Close.Code(packer.readByte() & 0xFF)
				val error = packer.readString()
				val args = unpackArgs(packer)
				Close(id, code, error, args)
			case BLOCK =>
				val id = packer.readLong()
				val seq = packer.readInt()
				val bin = packer.readByteArray()
				Block(id, seq, bin)
			case CONTROL =>
				val id = packer.readLong()
				val code = packer.readByte()
				val args = unpackArgs(packer)
				Control(id, code, args)
			case unknown =>
				throw new RemoteException("unsupported transfer-unit type: 0x%02X".format(unknown & 0xFF))
		}
		Some(unit)
	}

	// ========================================================================
	// 可変データのパック
	// ========================================================================
	/**
	 * 指定された可変長データをパックします。
	 */
	private[this] def pack(packer:Packer, args:Any*):Unit = {
		packer.writeArrayBegin(args.length)
		args.foreach { value => packer.write(toValue(value)) }
		packer.writeArrayEnd()
	}

	// ========================================================================
	// 可変データのパック
	// ========================================================================
	/**
	 * 指定された可変長データをパックします。
	 */
	private[this] def unpackArgs(packer:Unpacker):Seq[Any] = {
		val length = packer.readArrayBegin()
		val args = for(i <- 0 until length) yield {
			fromValue(packer.readValue())
		}
		packer.readArrayEnd()
		args.toSeq
	}

	// ========================================================================
	// 値の変換
	// ========================================================================
	/**
	 * 指定された値を MessagePack 用の値に変換します。
	 */
	private[this] def toValue(v:Any):Value = v match {
		case null => ValueFactory.createNilValue()
		case flag:Boolean => ValueFactory.createBooleanValue(flag)
		case num:Byte => ValueFactory.createIntegerValue(num)
		case num:Short => ValueFactory.createIntegerValue(num)
		case num:Int => ValueFactory.createIntegerValue(num)
		case num:Long => ValueFactory.createIntegerValue(num)
		case num:Float => ValueFactory.createFloatValue(num)
		case num:Double => ValueFactory.createFloatValue(num)
		case ch:Char => ValueFactory.createRawValue(ch.toString)
		case str:String => ValueFactory.createRawValue(str)
		case bin:Array[Byte] => ValueFactory.createRawValue(bin)
		case map:Map[_,_] => ValueFactory.createMapValue(map.map{ case (key,value) =>
				Array(toValue(key), toValue(value))
			}.toList.flatten.toArray, true)
		case list:Seq[_] => ValueFactory.createArrayValue(list.map{ toValue(_) }.toArray)
		case list:Array[_] => ValueFactory.createArrayValue(list.map{ toValue(_) }.toArray)
	}

	// ========================================================================
	// 可変データのパック
	// ========================================================================
	/**
	 * 指定された可変長データをパックします。
	 */
	private[this] def fromValue(v:Value):Any = v match {
		case value if value.isNilValue => null
		case value if value.isBooleanValue => value.asBooleanValue().getBoolean
		case value if value.isIntegerValue => value.asIntegerValue().getLong
		case value if value.isFloatValue => value.asFloatValue().getDouble
		case value if value.isRawValue => value.asRawValue().getByteArray
		case value if value.isArrayValue =>
			val array = value.asArrayValue()
			(for(i <- 0 until array.size()) yield {
				fromValue(value.asArrayValue().get(i))
			}).toSeq
		case value if value.isMapValue =>
			val map = value.asMapValue()
			val kv = map.getKeyValueArray
			(for(i <- 0 until kv.length by 2) yield {
				(fromValue(kv(i)), fromValue(kv(i+1)))
			}).toMap
	}

}
