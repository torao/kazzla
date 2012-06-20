/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import java.nio.ByteBuffer
import annotation.tailrec
import org.apache.log4j.Logger
import java.io.IOException

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// RawBuffer
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * FIFO 特性を持った可変長バッファです。隠蔽性より処理効率を優先しておりバッファの内容を
 * 直接参照することができます。
 * 初期バッファ容量に 0 以下の値を指定すると例外が発生します。
 * 内部バッファが上限容量を超える書き込みが行われようとした場合、バッファ内に有効な空き
 * 容量ができるまで書き込み処理がブロックされます。
 * 処理がブロックされた状態でブロック時間の上限を超えてもバッファに空きができない場合は
 * 例外が発生します。
 * @author Takami Torao
 * @param name バッファの名前(ログ出力用)
 * @param initialSize 内部バッファの初期容量
 * @param limitSize 内部バッファの上限容量
 * @param blockingLimit ブロック時間の上限 (ミリ秒)。0 を指定した場合は無制限
 * @throws IllegalArgumentException initialSize に 0 以下の値が指定された場合
 */
class RawBuffer(val name:String, val initialSize:Int, val limitSize:Int, val blockingLimit:Long) {
	import RawBuffer.logger
	// TODO 拡張したバッファの縮小

	// 初期バッファサイズの確認
	if(initialSize <= 0){
		throw new IllegalArgumentException("invalid initial buffer size: " + initialSize)
	}
	if(blockingLimit < 0){
		throw new IllegalArgumentException("invalid block limit: " + blockingLimit)
	}

	// ========================================================================
	// コンストラクタ
	// ========================================================================
	/**
	 * 初期状態で 4kB のバッファ容量を持つインスタンスを構築します。
	 */
	def this(name:String) = this(name, 4 * 1024, Int.MaxValue, 0)

	// ========================================================================
	// コンストラクタ
	// ========================================================================
	/**
	 * 指定された初期サイズのバッファ容量を持つインスタンスを構築します。
	 */
	def this(name:String, initialSize:Int) = this(name, initialSize, Int.MaxValue, 0)

	// ========================================================================
	// コンストラクタ
	// ========================================================================
	/**
	 * 指定された初期サイズのバッファ容量を持つインスタンスを構築します。
	 */
	def this(name:String, initialSize:Int, limitSize:Int) = this(name, initialSize, limitSize, 0)

	// ========================================================================
	// Mutex
	// ========================================================================
	/**
	 * enqueue / dequeue の整合性を確保するための Mutex です。
	 */
	private[this] val mutex = new Object()

	// ========================================================================
	// enqueue 待ち回数
	// ========================================================================
	/**
	 * このバッファ上で enqueue 待ちが発生した回数です。Int 値の範囲で循環します。
	 */
	@volatile
	private[this] var _blockingCount:Int = 0

	// ========================================================================
	// バイナリデータ
	// ========================================================================
	/**
	 * このバッファが保持しているバイナリデータです。
	 */
	private[this] var binary = new Array[Byte](initialSize)

	// ========================================================================
	// オフセット
	// ========================================================================
	/**
	 * 有効データの開始位置を表します。
	 */
	private[this] var _offset = 0

	// ========================================================================
	// 有効なデータ長
	// ========================================================================
	/**
	 * バッファ内の有効データの長さを表します。
	 */
	private[this] var _length = 0

	// ========================================================================
	// バッファの参照
	// ========================================================================
	/**
	 * バイナリデータのバッファを直接参照します。このバッファへの変更操作はこのインスタンス
	 * が保持する内部バッファへ影響を与えます。
	 */
	def raw:Array[Byte] = binary

	// ========================================================================
	// 有効なデータオフセットの参照
	// ========================================================================
	/**
	 * このバッファが保持している有効なデータの開始位置を返します。
	 */
	def offset:Int = _offset

	// ========================================================================
	// 有効なデータ長の参照
	// ========================================================================
	/**
	 * このバッファが保持している有効なデータ長を返します。
	 */
	def length:Int = _length

	// ========================================================================
	// キャパシティ
	// ========================================================================
	/**
	 * このバッファのキャパシティを参照します。
	 */
	def capacity:Int = binary.length

	// ========================================================================
	// enqueue ブロック回数
	// ========================================================================
	/**
	 * このバッファがいっぱいになり enqueue 操作がブロックされた回数を示すカウンターです。
	 * Int の範囲で循環します。
	 */
	def blockingCount:Int = _blockingCount

	// ========================================================================
	// バイナリの連結
	// ========================================================================
	/**
	 * 指定されたバイナリデータをこのバッファに連結します。
	 * @param buffer バッファ
	 */
	def enqueue(buffer:Array[Byte]):Unit = enqueue(buffer, 0, buffer.length)

	// ========================================================================
	// バイナリの連結
	// ========================================================================
	/**
	 * 指定されたバイナリデータをこのバッファに連結します。
	 * 長さに 0 を指定した場合は何も起きません。
	 * @param buffer バッファ
	 * @param offset バッファ内での連結データの開始位置
	 * @param length 連結データの長さ
	 */
	def enqueue(buffer:Array[Byte], offset:Int, length:Int):Unit = mutex.synchronized{
		var concat = 0
		while(length - concat > 0){
			val len = requireAndWait(length - concat)
			/*
			if(logger.isTraceEnabled){
				logger.trace((for(i <- 0 until len) yield {
					com.kazzla.debug.makeDebugChar((buffer(offset + concat + i) & 0xFF).toChar)
				}).mkString("\"", "", "\"") + " (" + len + "B)")
			}
			*/
			logger.trace("offset=%d, length=%d".format(_offset, _length))
			System.arraycopy(buffer, offset + concat, binary, _offset + _length, len)
			this._length += len
			concat += len
		}
	}

	// ========================================================================
	// バイナリの連結
	// ========================================================================
	/**
	 * 指定されたバッファの現在位置から remaining バイトのバイナリをこのバッファに連結し
	 * ます。
	 * @param buffer 連結するバッファ
	 */
	def enqueue(buffer:ByteBuffer):Unit = mutex.synchronized{
		do {
			val len = requireAndWait(buffer.remaining())
			buffer.get(binary, _offset + _length, len)
			this._length += len
		} while(buffer.remaining() > 0)
	}

	// ========================================================================
	// バイナリデータの取得
	// ========================================================================
	/**
	 * このインスタンスが保持している全てのバイナリを取得します。このメソッドの呼び出し後は
	 * バッファが空になります。
	 * @return このバッファが保持しているすべての有効データ
	 */
	def dequeue():ByteBuffer = dequeue(_length)

	// ========================================================================
	// バイナリデータの取得
	// ========================================================================
	/**
	 * このインスタンスが保持しているバイナリから指定された長さを取得します。
	 * 指定サイズが現在のバッファ内の有効サイズより大きい場合は例外が発生します。
	 * @return 指定されたサイズのバッファ
	 */
	def dequeue(size:Int):ByteBuffer = mutex.synchronized{
		if(size > _length || size < 0){
			throw new IllegalArgumentException("invalid dequeue size: " + size + ", max " + _length)
		}
		val buffer = ByteBuffer.wrap(binary, _offset, size)
		_length -= size
		if(_length == 0){
			_offset = 0
		} else {
			_offset += size
		}
		mutex.notify()
		buffer
	}

	// ========================================================================
	// バッファのクリア
	// ========================================================================
	/**
	 * このバッファが保持している内容をクリアします。
	 */
	def clear():Unit = synchronized{
		_length = 0
		_offset = 0
	}

	// ========================================================================
	// バッファサイズの要求
	// ========================================================================
	/**
	 * このインスタンスが保持しているバッファから指定されたサイズの領域を要求します。呼び出
	 * し側は現在のオフセットから返値が示す長さのデータを格納することができます。
	 * 内部バッファを上限まで使い尽くしている場合、このメソッドは処理をブロックして空きが
	 * 発生するまで待機します。
	 * @param len 連結する長さ
	 */
	@tailrec
	private[this] def requireAndWait(len:Int):Int = {
		assert(Thread.holdsLock(mutex))

		// バッファに空きがある場合
		if(_offset + _length < binary.length){
			return scala.math.min(len, binary.length - _offset - _length)
		}
		assert(_offset + _length == binary.length)

		// オフセットを調整すれば空きができる場合
		if(_offset > 0){
			val available = _offset
			System.arraycopy(binary, _offset, binary, 0, this._length)
			_offset = 0
			return scala.math.min(len, available)
		}
		assert(_length == binary.length)

		// バッファの拡張が可能な場合
		if(binary.length < limitSize){
			val newBufferSize = scala.math.min(((_length + len) * 1.25).toInt, limitSize)
			if(logger.isTraceEnabled){
				logger.trace("[%s] expanding buffer space from %,d to %,d bytes".format(name, binary.length, newBufferSize))
			}
			val temp = new Array[Byte](newBufferSize)
			System.arraycopy(binary, _offset, temp, 0, this._length)
			binary = temp
			assert(_offset == 0)
			return scala.math.min(len, binary.length - _length)
		}
		assert(binary.length == limitSize)
		if(logger.isDebugEnabled){
			logger.debug("[%s] %,d bytes buffer full, waiting buffer space".format(name, limitSize))
		}

		// バッファに空きができるまで待機して再実行
		_blockingCount += 1
		assert(blockingLimit >= 0)
		mutex.wait(blockingLimit)
		if(_length == limitSize){
			throw new IOException("blocking timed out for buffer \"%s\"; %,dmsec".format(name, blockingLimit))
		}
		requireAndWait(len)
	}

}

object RawBuffer {
	private[async] val logger = Logger.getLogger(classOf[RawBuffer])
}