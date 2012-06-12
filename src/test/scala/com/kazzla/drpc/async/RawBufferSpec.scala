/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import org.scalatest.FunSpec
import java.util.Arrays
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// RawBufferSpec
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class RawBufferSpec extends FunSpec {

	describe("コンストラクタ"){

		it("デフォルトのバッファ容量 4kB 確認"){
			val buffer = new RawBuffer()
			assert(buffer.capacity == 4 * 1024)
			assert(buffer.length == 0)
			// assert(buffer.offset == 0)		// オフセットが 0 である必要はない
		}

		it("初期バッファ容量指定"){
			(1 to 10).foreach{ size =>
				val buffer = new RawBuffer(size)
				assert(buffer.capacity == size)
				assert(buffer.length == 0)
				// assert(buffer.offset == 0)		// オフセットが 0 である必要はない
			}
		}

		it("不正なバッファ容量指定"){
			try { new RawBuffer(0); fail() } catch { case ex:IllegalArgumentException => None }
			try { new RawBuffer(-1); fail() } catch { case ex:IllegalArgumentException => None }
			try { new RawBuffer(-10); fail() } catch { case ex:IllegalArgumentException => None }
		}

	}

	describe("バッファのクリア"){

		it("空のバッファに対するクリア操作"){
			val buffer = new RawBuffer()
			buffer.clear()
			assert(buffer.length == 0)
		}

		it("有効なデータの入っているバッファに対するクリア操作"){
			val buffer = new RawBuffer()
			buffer.enqueue("ABCDEFG".getBytes())
			assert(buffer.length == 7)
			buffer.clear()
			assert(buffer.length == 0)
		}
	}

	describe("enqueue操作(Array[Byte])"){
		val test:Array[Byte] = (0 to 99).map{ n => (scala.math.random * 256).toByte }.toArray

		it("バッファ全体指定のenqueue"){
			val buffer = new RawBuffer()
			for(i <- 1 to 10){
				buffer.enqueue(test)
				assert(buffer.length == test.length * i)
				for(j <- 0 until i){
					assert(matches(buffer.raw, buffer.offset + test.length * j, test, 0, test.length))
				}
			}
		}

		it("バッファサイズ指定のenqueue"){
			val buffer = new RawBuffer()
			for(offset <- 0 to 10){
				for(size <- 0 to 10){
					buffer.clear()
					buffer.enqueue(test, offset, size)
					assert(buffer.length == size)
					assert(matches(buffer.raw, buffer.offset, test, offset, size))
				}
			}
		}

		it("連続してenqueueを実行した時のデータ整合性"){
			val expected = new ByteArrayOutputStream()
			val buffer = new RawBuffer()
			for(i <- 0 until 10) {
				val test:Array[Byte] = randomBinary((scala.math.random * 100).toInt + 1)
				assert(test.length > 0)
				expected.write(test)
				buffer.enqueue(test)
			}
			assert(buffer.length == expected.size())
			assert(matches(buffer.raw, buffer.offset, expected.toByteArray, 0, expected.size()))
		}

		it("enqueueでキャパシティが10倍まで増えた時のデータ整合性"){
			val expected = new ByteArrayOutputStream()
			val buffer = new RawBuffer()
			val limit = buffer.capacity * 10		// 初期サイズの 10 倍まで拡張させる
			while(buffer.length < limit){
				val test:Array[Byte] = (0 to ((scala.math.random * 100).toInt + 1)).map{ _ => (scala.math.random * 256).toByte }.toArray
				assert(test.length > 0)
				expected.write(test)
				buffer.enqueue(test)
			}
			assert(buffer.length == expected.size())
			assert(buffer.length <= buffer.capacity)
			assert(matches(buffer.raw, buffer.offset, expected.toByteArray, 0, expected.size()))
		}

	}

	describe("enqueue操作(ByteBuffer)"){

		it("バッファ全体指定のenqueue"){
			for(i <- 1 to 10){
				val buffer = new RawBuffer()
				val b = randomBinary(i)
				buffer.enqueue(ByteBuffer.wrap(b))
				assert(buffer.length == b.length)
				assert(matches(buffer.raw, buffer.offset, b, 0, b.length))
			}
		}

		it("連続してenqueueを実行した時のデータ整合性"){
			val expected = new ByteArrayOutputStream()
			val buffer = new RawBuffer()
			for(i <- 0 until 10) {
				val test:Array[Byte] = randomBinary((scala.math.random * 100).toInt + 1)
				assert(test.length > 0)
				expected.write(test)
				buffer.enqueue(test)
			}
			assert(buffer.length == expected.size())
			assert(matches(buffer.raw, buffer.offset, expected.toByteArray, 0, expected.size()))
		}

		it("enqueueでキャパシティが10倍まで増えた時のデータ整合性"){
			val expected = new ByteArrayOutputStream()
			val buffer = new RawBuffer()
			val limit = buffer.capacity * 10		// 初期サイズの 10 倍まで拡張させる
			while(buffer.length < limit){
				val test:Array[Byte] = (0 to ((scala.math.random * 100).toInt + 1)).map{ _ => (scala.math.random * 256).toByte }.toArray
				assert(test.length > 0)
				expected.write(test)
				buffer.enqueue(test)
			}
			assert(buffer.length == expected.size())
			assert(buffer.length <= buffer.capacity)
			assert(matches(buffer.raw, buffer.offset, expected.toByteArray, 0, expected.size()))
		}

	}

	// ランダム値のバイト配列作成
	def randomBinary(size:Int):Array[Byte] = {
		(0 until size).map{ _ => (scala.math.random * 256).toByte }.toArray
	}

	// バイナリの一部一致判定
	def matches(src:Array[Byte], soffset:Int, dst:Array[Byte], doffset:Int, length:Int):Boolean = {
		for(i <- 0 until length){
			if(src(soffset + i) != dst(doffset + i)){
				return false
			}
		}
		true
	}

}
