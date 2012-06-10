/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import org.scalatest.FunSpec
import java.nio.channels.SocketChannel
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.io.IOException

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// EndpointSpec
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class EndpointSpec extends FunSpec {
	describe("Endpoint"){

		it("constructor accept opened channel"){
			val google = new InetSocketAddress("www.google.com", 80)
			val channel = SocketChannel.open(google)
			new TestEndpoint(channel).close()
		}

		it("constructor reject closed channel"){
			val google = new InetSocketAddress("www.google.com", 80)
			val channel = SocketChannel.open(google)
			channel.close()
			try {
				new TestEndpoint(channel)
				fail("constructor accept closed channel")
			} catch {
				case ex:IOException => None
			}
		}

		it("close operation closes lower-channel"){
			val google = new InetSocketAddress("www.google.com", 80)
			val channel = SocketChannel.open(google)
			new TestEndpoint(channel).close()
			assert(! channel.isOpen)
		}

		it("toStirng"){
			val google = new InetSocketAddress("www.google.com", 80)
			val channel = SocketChannel.open(google)
			val endpoint = new TestEndpoint(channel)
			println(endpoint.toString())
			endpoint.close()
		}

		class TestEndpoint(channel:SocketChannel) extends Endpoint(channel){
			def receive(buffer:ByteBuffer) = { }
			def send():ByteBuffer = null
		}
	}

	class HttpEndpoint(channel:SocketChannel, hostname:String) extends Endpoint(channel) {
		val binary = "GET / HTTP/1.0\r\nHost: %s\r\nConnection: close\r\n\r\n".format(hostname).getBytes
		val sendData = (0 until 10).map{ i =>
			val len = binary.length / 10
			val from = len * i
			val until = scala.math.min(len * (i+1), binary.length)
			ByteBuffer.wrap(binary.slice(from, until))
		}.toList
		var sendIndex = 0
		sendDataReady(true)
		def receive(buffer:ByteBuffer):Boolean = {
			true
		}
		def send():ByteBuffer = {
			val b = sendData(sendIndex)
			sendIndex += 1
			if(sendIndex == sendData.length){
				sendDataReady(false)
			}
			b
		}
	}
}
