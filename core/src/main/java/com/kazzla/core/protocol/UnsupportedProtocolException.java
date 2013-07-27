/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.protocol;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// UnsupportedProtocolException
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * サポートされていないプロトコルが要求された場合に送出される例外クラスです。
 * @author Takami Torao
 */
public class UnsupportedProtocolException extends Exception {
	public UnsupportedProtocolException(){
		return;
	}
	public UnsupportedProtocolException(String msg){
		super(msg);
		return;
	}
	public UnsupportedProtocolException(Throwable ex){
		super(ex);
		return;
	}
	public UnsupportedProtocolException(String msg, Throwable ex){
		super(msg, ex);
		return;
	}
}
