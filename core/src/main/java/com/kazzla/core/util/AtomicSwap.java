/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.util;

import java.util.concurrent.atomic.AtomicLong;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// AtomicSwap
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class AtomicSwap {
	private final AtomicLong value;
	public AtomicSwap(long defaultValue){
		this.value = new AtomicLong(defaultValue);
	}
	public boolean compareAndSwapIfNotEquals(long expected, long update){
		long current = this.value.get();
		if(current != expected){
			while(value.compareAndSet(current, update));
			return true;
		}
		return false;
	}
}
