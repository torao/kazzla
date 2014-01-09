/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io.async;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Future
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class Future<T> {
	private final Object signal = new Object();
	private volatile Result<T> value = null;
	private volatile Throwable ex = null;

	public Future(){
		this.value = null;
	}

	public Future(T value){
		this.value = new Result(value);
	}

	public void setSuccess(T value){
		synchronized(signal){
			this.value = new Result(value);
			signal.notifyAll();
		}
	}

	public void setFailure(Throwable ex){
		synchronized(signal){
			this.value = new Result(ex);
			signal.notifyAll();
		}
	}

	public T get() throws InterruptedException {
		return get(0);
	}

	public T get(long timeout) throws InterruptedException {
		synchronized(signal){
			if(value == null){
				signal.wait(timeout);
				if(value == null){
					return null;
				}
			}
		}
		return value.value;
	}

	public boolean hasResult() {
		return value != null;
	}

	private class Result<T> {
		public final T value;
		public final Throwable ex;
		public Result(T value){ this.value = value; this.ex = null; }
		public Result(Throwable ex){ this.value = null; this.ex = ex; }
	}

}
