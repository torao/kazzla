/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io.irpc;

import com.kazzla.core.io.async.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Pipe
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class Pipe implements Closeable {
	private static final Logger logger = LoggerFactory.getLogger(Pipe.class);

	/**
	 * パイプ ID を生成する (つまり Open する) 場合、値の最上位ビットは {@code Session} を開始した側が 0、相手
	 * からの要求を受け取った側が 1 を使用する。これは通信の双方で相手側との合意手順を取らずユニークな ID を生成する
	 * ことを目的としている。
	 * これはつまり相手から受信、または相手へ送信するデータのパイプ ID の該当ビットはこのパイプ ID のそれと逆転して
	 * いなければならないことを意味する。
	 */
	public final int id;

	public static final int UNIQUE_MASK = (1 << 31);

	public final Session session;

	private final BlockingQueue<byte[]> in = new LinkedBlockingQueue<>();
	private final Future<Close> close = new Future<>();

	Pipe(int id, Session session){
		this.id = id;
		this.session = session;
	}

	void open(short function, Object... params) throws IOException {
		Open open = new Open(id, function, params);
		ByteBuffer ob = session.codec.encode(open);
		if (ob.remaining() > Codec.MAX_PACKET_SIZE) {
			throw new IOException(String.format("pipe open packet too large: %d / %d", ob.remaining(), Codec.MAX_PACKET_SIZE));
		}
		write(ob);
		logger.trace("<- Open(" + id + "," + function + "," + Arrays.toString(params) + "):" + session.name);
	}

	/**
	 * 指定した result 付きで Close を送信しパイプを閉じます。
	 * @param result Close に付加する結果
	 * @throws java.io.IOException
	 */
	public void close(Object result) throws IOException {
		sendAndClose(new Close<>(id, result, null));
	}

	/**
	 * 指定した例外付きで Close を送信しパイプを閉じます。
	 * @param ex Close に付加する例外
	 * @throws java.io.IOException
	 */
	public void close(Throwable ex) throws IOException {
		sendAndClose(new Close<>(id, null, ex.toString()));
	}

	/**
	 * 指定された Close を送信しパイプを閉じます。
	 * @param close 送信する Close
	 * @throws java.io.IOException
	 */
	void sendAndClose(Close close) throws IOException {
		ByteBuffer ob = session.codec.encode(close);
		if (ob.remaining() > Codec.MAX_PACKET_SIZE) {
			throw new IOException(String.format("pipe open packet too large: %d / %d", ob.remaining(), Codec.MAX_PACKET_SIZE));
		}
		write(ob);
		logger.trace("<- Close(" + close.id + "," + close.result + "," + close.errorMessage + "):" + session.name);
		session.onPipeClose(id);
		setClose(close);
	}

	void setClose(Close close){
		this.close.setSuccess(close);
		close();
	}

	public void close(){
		if(this.close.hasResult())
		logger.trace("sendAndClose()");
		in.clear();
	}

	public void block(byte[] binary, int offset, int length) throws IOException {
		if(close.hasResult()){
			throw new IOException("closed pipe");
		}
		Block block = new Block(id, binary, offset, length);
		ByteBuffer ob = session.codec.encode(block);
		if (ob.remaining() > Codec.MAX_PACKET_SIZE) {
			throw new IOException(String.format("pipe open packet too large: %d / %d", ob.remaining(), Codec.MAX_PACKET_SIZE));
		}
		write(ob);
		logger.trace("<- Block(" + id + "," + binary.length + "):" + session.name);
	}

	public Close waitForClose(long timeout) throws InterruptedException {
		return close.get(timeout);
	}

	void postBlock(Block block){
		in.add(block.binary);
	}

	private void write(ByteBuffer buffer) throws IOException {
		session.endpoint.write(buffer);
	}

	private InputStream _in = null;

	public InputStream getInputStream() {
		if (_in == null) {
			_in = new IS();
		}
		return _in;
	}

	private OutputStream _out = null;

	public OutputStream getOutputStream() {
		if (_out == null) {
			_out = new OS();
		}
		return _out;
	}

	public static class Open {
		public static final byte TYPE = 0;
		public final int id;
		public final short method;
		public final Object[] params;

		public Open(int id, short method, Object... params) {
			this.id = id;
			this.method = method;
			this.params = params;
		}
	}

	public static class Close<T> {
		public static final byte TYPE = 1;
		public final int id;
		public final T result;
		public final String errorMessage;

		public Close(int id, T result, String errorMessage) {
			this.id = id;
			this.result = result;
			this.errorMessage = errorMessage;
		}

		public Close(int id, T result) { this(id, result, null); }
	}

	public static class Block {
		public static final byte TYPE = 2;
		public final int id;
		public final byte[] binary;
		public final int offset;
		public final int length;

		public Block(int id, byte[] binary){
			this(id, binary, 0, binary.length);
		}
		public Block(int id, byte[] binary, int offset, int length) {
			this.id = id;
			this.binary = binary;
			this.offset = offset;
			this.length = length;
		}
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// OS
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * このパイプを使用した出力ストリームクラス。出力内容を Block にカプセル化して非同期セッションへ出力する。
	 */
	private class OS extends OutputStream {
		public void write(int b) throws IOException {
			write(new byte[]{ (byte)b });
		}

		public void write(byte[] b) throws IOException {
			write(b, 0, b.length);
		}

		public void write(byte[] b, int offset, int length) throws IOException {
			block(b, offset, length);
		}
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// IS
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * このパイプを使用した入力ストリームクラス。
	 */
	private class IS extends InputStream {
		private byte[] processing = null;
		private int offset = 0;
		private final byte[] read1 = new byte[1];

		public int read() throws IOException {
			if (read(read1, 0, 1) < 0) {
				return -1;
			}
			return read1[0] & 0xFF;
		}

		public int read(byte[] b) throws IOException {
			return read(b, 0, b.length);
		}

		public int read(byte[] b, int offset, int length) throws IOException {
			if (!prepareBuffer()) {
				return -1;
			}
			int len = Math.min(length, processing.length - offset);
			System.arraycopy(processing, this.offset, b, offset, len);
			offset += len;
			if (offset == processing.length) {
				processing = null;
			}
			return len;
		}

		private boolean prepareBuffer() throws IOException{
			if (processing == null) {
				if (in.isEmpty() && close.hasResult()) {
					return false;
				}
				try {
					processing = in.take();
				} catch(InterruptedException ex){
					throw new IOException(ex);
				}
				offset = 0;
				assert (processing.length > 0);
			}
			return true;
		}
	}

	private static final ThreadLocal<Pipe> currentPipes = new ThreadLocal<>();
	public static Pipe currentPipe(){
		return currentPipes.get();
	}
	static void currentPipe(Pipe pipe){
		if(pipe == null){
			currentPipes.remove();
		} else {
			currentPipes.set(pipe);
		}
	}

}
