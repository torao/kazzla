/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io.irpc;
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// FunctionalTest
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author Takami Torao
 */
@RunWith(JUnit4.class)
public class FunctionalTest {
	private static final Logger logger = LoggerFactory.getLogger(FunctionalTest.class);

	@Test
	public void EchoServer() throws IOException {
	}

	@Test
	public void Context() throws Exception {
		Executor threads = new ThreadPoolExecutor(5, 5, 10, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
		final Context context = new Context(8 * 1024, threads);
		final Codec codec = new MsgpackCodec();

		Thread server = new Thread(){
			public void run(){
				try {
					ServerSocketChannel server = ServerSocketChannel.open();
					server.bind(new InetSocketAddress(7777));
					synchronized(this){
						this.notify();
					}
					SocketChannel client = server.accept();
					logger.debug("accept connection");
					server.close();
					context.newSession("EchoServer").on(client).service(new Echo.Service()).passive().create();
				} catch(IOException ex){
					ex.printStackTrace();
				}
			}
		};
		synchronized(server){
			server.start();
			server.wait();
		}

		SocketChannel channel = SocketChannel.open(new InetSocketAddress("localhost", 7777));
		Session session = context.newSession("EchoClient").on(channel).create();
		Echo echo = session.getInterface(Echo.class);
		assertEquals("abc", echo.echo("abc"));
		assertEquals("cba", echo.reverse("abc"));

		Pipe pipe = session.open((short)12);
		InputStream in = pipe.getInputStream();
		OutputStream out = pipe.getOutputStream();
		for(int i=0; i<1024 * 10; i++){
			out.write(i);
			assertEquals(i & 0xFF, in.read());
		}
		pipe.close("exit");
		context.close();
	}

}
