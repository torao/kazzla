/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import java.nio.ByteBuffer

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// RawBuffer
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * FIFO 特性を持った可変長バッファです。
 * @author Takami Torao
 * @param initialSize 内部バッファの初期サイズ
 */
class RawBuffer(initialSize:Int) {
	// TODO buffer size limit need to block enqueue
	if(initialSize <= 0){
		throw new IllegalArgumentException("invalid initial buffer size: " + initialSize)
	}

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
	// コンストラクタ
	// ========================================================================
	/**
	 * 初期状態で 4kB のバッファを持つインスタンスを構築します。
	 */
	def this() = this(4 * 1024)

	// ========================================================================
	// バッファの参照
	// ========================================================================
	/**
	 * バイナリデータのバッファを直接参照します。
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
	// バイナリの連結
	// ========================================================================
	/**
	 * 指定されたバイナリデータをこのバッファに連結します。
	 * @param buffer バッファ
	 * @param offset バッファ内での連結データの開始位置
	 * @param length 連結データの長さ
	 */
	def enqueue(buffer:Array[Byte], offset:Int, length:Int):Unit = synchronized{
		ensureAppend(length)
		System.arraycopy(buffer, offset, binary, _offset + _length, length)
		this._length += length
	}

	// ========================================================================
	// バイナリの連結
	// ========================================================================
	/**
	 * 指定されたバイナリデータをこのバッファに連結します。
	 * @param buffer バッファ
	 */
	def enqueue(buffer:ByteBuffer):Unit = synchronized{
		val length = buffer.remaining()
		ensureAppend(length)
		buffer.get(binary, _offset + _length, length)
		this._length += length
	}

	// ========================================================================
	// バイナリデータの取得
	// ========================================================================
	/**
	 * このインスタンスが保持している全てのバイナリを取得します。このメソッドの呼び出し後は
	 * バッファが空になります。
	 * @return このバッファが保持しているすべての有効データ
	 */
	def dequeue():ByteBuffer = synchronized{ dequeue(_length) }

	// ========================================================================
	// バイナリデータの取得
	// ========================================================================
	/**
	 * このインスタンスが保持しているバイナリから指定された長さを取得します。
	 * 指定サイズが現在のバッファ内の有効サイズより小さい場合は例外が発生します。
	 * @return 指定されたサイズのバッファ
	 */
	def dequeue(size:Int):ByteBuffer = synchronized{
		if(size > _length){
			throw new IllegalArgumentException("dequeue size too long: " + size + "/" + _length)
		}
		val buffer = ByteBuffer.wrap(binary, _offset, size)
		_length -= size
		if(_length == 0){
			_offset = 0
		} else {
			_offset += size
		}
		buffer
	}

	// ========================================================================
	// バッファサイズの保証
	// ========================================================================
	/**
	 * このインスタンスが保持しているバッファの有効領域へ指定されたサイズのバイナリデータを
	 * 連結できることを保証します。
	 * @param len 連結する長さ
	 */
	private[this] def ensureAppend(len:Int){

		// 現在の状態で指定サイズのデータを連結できる場合
		val total = this._length + len
		if(_offset + total < binary.length){
			return
		}

		// オフセットを調整すれば収まる場合
		if(total < binary.length){
			System.arraycopy(binary, _offset, binary, 0, this._length)
			_offset = 0
			return
		}

		// バッファの拡張が必要な場合
		val temp = new Array[Byte]((total * 1.25).toInt)
		System.arraycopy(binary, _offset, temp, 0, this._length)
		binary = temp
		_offset = 0
	}

}
