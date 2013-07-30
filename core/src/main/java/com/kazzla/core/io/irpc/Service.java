/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.io.irpc;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Service
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * @author Takami Torao
 */
public abstract class Service {
	public final String name;
	protected Service(String name){
		this.name = name;
	}

	public <T,U> U getRemoteInterface(Context context, T local, Class<U> clazz){
		for(SocketAddress addr: remotes()){
			try {
				SocketChannel s = SocketChannel.open();
				s.connect(addr);
				Session session = context.newSession(name).on(s).service(local).create();
			} catch(IOException ex){

			}
		}
	}

	protected abstract List<SocketAddress> remotes();

}
