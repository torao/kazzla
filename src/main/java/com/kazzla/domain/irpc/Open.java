/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.irpc;

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Open
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

import com.kazzla.debug.*;

/**
 * @author Takami Torao
 */
public final class Open extends Transferable{
	public final long timeout;
	public final boolean callback;
	public final String name;
	public final Object[] args;
	public Open(long pipeId, long timeout, boolean callback, String name, Object... args){
		super(pipeId);
		assert verifyArgumentTypes(args);
		this.timeout = timeout;
		this.callback = callback;
		this.name = name;
		this.args = args;
	}

	// ========================================================================
	// インスタンスの文字列化
	// ========================================================================
	/**
	 * このインスタンスを文字列化します。
	 * @return インスタンスの文字列
	 */
	@Override
	public String toString(){
		return "Open[" + pipeId + "](" + timeout + "," + callback + "," + name + '(' + com.kazzla.debug.package$.MODULE$.makeDebugString(args, 25) + ')';
	}

}