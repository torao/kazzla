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
// PipelineSpec
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class PipelineSpec extends FunSpec {

	describe("パイプライングループ"){

		it("コンストラクタ"){
			val group = new PipelineGroup()
			assert(group.activePipelines == 0)
			// assert(context.activeThreads == 0)		// 初期状態の起動スレッド数は考慮しない
			group.close()
		}
	}

	describe("非同期ソケット通信"){

		it("Google と HTTP/1.0 通信可能"){
			val client = new Client()
			val group = new PipelineGroup()
			val channel = SocketChannel.open(new InetSocketAddress("www.google.com", 80))
			val socket = new SocketPipeline(channel, client.capture)
			group.begin(socket)
			socket.write("GET / HTTP/1.0\r\nConnection:close\r\nHost:www.google.com\r\n\r\n".getBytes)
			val content = client()
			val status = content.split("\\r?\\n")(0)
			logger.info(status)
			assert(status.matches("HTTP/1\\..+\\s+\\d{3}\\s+.*"), status)
			group.close()
		}

	}

	class Client {
		val buffer = new ByteArrayOutputStream()
		def capture(b:ByteBuffer){
			if(b != null){
				buffer.write(b.array(), b.position(), b.limit() - b.position())
			} else {
				buffer.synchronized{ buffer.notify() }
			}
		}
		def apply():String = {
			buffer.synchronized {
				buffer.wait()
				new String(buffer.toByteArray)
			}
		}
	}

}
