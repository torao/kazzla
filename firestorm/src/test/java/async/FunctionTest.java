/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package async;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.Pipe;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.Random;

import static org.junit.Assert.*;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// FunctionTest
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
@RunWith(JUnit4.class)
public class FunctionTest {
	private static final Logger logger = LoggerFactory.getLogger(FunctionTest.class);

	@Test
	public void RawBufferRandomReadWrite(){
		RawBuffer buffer = new RawBuffer(0);
		Random random = new Random();
		ByteArrayOutputStream expected = new ByteArrayOutputStream();
		ByteArrayOutputStream actual = new ByteArrayOutputStream(1024);
		for(int i=0; i<1000; i++){
			if(random.nextBoolean()){
				// 適当なサイズを何回かに分けて書き込み
				int length = random.nextInt(100);
				byte[] bin = randomBinary(length);
				int write = 0;
				while(write < length){
					int wlen = random.nextInt(length - write + 1);
					buffer.append(bin, write, wlen);
					write += wlen;
				}
				expected.write(bin, 0, bin.length);
			} else {
				// 適当なサイズを何回かに分けて読み込み (端数あり)
				while(buffer.length() > 10){
					int rlen = random.nextInt(buffer.length());
					byte[] r = new byte[rlen];
					buffer.toByteBuffer().get(r);
					buffer.consume(rlen);
					actual.write(r, 0, rlen);
				}
			}
		}
		// 残ったデータを読み込み
		byte[] r = new byte[buffer.length()];
		buffer.toByteBuffer().get(r);
		actual.write(r, 0, r.length);

		Assert.assertArrayEquals(expected.toByteArray(), actual.toByteArray());
	}

	@Test
	public void Socket() throws Exception{
		byte[] buffer1 = sequentialBinary(1024 * 1024);
		byte[] buffer2 = sequentialBinary(1024 * 1024);

		TestServer server = new TestServer(buffer1);
		Dispatcher dispatcher = new Dispatcher("aaa", 3);
		Endpoint session = dispatcher.newSession("test", server.in(), server.out());
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		session.setReceiver(new Receiver() {
			@Override
			public void arrivalBufferedIn(RawBuffer buffer) {
				byte[] b = new byte[buffer.length()];
				buffer.toByteBuffer().get(b);
				baos.write(b, 0, b.length);
				buffer.consume(b.length);
			}
		});
		session.write(ByteBuffer.wrap(buffer2));
		server.join();
		dispatcher.close();

		byte[] result1 = baos.toByteArray();
		Assert.assertArrayEquals(buffer1, result1);
		Assert.assertArrayEquals(buffer2, server.receiveData());
	}

	private class TestServer {
		private final Pipe rpipe;
		private final Pipe wpipe;
		private final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		private Throwable ex = null;
		private final Thread[] threads;
		public TestServer(final byte[] sendBuffer) throws IOException {
			rpipe = Pipe.open();
			wpipe = Pipe.open();

			threads = new Thread[]{
				new Thread(){
					@Override
					public void run(){
						try {
							ByteBuffer buffer = ByteBuffer.wrap(sendBuffer);
							Pipe.SinkChannel sink = wpipe.sink();
							sink.write(buffer);
							sink.close();
						} catch(Throwable e){
							ex = e;
						}
					}
				},
				new Thread(){
					@Override
					public void run(){
						try {
							ByteBuffer buffer = ByteBuffer.allocate(1024);
							Pipe.SourceChannel src = rpipe.source();
							while(true){
								buffer.clear();
								int len = src.read(buffer);
								if(len < 0){
									src.close();
									break;
								}
								baos.write(buffer.array(), 0, len);
							}
						} catch(Throwable e){
							ex = e;
						}
					}
				}
			};

			for(Thread t: threads){
				t.start();
			}
		}
		public void join() throws InterruptedException {
			for(Thread t: threads){
				t.join();
			}
		}
		public ReadableByteChannel in(){
			return wpipe.source();
		}
		public WritableByteChannel out(){
			return rpipe.sink();
		}
		public byte[] receiveData(){
			if(ex != null){
				Assert.fail(ex.toString());
			}
			return baos.toByteArray();
		}
	}

	private static byte[] sequentialBinary(int length){
		byte[] b = new byte[length];
		for(int i=0; i<b.length; i++){
			b[i] = (byte)i;
		}
		return b;
	}

	private static byte[] randomBinary(int length){
		Random random = new Random();
		byte[] b = new byte[length];
		random.nextBytes(b);
		return b;
	}

}
