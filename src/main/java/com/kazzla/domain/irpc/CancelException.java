/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.irpc;

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// CancelException
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class CancelException extends RemoteException {
	public CancelException(){
		super();
		return;
	}
	public CancelException(String msg){
		super(msg);
		return;
	}
	public CancelException(Throwable ex){
		super(ex);
	}
	public CancelException(String msg, Throwable ex){
		super(msg, ex);
	}
}
