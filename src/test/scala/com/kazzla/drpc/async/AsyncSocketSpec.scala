/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import org.scalatest.FunSpec
import java.nio.ByteBuffer
import java.io.ByteArrayOutputStream
import java.nio.channels.SocketChannel
import java.net.InetSocketAddress

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// AsyncSocketSpec
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class AsyncSocketSpec extends FunSpec {

	describe("非早期ソケットコンテキスト"){

		it("コンストラクタ"){
			val context = new AsyncSocketContext()
			assert(context.activeAsyncSockets == 0)
			// assert(context.activeThreads == 0)		// 初期状態の起動スレッド数は考慮しない
			context.close()
		}
	}

	describe("非同期ソケット"){

		it("Google と HTTP/1.0 通信可能"){
			val context = new AsyncSocketContext()
			val signal = new Object()
			val channel = SocketChannel.open(new InetSocketAddress("www.google.com", 80))
			val socket = new AsyncSocket(channel)
			context.join(socket)
			socket.addAsyncSocketListener(new AsyncSocketListener {
				val buffer = new ByteArrayOutputStream()
				def asyncDataReceived(b: ByteBuffer) { buffer.write(b.array(), b.position(), b.limit() - b.position()) }
				def asyncSocketClosed(socket: AsyncSocket) = signal.synchronized{
					val content = new String(buffer.toByteArray)
					val status = content.split("\\r?\\n")(0)
					logger.info(status)
					assert(status.matches("HTTP/1\\..+\\s+\\d{3}\\s+.*"))
					signal.notify()
				}
			})
			signal.synchronized {
				socket.send("GET / HTTP/1.0\r\nConnection:close\r\nHost:www.google.com\r\n\r\n".getBytes)
				signal.wait()
			}
			context.close()
		}

		it("TCP/IP で Google と HTTP/1.0 通信可能マルチ"){
			val context = new AsyncSocketContext()
			val signal = new Object()
			val channel = SocketChannel.open(new InetSocketAddress("www.google.com", 80))
			val socket = new AsyncSocket(channel)
			context.join(socket)
			socket.addAsyncSocketListener(new AsyncSocketListener {
				val buffer = new ByteArrayOutputStream()
				def asyncDataReceived(b: ByteBuffer) { buffer.write(b.array(), b.position(), b.limit() - b.position()) }
				def asyncSocketClosed(socket: AsyncSocket) = signal.synchronized{
					System.out.println(new String(buffer.toByteArray))
					signal.notify()
				}
			})
			signal.synchronized {
				socket.send("GET / HTTP/1.0\r\nConnection:close\r\nHost:www.google.com\r\n\r\n".getBytes)
				signal.wait()
			}
			context.close()
		}

	}

}
