/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.irpc

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong, AtomicBoolean}
import java.io.{OutputStream, InputStream}
import java.nio.ByteBuffer

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// ServiceContext
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class ServiceContext(stream:Endpoint, bulk:Endpoint) {

	// ========================================================================
	// リモート処理中パイプ
	// ========================================================================
	/**
	 * リモート処理中のパイプです。
	 */
	val remoteProcessing = new PipePool()

	// ========================================================================
	// パイプのオープン
	// ========================================================================
	/**
	 * このインスタンスが使用するセレクターです。
	 */
	def open(name:String, args:Array[Any], timeout:Long, callback:Boolean):Pipe = {
		val pipe = remoteProcessing.create()
		try {
			stream.send(new Open(pipe.id, timeout, callback, name, args:_*))
		} catch {
			case ex:Throwable =>
				throw ex
		} finally {
			if(! callback){
				pipe.set(new Close(pipe.id, Close.Code.NONE, "resulting callback disabled"))
			}
		}
		pipe
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// PipeImpl
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	private[ServiceContext] class PipeImpl(id:Long, pool:PipePool) extends Pipe{

		// ======================================================================
		// シグナル
		// ======================================================================
		/**
		 * Close が到着したことを通知するためのシグナルです。
		 */
		private[this] val signal = new Object()

		// ======================================================================
		// 処理結果
		// ======================================================================
		/**
		 * パイプ処理の結果です。
		 */
		private[this] var close:Option[Close] = None

		// ======================================================================
		// 入力ストリーム
		// ======================================================================
		/**
		 */
		def in:InputStream = {

		}

		// ======================================================================
		// 出力ストリーム
		// ======================================================================
		/**
		 */
		def out:OutputStream = {

		}

		// ======================================================================
		// 結果の参照
		// ======================================================================
		/**
		 * 結果を参照します。指定されたタイムアウトまでに結果のリターンがなかった場合は None
		 * を返します。指定された待ち時間までに応答がなかった場合は None を返します。
		 * 待ち時間に 0 を指定した場合、応答があるまで永遠に待機します。この場合 None が返る
		 * ことはありません。
		 * @param timeout 応答待ち時間 (ミリ秒)
		 * @return RPC 実行結果
		 */
		def get(timeout:Long):Option[Close] = {
			signal.synchronized{
				if(close.isEmpty){
					if(timeout > 0){
						signal.wait(timeout)
					} else {
						signal.wait()
					}
				}
				close
			}
		}

		// ======================================================================
		// 処理のキャンセル
		// ======================================================================
		/**
		 * このパイプを使用して行われている処理をキャンセルします。
		 */
		def cancel(reason:String = "operation canceled"):Unit = {

			// 相手へキャンセルを通知
			val close = new Close(id, Close.Code.CANCEL, reason)
			stream.send(close)

			// 内部をクローズ状態に設定
			setCloseState(close)
		}

		// ======================================================================
		// キャンセルの判定
		// ======================================================================
		/**
		 * このパイプが自分または相手側によってキャンセルされているかを判定します。
		 * @return キャンセルされている場合 true
		 */
		def isCanceled:Boolean = {
			close.foreach{ close =>
				if(close.code == Close.Code.CANCEL){
					return true
				}
			}
			false
		}

		// ======================================================================
		// 結果の設定
		// ======================================================================
		/**
		 * このパイプをクローズ状態にします。
		 */
		def setCloseState(code:Close.CodeClose, msg:String, args:Any*):Unit = {
			setCloseState(new Close(id, code, msg, args:_*))
		}

		// ======================================================================
		// 結果の設定
		// ======================================================================
		/**
		 * このパイプをクローズ状態にします。
		 */
		def setCloseState(unit:Close):Unit = {

			// パイプの終了を通知
			signal.synchronized{
				if(close.isDefined){
					throw new IllegalStateException("pipe already closed")
				}
				close = Some(unit)
				signal.notifyAll()
			}

			// プールのエントリから削除
			pool.close(id)
		}

	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// PipeImpl
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	private[ServiceContext] class PipeOutputStream(pipeId:Long, bufSize:Int, endpoint:Endpoint) extends OutputStream {

		// ======================================================================
		// シーケンス値
		// ======================================================================
		/**
		 * ブロック転送のためのシーケンス値です。
		 */
		private[this] val sequence = new AtomicInteger()

		// ======================================================================
		// バッファ
		// ======================================================================
		/**
		 * 内部バッファです。
		 */
		private[this] val buffer = ByteBuffer.allocate(bufSize)

		def write(b:Int):Unit = {
			if(buffer.remaining() == 0){
				flush()
			}
			buffer.put(b.toByte)
		}

		def write(b:Array[Byte]):Unit = write(b, 0, b.length)

		def write(b:Array[Byte], offset:Int, length:Int):Unit = {
			if(buffer.remaining() == 0){
				flush()
			}
			buffer.put(b.toByte)
		}

		def flush():Unit = {
			if(buffer.position() > 0){
				val seq = sequence.getAndIncrement
				val binary = new Array(buffer.position())
				buffer.flip()
				buffer.get(binary)
				endpoint.send(new Block(id, seq, binary))
			}
		}

	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// PipePool
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	class PipePool private[irpc] () {

		// ======================================================================
		// リモート処理中パイプ
		// ======================================================================
		/**
		 * リモート処理中のパイプです。
		 */
		private[this] var processing = Map[Long,PipeImpl]()

		// ======================================================================
		// 次回パイプID
		// ======================================================================
		/**
		 * 次のパイプ ID 要求で返す値です。
		 */
		private[this] var nextPipeId:Long = 0

		// ======================================================================
		// パイプ数の参照
		// ======================================================================
		/**
		 * 現在プールされているパイプ数を参照します。
		 */
		def pooledPipeCount = processing.size

		// ======================================================================
		// パイプの作成
		// ======================================================================
		/**
		 * 新規にパイプを作成しこのプールに追加します。
		 */
		private[ServiceContext] def create():PipeImpl = synchronized{

			// 新しいパイプ ID の取得
			while(processing.contains(nextPipeId)){
				nextPipeId += 1
			}
			val pipeId = nextPipeId
			nextPipeId += 1

			// パイプの作成
			val pipe = new PipeImpl(pipeId)
			processing += (pipeId -> pipe)
			pipe
		}

		// ======================================================================
		// パイプ処理の終了
		// ======================================================================
		/**
		 * 指定されたパイプ ID の処理が終了した時に呼び出されます。
		 */
		private[ServiceContext] def close(pipeId:Long):Unit = synchronized{
			processing = processing - pipeId
		}

	}

}
