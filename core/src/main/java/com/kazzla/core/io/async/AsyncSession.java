/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io.async;

import com.kazzla.core.io.IO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// AsyncSession
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public final class AsyncSession implements Closeable {
	private static final Logger logger = LoggerFactory.getLogger(AsyncSession.class);

	public final String name;
	public final Dispatcher dispatcher;
	private final ReadableByteChannel in;
	private final WritableByteChannel out;

	private final RawBuffer readBuffer = new RawBuffer();
	private final WriteQueue writeQueue = new WriteQueue();

	private Receiver receiver = null;

	// ==============================================================================================
	// コンストラクタ
	// ==============================================================================================
	/**
	 */
	AsyncSession(Dispatcher dispatcher, String name, ReadableByteChannel in, WritableByteChannel out) {
		this.dispatcher = dispatcher;
		this.name = name;
		if(in == null){
			throw new NullPointerException("in");
		}
		if(out == null){
			throw new NullPointerException("out");
		}
		SelectableChannel.class.cast(in);
		SelectableChannel.class.cast(out);
		this.in = in;
		this.out = out;
	}

	public Future<ByteBuffer> write(ByteBuffer buffer) throws IOException {
		try {
			Future<ByteBuffer> future = writeQueue.put(buffer);
			if(! dispatcher.hasInterestOptions((SelectableChannel)out, SelectionKey.OP_WRITE)){
				dispatcher.setInterestOptions((SelectableChannel)out, SelectionKey.OP_WRITE);
				logger.trace("writable listening on");
			}
			return future;
		} catch(InterruptedException ex){
			throw new IOException(ex);
		}
	}

	public void syncWrite(ByteBuffer buffer) throws IOException {
		try {
			write(buffer).get();
		} catch(InterruptedException ex){
			throw new IOException(ex);
		}
	}

	public void setWriteBufferSize(int writeBufferSize){
		if(writeBufferSize <= 0){
			throw new IllegalArgumentException(String.format("invalid write buffer size: %,d", writeBufferSize));
		}
		writeQueue.capacity = writeBufferSize;
	}

	public int getWriteBufferSize(){
		return writeQueue.capacity;
	}

	public void close() throws IOException {
		logger.trace("closing session");
		dispatcher.unregister((SelectableChannel) in);
		IO.close(in);
		logger.trace("session IN channel closed");
		dispatcher.unregister((SelectableChannel) out);
		IO.close(out);
		logger.trace("session OUT channel closed");
		logger.debug("session closed");
	}

	void register(Selector selector) throws IOException {
		((SelectableChannel)in).configureBlocking(false);
		SelectionKey ikey = ((SelectableChannel)in).register(selector, 0);
		ikey.attach(this);
		((SelectableChannel)out).configureBlocking(false);
		SelectionKey okey = ((SelectableChannel)out).register(selector, 0);
		okey.attach(this);

		// in == out の場合に options を上書きしないよう注意!
		// OP_WRITE はバッファにデータが入った後に設定する
		dispatcher.setInterestOptions((SelectableChannel)in, SelectionKey.OP_READ);
		logger.debug("session registered");
	}

	public void setReceiver(Receiver receiver){
		this.receiver = receiver;
		synchronized (readBuffer){
			if(readBuffer.length() > 0){
				receiver.arrivalBufferedIn(readBuffer);
			}
		}
	}

	boolean read(ByteBuffer buffer) throws IOException {
		buffer.clear();
		int len = in.read(buffer);
		if(len < 0){
			return false;
		}
		buffer.flip();
		synchronized (readBuffer){
			readBuffer.append(buffer);
		}
		if(receiver != null){
			receiver.arrivalBufferedIn(readBuffer);
		}
		return true;
	}

	void write() throws IOException {
		if(! writeQueue.write(out)){
			logger.trace("writable listening off");
			dispatcher.removeInterestOptions((SelectableChannel)out, SelectionKey.OP_WRITE);
		}
	}

}
