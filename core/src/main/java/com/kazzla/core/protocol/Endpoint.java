/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.protocol;

import com.kazzla.core.io.async.RawBuffer;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Endpoint
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public abstract class Endpoint implements Closeable {

	// ==============================================================================================
	// 読み込み処理
	// ==============================================================================================
	/**
	 * 読み込み処理です。
	 */
	private Consumer consumer = null;

	// ==============================================================================================
	// 読み込みバッファ
	// ==============================================================================================
	/**
	 * 読み込みバッファです。
	 */
	private final RawBuffer readBuffer = new RawBuffer();

	// ==============================================================================================
	// コンストラクタ
	// ==============================================================================================
	/**
	 */
	protected Endpoint(){
		return;
	}

	// ==============================================================================================
	// コンストラクタ
	// ==============================================================================================
	/**
	 */
	public void setConsumer(Consumer consumer){
		this.consumer = consumer;
		if(consumer != null){
			consumer.receive(readBuffer);
		}
		return;
	}

	// ==============================================================================================
	// 書き込みの実行
	// ==============================================================================================
	/**
	 * 指定されたバッファの内容を書き込みます。
	 * @param buffer 書き込む内容
	 */
	public void write(ByteBuffer buffer) throws IOException {
		Future<ByteBuffer> future = asyncWrite(buffer);
		try {
			future.get();
		} catch(Exception ex){
			throw new IOException(ex);
		}
		return;
	}

	// ==============================================================================================
	// 書き込みの実行
	// ==============================================================================================
	/**
	 * 指定されたバッファの内容を非同期で書き込みます。
	 * @param buffer 書き込む内容
	 */
	public abstract Future<ByteBuffer> asyncWrite(ByteBuffer buffer) throws IOException;

	// ==============================================================================================
	// 書き込みの実行
	// ==============================================================================================
	/**
	 * 指定されたバッファの内容を非同期で書き込みます。
	 * @param buffer 書き込む内容
	 */
	public abstract void asyncWriteAndIgnore(ByteBuffer buffer) throws IOException;

	// ==============================================================================================
	// 書き込みの実行
	// ==============================================================================================
	/**
	 * 指定されたバッファの内容を非同期で書き込みます。
	 * @param buffer 書き込む内容
	 */
	protected void receive(byte[] buffer, int offset, int length){
		readBuffer.append(buffer, offset, length);
		if(consumer != null){
			consumer.receive(readBuffer);
		}
		return;
	}

	// ==============================================================================================
	// クローズ
	// ==============================================================================================
	/**
	 * クローズを行います。
	 */
	public abstract void close() throws IOException;

	public interface Consumer {
		public void receive(RawBuffer buffer);
	}

}
