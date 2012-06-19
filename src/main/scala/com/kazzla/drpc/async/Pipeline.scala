/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.drpc.async

import java.nio.channels._
import scala.Some
import java.io.{IOException, Closeable}
import org.apache.log4j.Logger
import java.nio.ByteBuffer

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Pipeline
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 非同期入出力を行うためのクラスです。
 * パイプラインの入力チャネルが EOF に達している場合でも内部での自動クローズは行われません。
 * @author Takami Torao
 * @param sink データ読み出し時に呼び出す関数。パイプラインの入力チャネルが EOF に達して
 *             いる場合は null パラメータで呼び出される。
 */
abstract class Pipeline(sink:(ByteBuffer)=>Unit) extends Closeable with java.lang.AutoCloseable{
	import Pipeline.logger

	// I/O を非ブロッキングモードに設定
	in.configureBlocking(false)
	out.configureBlocking(false)

	// ========================================================================
	// セレクションキー
	// ========================================================================
	/**
	 * 入力チャネルに対するセレクションキーです。Read/Write 可能通知の On/Off を切り替え
	 * るために使用します。
	 * セレクションキーはサブクラスの自動再接続や Selector の引継ぎのために再設定される事
	 * があります。
	 */
	private[this] var keys:Option[(SelectionKey,SelectionKey)] = None

	// ========================================================================
	// 出力待機ちデータキュー
	// ========================================================================
	/**
	 * このパイプラインに対して出力待機しているバイナリデータのキューです。
	 */
	private[this] val writeQueue = new RawBuffer("pipeline queue")

	// ========================================================================
	// 出力バッファ
	// ========================================================================
	/**
	 * このパイプライン上で出力中のデータのバッファです。出力中のバッファが存在しない場合は
	 * None となります。
	 */
	private[this] var writeBuffer:Option[ByteBuffer] = None

	// ========================================================================
	// 入力元の参照
	// ========================================================================
	/**
	 * 入力元を参照します。
	 */
	def in:SelectableChannel with ReadableByteChannel

	// ========================================================================
	// 出力先の参照
	// ========================================================================
	/**
	 * このパイプラインの出力先を参照します。
	 */
	def out:SelectableChannel with WritableByteChannel

	// ========================================================================
	// データの非同期出力
	// ========================================================================
	/**
	 * 指定されたバイナリデータをこのパイプラインに非同期で出力します。呼び出しは直ちに
	 * 完了しますが、呼び出し完了時点でデータが出力を完了している保証はありません。
	 * 出力が完了していないデータと指定されたデータの合計サイズが出力バッファの最大サイズを
	 * 超える場合、このメソッドはブロックします。
	 * @param buffer バッファ
	 * @param offset バッファ内での送信データの開始位置
	 * @param length 送信データの長さ
	 */
	def write(buffer:Array[Byte], offset:Int, length:Int):Unit = {

		// 送信キューに送信データを連結
		val (old,next) = writeQueue.synchronized{
			val old = writeQueue.length
			writeQueue.enqueue(buffer, offset, length)
			(old, writeQueue.length)
		}
		if(logger.isTraceEnabled){
			logger.trace("enqueued %,d bytes into buffer, totally %,d bytes".format(length, next))
		}

		// 送信待機中のデータが発生したらチャネルの書き込み可能通知を受ける
		if(old == 0 && next > 0){
			synchronized{
				onWaitingToWrite(true)
			}
		}
	}

	// ========================================================================
	// データの非同期出力
	// ========================================================================
	/**
	 * 指定されたバッファに格納さされている全てのデータを非同期で出力します。
	 * @param buffer 送信バッファ
	 */
	def write(buffer:Array[Byte]):Unit = write(buffer, 0, buffer.length)

	// ========================================================================
	// パイプラインのクローズ
	// ========================================================================
	/**
	 * このパイプラインをクローズします。
	 * スーパークラスのメソッドではセレクタへ登録されているキーを解放する
	 */
	override def close():Unit = {
		try{
			register(None)
		} catch {
			case ex:IOException => logger.error("fail to close SelectionKey", ex)
		}
	}

	// ========================================================================
	// データの書き込み
	// ========================================================================
	/**
	 * このパイプラインが保持している出力バッファから非同期データの書き込みを行います。
	 */
	private[async] def write():Unit = {

		// 出力中のバッファを参照
		val buffer = writeBuffer match {
			case Some(b) => b
			case None =>
				// 送信中バッファがなくなり送信キューも空なら、次の送信データが到着するまで
				// OP_WRITE 通知を受けない
				writeQueue.synchronized{
					if(writeQueue.length == 0){
						onWaitingToWrite(false)
						return
					}
					writeBuffer = Some(writeQueue.dequeue())
				}
				writeBuffer.get
		}

		// バイナリデータの出力
		// 送信しきれなかった分は次回出力可能時に続きから出力される
		val len = out.write(buffer)
		if(logger.isTraceEnabled){
			logger.trace("write %,d bytes".format(len))
		}

		// 出力バッファ内のデータを全て出力し終えたらバッファを持たない状態にして次回の呼び
		// 出しで出力キューから取得
		if(buffer.remaining() == 0){
			writeBuffer = None
		}
	}

	// ========================================================================
	// データの読み込み
	// ========================================================================
	/**
	 * 指定されたバッファを使用して非同期データの読み込みを行います。
	 * @param inBuffer 読み込みに使用するバッファ
	 */
	private[async] def read(inBuffer:ByteBuffer):Unit = {

		// データの受信
		val len = in.read(inBuffer)

		// 相手からストリームがクローズされている場合
		if(len < 0){
			logger.debug("pipeline eof detected")
			synchronized {
				keys.foreach{ case (inKey, _) =>
					inKey.interestOps(inKey.interestOps() & ~SelectionKey.OP_READ)
				}
			}
			sink(null)
			return
		}

		if(logger.isTraceEnabled){
			logger.trace("read " + len + " bytes")
		}

		// 受信したデータをリスナに通知しバッファをクリア
		inBuffer.flip()
		sink(inBuffer)
		inBuffer.clear()
	}

	// ========================================================================
	// セレクターへ登録
	// ========================================================================
	/**
	 * このパイプの非同期入出力を指定されたセレクターに登録します。既に別のセレクターに登録
	 * されている場合は以前のセレクターと切り離して新しいセレクターに登録します。値に None
	 * を指定した場合はどのセレクターにも登録されていない状態にします。
	 * このパイプラインが使用する SelectionKey には Pipeline そのものが attach されて
	 * います。
	 * @param selector 登録するセレクター
	 */
	private[async] def register(selector:Option[Selector]):Unit = synchronized{

		// 既存のセレクターと切り離し
		keys.foreach{ case (inKey, outKey) =>
			inKey.cancel()
			outKey.cancel()
		}

		// 指定されたセレクターとバインド
		keys = selector match {
			case Some(sel) =>
				val inOpt = SelectionKey.OP_READ
				val outOpt = (if(writeQueue.length > 0) SelectionKey.OP_WRITE else 0)
				if(in.eq(out)){
					val key = in.register(sel, inOpt | outOpt)
					key.attach(this)
					Some((key, key))
				} else {
					val inKey = in.register(sel, inOpt)
					val outKey = out.register(sel, outOpt)
					inKey.attach(this)
					outKey.attach(this)
					Some((inKey, outKey))
				}
			case None => None
		}
		keys
	}

	// ========================================================================
	// 書き込み可能通知
	// ========================================================================
	/**
	 * 内部出力バッファに書き込み可能なデータが待機状態となった時に呼び出されます。
	 * スーパークラスのメソッドでは SelectionKey の interestOpt フラグを変更します。
	 * @param ready データが待機状態になった場合 true
	 */
	protected def onWaitingToWrite(ready:Boolean):Unit = synchronized{
		// in/out が同じインスタンスのケースを考慮して OP_WRITE 以外のフラグは維持
		keys.foreach{ case(_, outKey) =>
			val opt = outKey.interestOps()
			val disabled = ((opt & SelectionKey.OP_WRITE) == 0)
			if(ready && disabled){
				if(logger.isTraceEnabled){
					logger.trace("enable write callback")
				}
				outKey.interestOps(opt | SelectionKey.OP_WRITE)
				outKey.selector().wakeup()
			} else if(!ready && !disabled){
				if(logger.isTraceEnabled){
					logger.trace("disnable write callback")
				}
				outKey.interestOps(opt & ~SelectionKey.OP_WRITE)
				outKey.selector().wakeup()
			}
		}
	}

}

object Pipeline {
	val logger = Logger.getLogger(classOf[Pipeline])
}
