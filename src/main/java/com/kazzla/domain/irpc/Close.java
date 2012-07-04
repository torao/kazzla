/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.irpc;

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Close
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public final class Close extends Transferable{

	public enum Code {
		NONE((byte)0),
		EXIT((byte)1),
		WARN((byte)50),
		CANCEL((byte)51),
		ERROR((byte)100),
		FATAL((byte)127)
		;
		public final byte id;
		Code(byte id){
			this.id = id;
		}
		public static Code getCode(byte id) throws RemoteException{
			for(Code code: values()){
				if(code.id == id){
					return code;
				}
			}
			throw new RemoteException(String.format("unknown close code: 0x%02X", id & 0xFF));
		}
	}

	public final Code code;
	public final String message;
	public final Object[] args;

	public Close(long pipeId, Code code, String message, Object... args) {
		super(pipeId);
		assert verifyArgumentTypes(args);
		this.code = code;
		this.message = message;
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
		return "Close[" + pipeId + "](" + code + ","
			+ com.kazzla.debug.package$.MODULE$.makeDebugString(args, 25)
			+ "," + message + ")";
	}

}