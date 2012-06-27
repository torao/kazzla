/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.irpc.async

import org.scalatest.FunSpec
import java.util.Arrays
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import scala.actors.Actor

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// RawBufferSpec
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class RawBufferSpec extends FunSpec {

	describe("コンストラクタ"){

		it("デフォルトのバッファ容量 4kB 確認"){
			val buffer = new RawBuffer("unit test")
			assert(buffer.capacity == 4 * 1024)
			assert(buffer.length == 0)
			// assert(buffer.offset == 0)		// 初期オフセットが 0 である必要はない
		}

		it("初期バッファ容量指定"){
			(1 to 10).foreach{ size =>
				val buffer = new RawBuffer("unit test", size)
				assert(buffer.capacity == size)
				assert(buffer.length == 0)
				// assert(buffer.offset == 0)		// 初期オフセットが 0 である必要はない
			}
		}

		it("不正なバッファ容量指定"){
			try { new RawBuffer("unit test", 0); fail() } catch { case ex:IllegalArgumentException => None }
			try { new RawBuffer("unit test", -1); fail() } catch { case ex:IllegalArgumentException => None }
			try { new RawBuffer("unit test", -10); fail() } catch { case ex:IllegalArgumentException => None }
		}

	}

	describe("バッファのクリア"){

		it("空のバッファに対するクリア操作"){
			val buffer = new RawBuffer("unit test")
			buffer.clear()
			assert(buffer.length == 0)
		}

		it("有効なデータの入っているバッファに対するクリア操作"){
			val buffer = new RawBuffer("unit test")
			buffer.enqueue("ABCDEFG".getBytes())
			assert(buffer.length == 7)
			buffer.clear()
			assert(buffer.length == 0)
		}
	}

	describe("enqueue操作"){

		it("バッファ全体指定の enqueue"){
			for(i <- 1 to 10){
				val b = randomBinary(i)							// 期待値
				val buffer1 = new RawBuffer("unit test")
				val buffer2 = new RawBuffer("unit test")
				buffer1.enqueue(b)									// Array[Byte]版
				buffer2.enqueue(ByteBuffer.wrap(b))	// ByteBuffer版
				assertMatch(buffer1, b)
				assertMatch(buffer2, b)
			}
		}

		it("バッファサイズ指定の enqueue 連結"){
			val expected = new ByteArrayOutputStream()
			val buffer1 = new RawBuffer("unit test")
			val buffer2 = new RawBuffer("unit test")
			val limit = buffer1.capacity * 10			// バッファ容量拡張を伴う処理
			while(expected.size() < limit){
				for(offset <- 0 to 10){
					for(size <- 0 to 10){
						val b1 = randomBinary(offset + size + 10)
						val b2 = ByteBuffer.wrap(b1)
						b2.position(offset)
						b2.limit(offset + size)
						buffer1.enqueue(b1, offset, size)	// Array[Byte]版
						buffer2.enqueue(b2)								// ByteBuffer版
						expected.write(b1, offset, size)	// 期待値
						assert(b1.length > 0)
					}
				}
			}
			val b = expected.toByteArray
			assertMatch(buffer1, b)
			assertMatch(buffer2, b)
		}

		it("バッファ全体に対する dequeue 操作"){
			for(i <- 0 to 1024){							// 長さが 0 のケースも対応
				val b = randomBinary(i)					// 期待値
				val buffer = new RawBuffer("unit test")
				buffer.enqueue(b)
				assertMatch(buffer.dequeue(), b)
				assert(buffer.length == 0)
			}
		}

		it("バッファの一部に対する dequeue 操作"){
			for(i <- 0 to 1024){							// 長さが 0 のケースも対応
				val b = randomBinary(i + 100)		// 期待値
				val buffer = new RawBuffer("unit test")
				buffer.enqueue(b)
				assertMatch(buffer.dequeue(i), b, 0, i)
				assert(buffer.length == 100)
				assertMatch(buffer.dequeue(100), b, i, 100)
				assert(buffer.length == 0)
			}
		}

	}

	describe("ブロッキング操作"){

		it("バッファフル状態でのブロッキング"){
			val data1 = "hello, world".getBytes
			val data2 = new Array[Byte](data1.length)
			val buffer = new RawBuffer("unit test", 1, 1)
			val actor = new Actor {
				def act() {
					// 1 バイト当たり 0.5 秒かけて読み込むスレッド
					for(i <- 0 until data2.length){
						Thread.sleep(500)
						val b = buffer.dequeue(1)
						assert(b.remaining() == 1, b.remaining())
						val b1 = data1(i)
						val b2 = b.get()
						logger.debug("%s <-> %s".format(b1.toChar, b2.toChar))
						data2(i) = b2
					}
					react {
						case e => reply("ok")
					}
				}
			}
			actor.start()
			val start = System.currentTimeMillis()
			buffer.enqueue(data1)
			val actual = System.currentTimeMillis() - start
			val expected = 500 * data1.length
			val error = scala.math.abs(expected - actual) / expected.toDouble
			assert(error < 0.10, error)		// 誤差 10% 以内
			// scala.actors.Actor.exit()

			// enqueue したデータが dequeue できていること
			actor !? "exit"
			assert(Arrays.equals(data1, data2), new String(data2))
		}

	}

		// ランダム値のバイト配列作成
	private def randomBinary(size:Int):Array[Byte] = {
		(0 until size).map{ _ => (scala.math.random * 256).toByte }.toArray
	}

	// バイナリの一部一致判定
	private def assertMatch(b1:Array[Byte], o1:Int, l1:Int, b2:Array[Byte], o2:Int, l2:Int){
		assert(l1 == l2)
		for(i <- 0 until l1){
			assert(b1(o1 + i) == b2(o2 + i))
		}
	}

	// バイナリの一部一致判定
	private def assertMatch(b1:Array[Byte], b2:Array[Byte]){
		assertMatch(b1, 0, b1.length, b2, 0, b2.length)
	}

	// バイナリの一部一致判定
	private def assertMatch(b1:RawBuffer, b2:Array[Byte], o2:Int, l2:Int){
		assertMatch(b1.raw, b1.offset, b1.length, b2, o2, l2)
	}

	// バイナリの一部一致判定
	private def assertMatch(b1:RawBuffer, b2:Array[Byte]){
		assertMatch(b1.raw, b1.offset, b1.length, b2, 0, b2.length)
	}

	// バイナリの一部一致判定
	private def assertMatch(b1:ByteBuffer, b2:Array[Byte], o2:Int, l2:Int){
		val b = new Array[Byte](b1.remaining())
		b1.get(b)
		assertMatch(b, 0, b.length, b2, o2, l2)
	}

	// バイナリの一部一致判定
	private def assertMatch(b1:ByteBuffer, b2:Array[Byte]){
		assertMatch(b1, b2, 0, b2.length)
	}

	// バイナリの一部一致判定
	def matches(src:Array[Byte], soffset:Int, dst:Array[Byte], doffset:Int, length:Int):Boolean = {
		for(i <- 0 until length){
			if(src(soffset + i) != dst(doffset + i)){
				import com.kazzla.debug.makeDebugString
				val b1 = new Array[Byte](length)
				System.arraycopy(src, soffset, b1, 0, length)
				val b2 = new Array[Byte](length)
				System.arraycopy(dst, doffset, b2, 0, length)
				System.err.println("%s != %s @ %d %s != %s".format(makeDebugString(b1), makeDebugString(b2), i, src(soffset + i), dst(doffset + i)))
				return false
			}
		}
		true
	}


	// バイナリの一部一致判定
	def matches(buffer:RawBuffer, dst:Array[Byte], doffset:Int, length:Int):Boolean = {
		buffer.length == length && matches(buffer.raw, buffer.offset, dst, doffset, length)
	}

	// バイナリの一部一致判定
	def matches(buffer:RawBuffer, dst:Array[Byte]):Boolean = {
		buffer.length == dst.length && matches(buffer, dst, 0, dst.length)
	}

	// バイナリの一部一致判定
	def matches(buffer:ByteBuffer, dst:Array[Byte]):Boolean = {
		val b = buffer.array()
		matches(b, 0, dst, 0, dst.length)
	}

	// バイナリの一部一致判定
	def matches(buffer:ByteBuffer, dst:Array[Byte], offset:Int, length:Int):Boolean = {
		val b = buffer.array()
		matches(b, 0, dst, offset, length)
	}

}
