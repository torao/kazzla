/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import java.nio.ByteBuffer

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// RawBuffer
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * FIFO 特性を持った可変長バッファです。バッファの内容を直接参照することができます。
 * このインスタンスはスレッドセーフではありません。
 * @author Takami Torao
 * @param initialSize 内部バッファの初期サイズ
 */
private[async] class RawBuffer(initialSize:Int) {

	// ========================================================================
	// コンストラクタ
	// ========================================================================
	/**
	 * 初期状態で 4kB のバッファを持つインスタンスを構築します。
	 */
	def this() = this(4 * 1024)

	// ========================================================================
	// バイナリデータ
	// ========================================================================
	/**
	 * このバッファが保持しているバイナリデータです。
	 */
	private[async] var binary = new Array[Byte](initialSize)

	// ========================================================================
	// オフセット
	// ========================================================================
	/**
	 * 有効データの開始位置を表します。
	 */
	private[async] var offset = 0

	// ========================================================================
	// 長さ
	// ========================================================================
	/**
	 * 有効データの長さを表します。
	 */
	private[async] var length = 0

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
		System.arraycopy(buffer, offset, binary, this.offset, length)
		this.length += length
	}

	// ========================================================================
	// バイナリの連結
	// ========================================================================
	/**
	 * 指定されたバイナリデータをこのバッファに連結します。
	 * @param buffer バッファ
	 */
	def enqueue(buffer:ByteBuffer):Unit = {
		val len = buffer.remaining()
		ensureAppend(len)
		buffer.get(binary, this.offset, len)
		this.length += len
	}

	// ========================================================================
	// バイナリデータの取得
	// ========================================================================
	/**
	 * このインスタンスが保持している全てのバイナリを取得します。このメソッドの呼び出し後は
	 * バッファが空になります。
	 * @return この
	 */
	def dequeue():ByteBuffer = synchronized{
		val buffer = ByteBuffer.wrap(binary, offset, length)
		offset = 0
		length = 0
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
		val total = this.length + len
		if(offset + total < binary.length){
			return
		}
		// オフセットを調整すれば収まる場合
		if(total < binary.length){
			System.arraycopy(binary, offset, binary, 0, this.length)
			offset = 0
			return
		}
		// バッファの拡張が必要な場合
		val temp = new Array[Byte]((total * 1.25).toInt)
		System.arraycopy(binary, offset, temp, 0, this.length)
		binary = temp
		offset = 0
	}

}
