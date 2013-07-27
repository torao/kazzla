/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.UUID;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// IO
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public final class IO {
	private static final Logger logger = LoggerFactory.getLogger(IO.class);

	// ==============================================================================================
	// コンストラクタ
	// ==============================================================================================
	/**
	 * コンストラクタはクラス内に隠蔽されています。
	 */
	private IO() { }

	// ==============================================================================================
	// リソースのクローズ
	// ==============================================================================================
	/**
	 * 指定されたリソースを例外なしでクローズします。
	 * @param cs クローズするリソース
	 */
	public static void close(Closeable... cs){
		for(Closeable c: cs){
			try {
				if(c != null){
					c.close();
				}
			} catch(IOException ex){
				logger.warn("fail to close object", ex);
			}
		}
	}

	// ==============================================================================================
	// UUID の出力
	// ==============================================================================================
	/**
	 * 指定されたストリームへ UUID を出力します。
	 */
	public static void write(DataOutput out, UUID uuid) throws IOException {
		out.writeLong(uuid.getMostSignificantBits());
		out.writeLong(uuid.getLeastSignificantBits());
	}

	// ==============================================================================================
	// UUID の入力
	// ==============================================================================================
	/**
	 * 指定されたストリームから UUID を読み込みます。
	 */
	public static UUID readUUID(DataInput in) throws IOException {
		long most = in.readLong();
		long least = in.readLong();
		return new UUID(most, least);
	}

	public static void writeUShortBinary(DataOutput out, byte[] binary) throws IOException {
		if(binary.length > 0xFFFF){
			throw new IllegalArgumentException(String.format("binary too long: %d", binary.length));
		}
		out.writeShort(binary.length);
		out.write(binary);
	}
	public static byte[] readUShortBinary(DataInput in) throws IOException {
		int length = in.readUnsignedShort();
		byte[] binary = new byte[length];
		in.readFully(binary);
		return binary;
	}
}
