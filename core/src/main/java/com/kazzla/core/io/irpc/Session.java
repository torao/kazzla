/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io.irpc;

import com.kazzla.core.io.IO;
import com.kazzla.core.io.async.AsyncSession;
import com.kazzla.core.io.async.Receiver;
import com.kazzla.core.io.async.RawBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Session
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class Session implements Closeable {
	private static final Logger logger = LoggerFactory.getLogger(Session.class);
	private final Context context;
	final AsyncSession asyncSession;
	final Codec codec;

	private final int pipeIdMask;
	private static AtomicInteger sequence = new AtomicInteger();

	private Map<Integer, Pipe> pipes = new HashMap<>();

	private final Stub stub;

	public final String name;

	Session(Context context, Object service, boolean active, AsyncSession asyncSession, Codec codec) {
		this.context = context;
		this.pipeIdMask = (active? 0: Pipe.UNIQUE_MASK);
		this.asyncSession = asyncSession;
		this.codec = codec;
		this.stub = new Stub(service);
		this.name = asyncSession.name;

		asyncSession.setReceiver(new Receiver() {
			@Override
			public void arrivalBufferedIn(RawBuffer buffer) {
				read(buffer);
			}
		});
	}

	/**
	 * 指定されたリモートプロシジャを呼び出しパイプを作成します。
	 * @param function リモートプロシジャの識別子
	 * @param params
	 * @return
	 * @throws IOException
	 */
	public Pipe open(short function, Object... params) throws IOException {
		Pipe pipe;
		synchronized (pipes) {
			int id = nextPipeId();
			pipe = new Pipe(id, this);
			pipes.put(id, pipe);
		}
		pipe.open(function, params);
		return pipe;
	}

	public void close() throws IOException {
		asyncSession.close();
	}

	void onPipeClose(int pipeId){
		synchronized(pipes){
			pipes.remove(pipeId);
		}
	}

	public <T> T getInterface(Class<T> clazz) {
		return clazz.cast(Proxy.newProxyInstance(
				Thread.currentThread().getContextClassLoader(),
				new Class[]{clazz}, new Skelton()));
	}

	private void read(RawBuffer buffer) {
		logger.trace("read(" + buffer.length() + ")");
		try {
			Object packet = codec.decode(buffer);
			if(packet instanceof Pipe.Open){
				receiveOpen((Pipe.Open)packet);
			} else if(packet instanceof Pipe.Close){
				receiveClose((Pipe.Close)packet);
			} else if(packet instanceof Pipe.Block){
				receiveBlock((Pipe.Block)packet);
			} else if(packet != null){
				throw new CodecException("unexpected object decoded: " + packet.getClass().getName());
			}
		} catch(CodecException ex){
			logger.error("protocol error", ex);
			IO.close(this);
		}
	}

	private void receiveOpen(final Pipe.Open open) {
		logger.trace("-> Open(" + open.id + "," + open.method + "," + Arrays.toString(open.params) + "):" + name);
		if((open.id & Pipe.UNIQUE_MASK) == pipeIdMask){
			throw new CodecException("invalid active/passive pipe-id specified in open pipe");
		}
		final Pipe pipe = new Pipe(open.id, this);
		synchronized(pipes){
			if(pipes.containsKey(pipe.id)){
				throw new CodecException(String.format("pipe-id %s already used", pipe.id));
			}
			pipes.put(pipe.id, pipe);
		}

		// TODO スレッドプールに呼び出し先情報を渡して処理を実行する
		context.execute(new Runnable(){
			public void run(){
				Pipe.Close close = stub.invoke(open);
				try {
					pipe.close(close);
				} catch(IOException ex){
					logger.error("", ex);
				}
			}
		});
	}

	private void receiveClose(Pipe.Close close) {
		logger.trace("-> Close(" + close.id + "," + close.result + "," + close.errorMessage + "):" + name);
		Pipe pipe;
		synchronized(pipes){
			pipe = pipes.remove(close.id);
		}
		if(pipe == null){
			logger.debug(String.format("pipe not found: %d", close.id));
		} else {
			pipe.setClose(close);
		}
	}

	private void receiveBlock(Pipe.Block block) {
		logger.trace("-> Block(" + block.id + "," + block.binary.length + "):" + name);
		Pipe pipe;
		synchronized(pipes){
			pipe = pipes.get(block.id);
		}
		if(pipe == null){
		} else {
			pipe.postBlock(block);
		}
	}

	private int nextPipeId() {
		assert (Thread.holdsLock(pipes));
		while (true) {
			int id = (sequence.addAndGet(1) & ~ Pipe.UNIQUE_MASK) | pipeIdMask;
			if (!pipes.containsKey(id)) {
				return id;
			}
		}
	}

	private class Skelton implements InvocationHandler {
		public Object invoke(Object proxy, Method method, Object... params) throws InvocationTargetException {
			RemoteProcedure rpc = method.getAnnotation(RemoteProcedure.class);
			if (rpc == null) {
				String msg = String.format("%s.%s() has no remote procedure annotation", method.getDeclaringClass(), method.getName());
				throw new InvocationTargetException(new NoSuchMethodError(msg));
			}

			try {
				Pipe pipe = open(rpc.value(), params);
				Pipe.Close result = pipe.waitForClose(0);		// TODO タイムアウトの設定
				return result.result;
			} catch(Exception ex){
				throw new InvocationTargetException(ex);
			}
		}
	}

	class Stub {
		private Object service;
		private Map<Short, Method> functions = new HashMap<>();

		public Stub(Object service) {
			this.service = service;
			for (Class<?> c : service.getClass().getInterfaces()) {
				bind(c);
			}
		}

		private void bind(Class<?> c) {
			for (Method m : c.getDeclaredMethods()) {
				RemoteProcedure rpc = m.getAnnotation(RemoteProcedure.class);
				if (rpc != null) {
					if (functions.containsKey(rpc.value())) {
						throw new IllegalArgumentException(String.format("0x%04X is assigned", rpc.value()));
					}
					functions.put(rpc.value(), m);
					logger.debug("bind " + rpc.value() + " to " + m.getName() + "()");
				}
			}
		}

		public Pipe.Close invoke(Pipe.Open open) {
			Method m = functions.get(open.method);
			if (m == null) {
				return new Pipe.Close(open.id, null, new NoSuchMethodException(String.format("0x%04X", open.method)).toString());
			} else {
				try {
					Object result = m.invoke(service, open.params);
					return new Pipe.Close(open.id, result);
				} catch (Exception ex) {
					return new Pipe.Close(open.id, null, ex.toString());
				}
			}
		}
	}

	public static class Builder {
		private final Context context;
		private final String name;
		private ReadableByteChannel in = null;
		private WritableByteChannel out = null;
		private Codec codec = new MsgpackCodec();
		private Object service = new Object();
		private boolean active = true;
		Builder(Context context, String name){
			this.context = context;
			this.name = name;
		}
		public Builder on(SocketChannel channel){
			on(channel, channel);
			return this;
		}
		public Builder on(ReadableByteChannel in, WritableByteChannel out){
			this.in = in;
			this.out = out;
			return this;
		}
		public Builder codec(Codec codec){
			this.codec = codec;
			return this;
		}
		public Builder service(Object service){
			this.service = service;
			return this;
		}
		public Builder passive(){
			this.active = false;
			return this;
		}
		public Session create() throws IOException {
			return context.newSession(name, service, active, in, out, codec);
		}
	}
}
