/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import javax.net.SocketFactory
import java.nio.channels.SocketChannel
import java.io.IOException
import java.net.InetSocketAddress
import annotation.tailrec

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// ActiveChannel
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class ActiveChannel(address:InetSocketAddress, factory:SocketFactory) {

	// ========================================================================
	// 接続中ソケット
	// ========================================================================
	/**
	 * 接続中のソケットです。
	 */
	private[this] var _channel:Option[SocketChannel] = None

	// ========================================================================
	// 再接続回数
	// ========================================================================
	/**
	 * 再接続のための回数です。
	 */
	private[this] val maxRetryCount = 10

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	def channel:SocketChannel = synchronized {
		if(_channel.isEmpty){
			_channel = Some(newChannel(0))
		}
		_channel.get
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@tailrec
	private[this] def newChannel(count:Int):SocketChannel = {
		try {
			return factory.createSocket(address.getAddress, address.getPort)
		} catch {
			case e:IOException =>
				if(count >= maxRetryCount){
					throw ex
				}
				logger.info("connection failure: %s:%s (%s), retrying %d/%d...".format(
					address.getHostName, address.getPort, ex, count + 1, maxRetryCount))
		}
		Thread.sleep((1 << count) * 1000L)
		newChannel(count + 1)
	}

}
