/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.irpc;

import sun.rmi.transport.ObjectTable;

import java.util.Map;

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Transferable
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public abstract class Transferable implements java.io.Serializable{
	public final long pipeId;
	Transferable(long pipeId){
		this.pipeId = pipeId;
	}

	// ========================================================================
	// パラメータの確認
	// ========================================================================
	/**
	 * 指定されたパラメータが iRPC 仕様に沿ったものかを確認します。
	 * このメソッドは未定義の型のオブジェクトを検出した場合に例外を発生させますが、プロダク
	 * ション環境では実行されないないように assert と組み合わせて使用することを想定してる
	 * ため boolean を返します。
	 */
	@SuppressWarnings("unchecked")
	public static boolean verifyArgumentTypes(Object... args){
		for(Object arg: args){
			if(arg == null
				|| arg instanceof Boolean || arg instanceof Byte || arg instanceof Short
				|| arg instanceof Integer || arg instanceof Long || arg instanceof Float
				|| arg instanceof Double || arg instanceof String){
				// do nothing
			} else if(arg instanceof Map){
				verifyArgumentTypes(((Map<Object,Object>)arg).keySet());
				verifyArgumentTypes(((Map<Object,Object>)arg).values());
			} else if(arg instanceof Iterable){
				for(Object elem: (Iterable<Object>)arg){
					verifyArgumentTypes(elem);
				}
			} else if(arg instanceof Object[]){
				for(Object elem: (Object[])arg){
					verifyArgumentTypes(elem);
				}
			} else {
				throw new IllegalArgumentException("unsupported argument type: " + arg.getClass().getName());
			}
		}
		return true;
	}

}