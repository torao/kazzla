/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.protocol;

import com.kazzla.core.io.IO;
import com.kazzla.core.io.async.RawBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Protocol
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class Protocol implements Closeable {
	private static final Logger logger = LoggerFactory.getLogger(Protocol.class);

	// ==============================================================================================
	// データタイプに対するファクトリ
	// ==============================================================================================
	/**
	 * データタイプからインスタンスを生成するためのファクトリのマッピングです。
	 */
	private final Map<Short, Constructor<? extends Transferable>> FACTORIES = new HashMap<>();

	// ==============================================================================================
	// エンドポイント
	// ==============================================================================================
	/**
	 * エンドポイントです。
	 */
	private final Endpoint endpoint;

	// ==============================================================================================
	// コンストラクタ
	// ==============================================================================================
	/**
	 * このプロトコル層の下層となるエンドポイントを指定して構築を行います。
	 * @param endpoint エンドポイント
	 */
	protected Protocol(Endpoint endpoint){
		this.endpoint = endpoint;
		register(Noop.TYPE,						Noop.class);
		register(Specification.TYPE,	Specification.class);
		register(Certificate.TYPE,		Certificate.class);
		registerTransferable();

		// データタイプの登録が終わったら読み出し処理を開始
		this.addListener(new CertListener());
		this.endpoint.setConsumer(new ListenerBridge());
		return;
	}

	// ==============================================================================================
	// ファクトリの登録
	// ==============================================================================================
	/**
	 * サブクラスのプロトコルが定義するデータタイプを登録するためにオーバーライドする必要があります。
	 */
	protected void registerTransferable(){
		return;
	}

	// ==============================================================================================
	// ファクトリの登録
	// ==============================================================================================
	/**
	 * 指定されたデータタイプとファクトリを登録します。このメソッドはサブクラスのコンストラクタで呼び出す必要があり
	 * ます。
	 * @param type データタイプ
	 * @param clazz クラス
	 */
	protected void register(short type, Class<? extends Transferable> clazz){
		if(FACTORIES.containsKey(type)){
			throw new IllegalArgumentException(String.format("type 0x%02X already registered", type));
		}
		try {
			FACTORIES.put(type, clazz.getConstructor(DataInput.class));
		} catch(NoSuchMethodException ex){
			throw new IllegalArgumentException(String.format("constructor %s(DataInput) not defined", clazz.getSimpleName()));
		}
		return;
	}

	// ==============================================================================================
	// データの書き込み
	// ==============================================================================================
	/**
	 * 指定されたデータをこのプロトコル層に書き込みます。
	 * @param data 書き込むデータ
	 */
	public void write(Transferable data) throws IOException {
		if(! FACTORIES.containsKey(data.code)){
			throw new IllegalArgumentException(String.format("unsupported data-unit on this protocol: 0x%02X", data.code));
		}
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(baos);
		data.write(out);
		out.flush();
		byte[] binary = baos.toByteArray();

		baos.reset();
		out.writeShort(data.code);
		out.writeShort(binary.length & 0xFFFF);
		out.write(binary);
		out.flush();

		endpoint.asyncWriteAndIgnore(ByteBuffer.wrap(baos.toByteArray()));
		return;
	}

	// ==============================================================================================
	// データリスナの追加
	// ==============================================================================================
	/**
	 * このプロトコル上でデータを取得した時のリスナを追加します。
	 * @param l 追加するリスナ
	 */
	public void addListener(Listener l){
		this.listeners.add(l);
		return;
	}

	// ==============================================================================================
	// プロトコルのクローズ
	// ==============================================================================================
	/**
	 * このプロトコル層をクローズします。
	 */
	public void close(){
		IO.close(this.endpoint);
		return;
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Noop
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 *
	 */
	public static class Noop extends Transferable {
		public static final short TYPE = 0x00;
		public Noop(){ super(TYPE); }
		public Noop(DataInput in) throws IOException { super(TYPE, in); }
		protected void writeTo(DataOutput out) throws IOException { }
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Specification
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * プロトコル仕様を表すクラスです。
	 */
	public static class Specification extends Transferable {
		public static final short TYPE = 0x01;
		public final String name;
		public final short version;
		public Specification(String name, short version){
			super(TYPE);
			this.name = name;
			this.version = version;
		}
		public Specification(DataInput in) throws IOException {
			super(TYPE, in);
			this.name = in.readUTF();
			this.version = in.readShort();
		}
		protected void writeTo(DataOutput out) throws IOException {
			out.writeUTF(name);
			out.writeShort(version);
			return;
		}
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Certificate
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * 証明書を表すクラスです。
	 */
	public static class Certificate extends Transferable {
		public static final short TYPE = 0x02;
		public Certificate(){ super(TYPE); }
		public Certificate(DataInput in) throws IOException { super(TYPE, in); }
		protected void writeTo(DataOutput out) throws IOException {
			return;
		}
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Listener
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 *
	 */
	public interface Listener {
		public void onReceive(Transferable data);
	}

	private final List<Listener> listeners = new ArrayList<>();

	private void restore(short code, byte[] data){
		try {
			Constructor<? extends Transferable> c = FACTORIES.get(code);
			if(c == null){
				logger.error("unsupported data-unit detected: 0x%02X", code & 0xFF);
			} else {
				DataInput in = new DataInputStream(new ByteArrayInputStream(data));
				Transferable t = c.newInstance(in);
				for(Listener l: listeners){
					l.onReceive(t);
				}
			}
		} catch(Throwable ex){
			logger.error(String.format("fail to convert transferable: 0x%02X", code & 0xFF), ex);
			if(ex instanceof ThreadDeath){
				throw (ThreadDeath)ex;
			}
		}
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// ListenerBridge
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 *
	 */
	private class ListenerBridge implements Endpoint.Consumer {
		public void receive(RawBuffer buffer){
			if(buffer.length() >= 2 + 2){
				ByteBuffer m = buffer.toByteBuffer();
				short code = m.getShort();
				int length = m.getShort() & 0xFFFF;
				if(m.remaining() >= length){
					byte[] data = new byte[length];
					m.get(data);
					restore(code, data);
					buffer.consume(2 + 2 + length);
				}
			}
			return;
		}
	}

	private Certificate cert = null;
	public Certificate getCertificate(){
		if(cert == null){
			throw new IllegalStateException("certificate not specified");
		}
		return cert;
	}
	private class CertListener implements Listener {
		public void onReceive(Transferable data){
			if(data instanceof Certificate){
				if(cert != null){
					throw new SecurityException("unable to overwrite certificate");
				}
				cert = (Certificate)data;
			}
		}
	}
}
