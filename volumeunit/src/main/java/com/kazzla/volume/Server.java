/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.volume;

import com.kazzla.IO;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Server
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class Server {
	private final Logger logger = Logger.getLogger(Server.class.getName());

	// =========================================================================
	// サーバポート
	// =========================================================================
	/**
	 * このサーバのポート番号です。
	 */
	private final int port;

	// =========================================================================
	// 処理スレッド
	// =========================================================================
	/**
	 * サーバ処理を行うスレッドです。
	 */
	private Thread thread = null;

	// =========================================================================
	//
	// =========================================================================
	/**
	 *
	 */
	public Server(int port){
		this.port = port;
		return;
	}

	// =========================================================================
	// サーバ処理の開始
	// =========================================================================
	/**
	 * このサーバの処理を開始します。
	 * @throws IOException 処理の開始に失敗した場合
	 */
	public void start() throws IOException {
		thread = new Thread(new Runnable(){
			public void run(){
				try {
					execute();
				} catch(Throwable ex){
					if(ex instanceof ThreadDeath){
						throw (ThreadDeath)ex;
					}
					ex.printStackTrace();
				}
			}
		});
		thread.setName("Server");
		thread.start();
		return;
	}

	// =========================================================================
	// サーバ処理の停止
	// =========================================================================
	/**
	 * このサーバの処理を終了します。
	 */
	public void stop() {
		Thread t = thread;
		if(t != null){
			t.interrupt();
		}
		return;
	}

	// =========================================================================
	// サーバ処理の開始
	// =========================================================================
	/**
	 * このサーバの処理を開始します。
	 * @throws IOException 処理の開始に失敗した場合
	 */
	private void execute(){
		logger.info("starting volume service on port " + port);
		try {
			ServerSocket server = new ServerSocket(port);
			server.setSoTimeout(2 * 1000);
			while(! thread.isInterrupted()){
				Socket client = null;
				try {
					client = server.accept();
				} catch(InterruptedIOException ex){
					continue;
				}
				fork(client);
			}
		} catch(IOException ex){
			logger.log(Level.SEVERE, "abort to accept from clients", ex);
			thread = null;
		} finally {
			logger.info("stopping volume service");
		}
		return;
	}

	// =========================================================================
	// サーバ処理の開始
	// =========================================================================
	/**
	 * このサーバの処理を開始します。
	 * @throws IOException 処理の開始に失敗した場合
	 */
	private void fork(final Socket client) {
		InputStream in = null;
		OutputStream out = null;
		try {
			final InputStream i = in = client.getInputStream();
			final OutputStream o = out = client.getOutputStream();
			Thread t = new Thread(new Runnable(){
				public void run(){
					logger.info("open connection");
					dispatch(i, o);
					IO.close(i, o, client);
					logger.info("close connection");
				}
			});
			t.setName("VolumeClient");
			t.start();
		} catch(IOException ex){
			logger.log(Level.INFO, "", ex);
			ex.printStackTrace();
			IO.close(in, out);
		}
	}

	// =========================================================================
	// サーバ処理の開始
	// =========================================================================
	/**
	 * このサーバの処理を開始します。
	 * @throws IOException 処理の開始に失敗した場合
	 */
	private void dispatch(InputStream in, OutputStream out) {
	}


	// =========================================================================
	// =========================================================================
	public void main(String[] args){

	}

}
