/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla;

import java.io.Closeable;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// IO
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public final class IO {
	private static final Logger logger = Logger.getLogger(IO.class.getName());

	// =========================================================================
	// コンストラクタ
	// =========================================================================
	/**
	 * コンストラクタはクラス内に隠蔽されています。
	 */
	private IO() {
		return;
	}

	// =========================================================================
	// リソースのクローズ
	// =========================================================================
	/**
	 * 指定されたリソースを例外なしでクローズします。
	 * @param cs クローズする利祖イース
	 */
	public static final void close(Closeable... cs){
		for(Closeable c: cs){
			try {
				if(c != null){
					c.close();
				}
			} catch(IOException ex){
				logger.log(Level.WARNING, "fail to close object", ex);
			}
		}
	}

}
