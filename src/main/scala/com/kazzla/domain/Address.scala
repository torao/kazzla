/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain

import java.nio.channels._
import java.net.Socket

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Session
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * <p>
 * 異なるセッション間で接続が共有されることはありません。
 * </p>
 * @author Takami Torao
 */
trait ServerAddress {

	// ========================================================================
	//
	// ========================================================================
	/**
	 */
	def create():ServerSocketChannel

}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Session
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * <p>
 * 異なるセッション間で接続が共有されることはありません。
 * </p>
 * @author Takami Torao
 */
trait ReconnectableAddress {

	// ========================================================================
	//
	// ========================================================================
	/**
	 * このセッション上で確保されている不必要なリソースを開放します。
	 */
	def connect():(SelectableChannel with ReadableByteChannel, SelectableChannel with WritableByteChannel)

}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Session
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * <p>
 * 異なるセッション間で接続が共有されることはありません。
 * </p>
 * @author Takami Torao
 */
class SocketAddress(address:java.net.SocketAddress) extends ReconnectableAddress{

	// ========================================================================
	//
	// ========================================================================
	/**
	 * このセッション上で確保されている不必要なリソースを開放します。
	 */
	def connect():(SelectableChannel with ReadableByteChannel, SelectableChannel with WritableByteChannel) = {
		val socket = new Socket
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 * このセッション上で確保されている不必要なリソースを開放します。
	 */
	protected def createSocket(address:java.net.SocketAddress):Socket = {
		val socket = new Socket()
		socket.connect()
	}

}
