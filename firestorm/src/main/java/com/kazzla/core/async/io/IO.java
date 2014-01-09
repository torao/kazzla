/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.UUID;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// IO
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public final class IO {
	private static final Logger logger = LoggerFactory.getLogger(IO.class);

	public static final Charset UTF8 = Charset.forName("UTF-8");

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
				logger.warn("fail to destroy object", ex);
			}
		}
	}

	// ==============================================================================================
	// メッセージダイジェストの参照
	// ==============================================================================================
	/**
	 * 指定されたアルゴリズムを使用して入力ストリームから取得できるデータのメッセージダイジェストを参照します。
	 */
	public static byte[] getMessageDigest(InputStream is, String algorithm) throws IOException {
		try {
			MessageDigest md = MessageDigest.getInstance(algorithm);
			DigestInputStream in = new DigestInputStream(is, md);
			byte[] buffer = new byte[1024];
			while(in.read(buffer) > 0);
			return md.digest();
		} catch(Exception ex){
			throw new IOException(ex);
		}
	}

}
