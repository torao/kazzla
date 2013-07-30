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
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Dispatcher
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class Dispatcher implements Closeable {
	private static final Logger logger = LoggerFactory.getLogger(Dispatcher.class);

	private static AtomicInteger sequence = new AtomicInteger();

	public final String name;
	private final Selector selector;

	private final AtomicBoolean closed = new AtomicBoolean(false);
	private final BlockingQueue<Task> queue = new LinkedBlockingQueue<>();

	private final ByteBuffer readBuffer;
	private final Worker worker;

	// ==============================================================================================
	// コンストラクタ
	// ==============================================================================================
	/**
	 */
	public Dispatcher() throws IOException {
		this(String.valueOf(sequence.getAndAdd(1)), 8 * 1024);
	}

	// ==============================================================================================
	// コンストラクタ
	// ==============================================================================================
	/**
	 * @param readBufferSize データが読み出し可能になったチャネルに対して使用する共有読み出しバッファのサイズ
	 */
	public Dispatcher(String name, int readBufferSize) throws IOException {
		this.name = name;
		this.selector = Selector.open();
		this.readBuffer = ByteBuffer.allocateDirect(readBufferSize);

		this.worker = new Worker();
		this.worker.start();
	}

	// ==============================================================================================
	// ディスパッチャーの終了
	// ==============================================================================================
	/**
	 * このディスパッチャーを終了します。
	 */
	public void close(){
		logger.debug("closing dispatcher");
		worker.interrupt();
		try {
			worker.join();
		} catch(InterruptedException ex){/* */}

		// 残っているセッションをすべてクローズ
		for(SelectionKey key: selector.keys()){
			IO.close((Endpoint) key.attachment());
		}
		IO.close(selector);

		// TODO キューに残っているタスクをすべて削除
		logger.debug("dispatcher closed");
	}

	public Endpoint newSession(String name, ReadableByteChannel in, WritableByteChannel out) throws IOException {
		return newSession(new Endpoint(this, name, in, out));
	}

	private Endpoint newSession(final Endpoint session) throws IOException {
		try {
			IOException ex = syncExec(new Task<IOException>(){
				protected IOException execute(){
					try {
						session.register(selector);
						logger.trace("session \"" + session.name + "\" bind to dispatcher");
					} catch(IOException ex){
						IO.close(session);
						return ex;
					}
					return null;
				}
			});
			if(ex != null){
				throw ex;
			}
		} catch(InterruptedException ex){
			throw new IOException(ex);
		}
		return session;
	}

	void unregister(final SelectableChannel channel) throws IOException {
		try {
			syncExec(new Task<Object>(){
				protected IOException execute(){
					channel.keyFor(selector).cancel();
					logger.trace("channel canceled");
					return null;
				}
			});
		} catch(InterruptedException ex){
			throw new IOException(ex);
		}
	}

	void setInterestOptions(final SelectableChannel channel, final int options){
		post(new Task<Object>(){
			public Object execute(){
				SelectionKey key = channel.keyFor(selector);
				key.interestOps(key.interestOps() | options);
				return null;
			}
		});
	}

	boolean hasInterestOptions(SelectableChannel channel, int options){
		return (channel.keyFor(selector).interestOps() & options) == options;
	}

	void removeInterestOptions(final SelectableChannel channel, final int options){
		post(new Task<Object>(){
			public Object execute(){
				SelectionKey key = channel.keyFor(selector);
				key.interestOps(key.interestOps() & ~options);
				return null;
			}
		});
	}

	private <T> Future<T> post(Task<T> task) {
		synchronized(queue){
			if(closed.get()){
				task.future.setFailure(new IOException("dispatcher closed"));
				return task.future;
			}
			if(worker == Thread.currentThread()){
				task.run();
				return task.future;
			}
			queue.add(task);
		}
		selector.wakeup();
		logger.trace("post task");
		return task.future;
	}

	private <T> T syncExec(Task<T> task) throws InterruptedException {
		return post(task).get();
	}

	// ==============================================================================================
	// ==============================================================================================
	/**
	 */
	private void execute(){
		logger.debug("starting asynchronous dispatcher thread");
		while(! Thread.currentThread().isInterrupted()){
			try {
				selector.select(3 * 1000);
			} catch(IOException ex){
				logger.error("asynchronous select failed", ex);
				break;
			}
			Set<SelectionKey> keys = selector.selectedKeys();

			// キューの処理をすべて実行
			while(queue.size() > 0){
				queue.remove().run();
			}

			Iterator<SelectionKey> it = keys.iterator();
			while(it.hasNext()){
				SelectionKey key = it.next();
				it.remove();
				Endpoint session = (Endpoint)key.attachment();
				try {
					if(! key.isValid()){
						logger.debug("invalid key detected for session: " + session.name);
						session.close();
					} else if(key.isReadable()){
						if(! session.read(readBuffer)){
							logger.debug("connection closed by peer, closing session: " + session.name);
							session.close();
						}
					} else if(key.isWritable()){
						session.write();
					} else {
						logger.error("unexpected asynchronous event detected: " + key);
						session.close();
					}
				} catch(IOException ex){
					logger.debug("", ex);
					IO.close(session);
				}
			}
		}

		// キュー内のタスクをすべて停止し新規タスクの受け付け
		synchronized(queue){
			closed.set(true);
			while(queue.size() > 0){
				queue.remove().future.setFailure(new IOException("operation closed"));
			}
		}
		logger.debug("exiting asynchronous dispatcher thread");
	}

	private class Worker extends Thread {
		public Worker(){
			setName("AsynchronousDispatcher[" + name + "]");
		}
		@Override
		public void run(){
			execute();
		}
	}

	/**
	 * Select スレッド内で実行する処理。
	 * @param <T>
	 */
	private abstract class Task<T> {
		public final Future<T> future = new Future<>();
		public void run(){
			try {
				future.setSuccess(execute());
			} catch(Throwable ex){
				future.setFailure(ex);
			}
		}
		protected abstract T execute();
	}

}
