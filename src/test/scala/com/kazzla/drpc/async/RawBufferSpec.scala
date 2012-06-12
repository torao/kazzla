/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import org.scalatest.FunSpec

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// RawBufferSpec
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class RawBufferSpec extends FunSpec {
	describe("低レベルバッファ"){
		val test = (0 to 99).map{ n => ((num % 10) + '0').toByte }.toArray

		it("バイナリデータを追加するとサイズが増える"){
			val buffer = new RawBuffer(1)
			assert(buffer.length == 0)
			buffer.enqueue(test, 0, test.length)
			assert(buffer.length == test.length)
			buffer.enqueue(test, 0, test.length)
			assert(buffer.length == test.length * 2)
		}

		it("初期バッファサイズに0を指定すると例外が発生"){
			try {
				new RawBuffer(0)
				fail()
			} catch {
				case ex:IllegalArgumentException => None
			}
		}

		it("初期バッファサイズに負の値を指定すると例外が発生"){
			try {
				new RawBuffer(-1)
				fail()
			} catch {
				case ex:IllegalArgumentException => None
			}
		}
	}
}
