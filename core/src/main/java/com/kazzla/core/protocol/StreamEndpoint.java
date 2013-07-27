/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.protocol;

import com.kazzla.core.io.IO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// StreamEndpoint
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class StreamEndpoint extends Endpoint {
	private static final Logger logger = LoggerFactory.getLogger(StreamEndpoint.class);

	// ==============================================================================================
	// 入力ストリーム
	// ==============================================================================================
	/**
	 * 入力ストリームです。
	 */
	private final InputStream in;

	// ==============================================================================================
	// 出力ストリーム
	// ==============================================================================================
	/**
	 * 出力ストリームです。
	 */
	private final OutputStream out;

	// ==============================================================================================
	// 出力ストリーム
	// ==============================================================================================
	/**
	 * 出力ストリームです。
	 */
	private final Worker worker;

	private volatile boolean closing = false;

	// ==============================================================================================
	// コンストラクタ
	// ==============================================================================================
	/**
	 * @param in 入力ストリーム
	 * @param out 出力ストリーム
	 */
	public StreamEndpoint(InputStream in, OutputStream out){
		super();
		this.in = in;
		this.out = out;
		this.worker = new Worker();
		this.worker.start();
		return;
	}

	// ==============================================================================================
	// 書き込みの実行
	// ==============================================================================================
	/**
	 * 指定されたバッファの内容を非同期で書き込みます。
	 * @param buffer 書き込む内容
	 */
	public Future<ByteBuffer> asyncWrite(final ByteBuffer buffer) throws IOException {
		asyncWriteAndIgnore(buffer);
		return new Future<ByteBuffer>() {
			@Override
			public boolean cancel(boolean mayInterruptIfRunning) { return false; }
			@Override
			public boolean isCancelled() { return false; }
			@Override
			public boolean isDone() { return true; }
			@Override
			public ByteBuffer get() { return buffer; }
			@Override
			public ByteBuffer get(long timeout, TimeUnit unit) { return get(); }
		};
	}

	// ==============================================================================================
	// 書き込みの実行
	// ==============================================================================================
	/**
	 * 指定されたバッファの内容を非同期で書き込みます。
	 * @param buffer 書き込む内容
	 */
	public void asyncWriteAndIgnore(ByteBuffer buffer) throws IOException {
		byte[] b = new byte[1024];
		while(buffer.hasRemaining()){
			int len = Math.max(buffer.remaining(), b.length);
			buffer.get(b, 0, len);
			out.write(b, 0, len);
		}
		return;
	}

	// ==============================================================================================
	// クローズ
	// ==============================================================================================
	/**
	 */
	@Override
	public void close() {
		closing = true;
		IO.close(in, out);
		return;
	}

	// ==============================================================================================
	// クローズ
	// ==============================================================================================
	/**
	 */
	private class Worker extends Thread {
		@Override
		public void run(){
			byte[] buffer = new byte[1024];
			try {
				while(! this.isInterrupted()){
					try {
						int len = in.read(buffer);
						if(len < 0){
							break;
						}
						receive(buffer, 0, len);
					} catch(InterruptedIOException ex){/* */}
				}
			} catch(IOException ex){
				if(! closing){
					logger.error("fail to read stream", ex);
				}
			}
			return;
		}
	}

}
