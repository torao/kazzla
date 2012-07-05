/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.irpc;

import com.kazzla.debug.package$;

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Control
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public final class Control extends Transferable{
	public static final byte NOOP = 0;
	public static final byte CANCEL = 1;
	public final byte code;
	public final Object[] args;
	public Control(long pipeId, byte code, Object... args) {
		super(pipeId);
		assert verifyArgumentTypes(args);
		this.code = code;
		this.args = args;
		return;
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
		return pipeId + ":{" + String.format("0x%02X", code & 0xFF) + ":" + package$.MODULE$.makeDebugString(args, 25) + "}";
	}

}