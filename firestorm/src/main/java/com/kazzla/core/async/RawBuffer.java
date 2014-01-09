/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// RawBuffer
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class RawBuffer {
	private static final Logger logger = LoggerFactory.getLogger(RawBuffer.class);
	private byte[] buffer = null;
	private int offset = 0;
	private int length = 0;

	public RawBuffer(){
		this(1024);
	}

	public RawBuffer(int defaultBufferSize){
		this.buffer = new byte[defaultBufferSize];
	}

	public int length(){
		return length;
	}

	public ByteBuffer toByteBuffer(){
		return ByteBuffer.wrap(buffer, offset, length);
	}

	public void consume(int length){
		this.offset += length;
		this.length -= length;
	}

	public void append(byte[] buffer, int offset, int length){
		int total = prepare(length);
		System.arraycopy(buffer, offset, this.buffer, this.offset + this.length, length);
		this.length = total;
	}

	public void append(ByteBuffer buffer){
		int total = prepare(buffer.remaining());
		buffer.get(this.buffer, this.offset + this.length, buffer.remaining());
		this.length = total;
	}

	private int prepare(int length){
		int total = this.length + length;
		if(total > this.buffer.length){
			// バッファ拡張の必要がある場合
			byte[] temp = new byte[(int)(total * 1.2)];		// TODO 最適な拡張プランを考える
			logger.trace("extending internal buffer " + buffer.length + " to " + temp.length + ", need " + total);
			System.arraycopy(this.buffer, this.offset, temp, 0, this.length);
			this.buffer = temp;
			this.offset = 0;
		} else if(this.offset + total > this.buffer.length){
			// 読み込み位置の最適化を行う必要がある場合
			System.arraycopy(this.buffer, this.offset, this.buffer, 0, this.length);
			this.offset = 0;
		}
		return total;
	}

}
