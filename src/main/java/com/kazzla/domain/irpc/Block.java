/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.irpc;

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Block
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

import com.kazzla.debug.*;
import com.kazzla.debug.package$;

/**
 * @author Takami Torao
 */
public final class Block extends Transferable{
	public final int sequence;
	public final byte[] binary;
	public Block(long pipeId, int seq, byte[] bin) {
		super(pipeId);
		this.sequence = seq;
		this.binary = bin;
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
		return pipeId + ":[" + sequence + ":" + package$.MODULE$.makeDebugString(binary, 25) + "]";
	}

}