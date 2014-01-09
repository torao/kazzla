/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io.irpc;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// CodecException
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class CodecException extends RuntimeException {
	public CodecException(){ }
	public CodecException(String msg){ super(msg); }
	public CodecException(Throwable ex){ super(ex); }
	public CodecException(String msg, Throwable ex){ super(msg, ex); }
}
