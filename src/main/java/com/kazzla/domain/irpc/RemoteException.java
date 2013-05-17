/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.irpc;

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// RemoteException
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class RemoteException extends RuntimeException {
	public RemoteException(){
		super();
		return;
	}
	public RemoteException(String msg){
		super(msg);
		return;
	}
	public RemoteException(Throwable ex){
		super(ex);
	}
	public RemoteException(String msg, Throwable ex){
		super(msg, ex);
	}
}
