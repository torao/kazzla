/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io.irpc;

import com.kazzla.core.io.IO;
import com.kazzla.core.io.async.Dispatcher;

import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.concurrent.Executor;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Context
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class Context implements Closeable {
	private final Dispatcher dispatcher;
	private final Executor threads;

	public Context(int readBufferSize, Executor threads) throws IOException {
		this.dispatcher = new Dispatcher("", readBufferSize);
		this.threads = threads;
	}

	/**
	 * 新規の受動的セッションを構築します。
	 * サーバソケット {@code ServerSocket} で受け付けた {@code Socket} のように相手側からもたらされた接続に対
	 * するセッションです。
	 * セッションが能動的かどうかは以後のセッション操作において意識する必要はありません。
	 * @param in
	 * @param out
	 * @param codec
	 * @return
	 * @throws IOException
	 */
	Session newSession(String name, Object service, boolean active, ReadableByteChannel in, WritableByteChannel out, Codec codec) throws IOException {
		return new Session(this, service, active, dispatcher.newSession(name, in, out), codec);
	}

	public void close(){
		IO.close(dispatcher);
	}

	void execute(Runnable r){
		threads.execute(r);
	}

	public Session.Builder newSession(String name){
		return new Session.Builder(this, name);
	}

}
