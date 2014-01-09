/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// WriteQueue
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class WriteQueue {
	private static final Logger logger = LoggerFactory.getLogger(WriteQueue.class);

	private final BlockingQueue<Item> queue = new LinkedBlockingQueue<>();
	private Item processing = null;
	private int length = 0;
	public int capacity = 1 * 1024 * 1024;

	public int length(){ return length; }

	public Future<ByteBuffer> put(ByteBuffer buffer) throws InterruptedException {
		Future<ByteBuffer> future = new Future<>();
		put(new Item(buffer, future));
		return future;
	}

	public int putWithoutFuture(ByteBuffer buffer) throws InterruptedException {
		return put(new Item(buffer, null));
	}

	private synchronized int put(Item item) throws InterruptedException {
		// このアイテムの追加でキャパシティを越える場合はブロック
		// TODO キャパシティを越えるデータブロックが指定された場合は?
		while(length + item.buffer.remaining() > capacity){
			logger.debug(String.format("block writing: %,d + %,d > %,d", length, item.buffer.remaining(), capacity));
			this.wait();
		}
		if(processing == null){
			processing = item;
		} else {
			queue.add(item);
		}
		length += item.buffer.remaining();
		return length;
	}

	public synchronized boolean write(WritableByteChannel out) throws IOException {
		if(processing != null){
			int len = out.write(processing.buffer);
			logger.trace("WritableByteChannel.write() = " + len);
			if(! processing.buffer.hasRemaining()){
				if(processing.future != null){
					processing.future.setSuccess(processing.buffer);
				}
				if(queue.isEmpty()){
					processing = null;
				} else {
					processing = queue.remove();
				}
			}
			length -= len;
			return length > 0;
		} else {
			assert(length == 0);
			return false;
		}
	}

	private static class Item {
		public final ByteBuffer buffer;
		public final Future<ByteBuffer> future;
		public Item(ByteBuffer buffer, Future<ByteBuffer> future){
			this.buffer = buffer;
			this.future = future;
		}
	}

}
