/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.async

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
 * <p>
 * sink パラメータはこのパイプラインが非同期に読みだしたデータを渡すコールバック関数です。
 * 関数の呼び出しはディスパッチャースレッド内で行われるため関数は直ちに終了する必要があります。
 * 関数は入力データバッファや処理を起動するためのワーカースレッドプールを実装する必要があり
 * ます。パイプラインの入力が EOF に達した場合、Sink 関数 null パラメータ付きで呼び出され
 * ます。
 * </p>
 * @author Takami Torao
 * @param sink function to callback when asynchronouse data is read on this
 *             pipeline. Null will pass in case input reaches EOF.
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
	private[this] var writingBuffer:Option[ByteBuffer] = None

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
	 * 指定されたバッファに格納さされている全てのデータを非同期で出力します。
	 * @param buffer 送信バッファ
	 * @return バッファリングされているデータの全サイズ
	 */
	def write(buffer:Array[Byte]):Int = write(buffer, 0, buffer.length)

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
	 * @return バッファリングされているデータの全サイズ
	 */
	def write(buffer:Array[Byte], offset:Int, length:Int):Int = {
		// ※バッファフラッシュの目的で 0 バイトのデータが渡ってくることに注意

		// 送信キューに送信データを連結
		val (needNotify, bufferingSize) = writeQueue.synchronized{
			val old = writeQueue.length
			writeQueue.enqueue(buffer, offset, length)
			((old == 0 && writeQueue.length > 0), writeQueue.length + writingBuffer.size)
		}
		if(logger.isTraceEnabled){
			logger.trace("enqueued %,d bytes into buffer, totally %,d bytes".format(length, writeQueue.length))
		}

		// 送信待機中のデータが発生したらチャネルの書き込み可能通知を受ける
		if(needNotify){
			synchronized{
				onWaitingToWrite(true)
			}
		}
		bufferingSize
	}

	// ========================================================================
	// データの非同期出力
	// ========================================================================
	/**
	 * 指定されたバイナリデータをこのパイプラインに非同期出力します。返値の `Future` を
	 * 使用してデータがパイプラインに送信されるまで待機することができます。
	 * @param buffer バッファ
	 * @return 指定データの出力完了確認用
	 */
	def writeWithFuture(buffer:Array[Byte]):Pipeline.Future = writeWithFuture(buffer, 0, buffer.length)

	// ========================================================================
	// データの非同期出力
	// ========================================================================
	/**
	 * 指定されたバイナリデータをこのパイプラインに非同期出力します。返値の `Future` を
	 * 使用してデータがパイプラインに送信されるまで待機することができます。
	 * @param buffer バッファ
	 * @param offset バッファ内での送信データの開始位置
	 * @param length 送信データの長さ
	 * @return 指定データの出力完了確認用
	 */
	def writeWithFuture(buffer:Array[Byte], offset:Int, length:Int):Pipeline.Future = {
		futuresMutex.synchronized{
			val len = write(buffer, offset, length)
			val future = new Pipeline.Future(len)
			futures ::= future
			future
		}
	}

	// ========================================================================
	// パイプラインのクローズ
	// ========================================================================
	/**
	 * このパイプラインをクローズします。
	 * スーパークラスのメソッドではセレクタへ登録されているキーを解放するのみでチャネルの
	 * クローズは行われません。
	 */
	override def close():Unit = {
		try{
			register(None)
		} catch {
			case ex:IOException => logger.error("fail to close SelectionKey", ex)
		}
	}

	// ========================================================================
	// クローズ判定
	// ========================================================================
	/**
	 * このパイプラインがクローズしているかどうかを判定します。
	 */
	def isClosed():Boolean = ! in.isOpen && out.isOpen

	// ========================================================================
	// データの書き込み
	// ========================================================================
	/**
	 * このパイプラインが保持している出力バッファから非同期データの書き込みを行います。
	 */
	private[async] def write():Unit = {

		// 出力中のバッファを参照
		val buffer = writingBuffer match {
			case Some(b) => b
			case None =>
				// 送信中バッファがなくなり送信キューも空なら、次の送信データが到着するまで
				// OP_WRITE 通知を受けない
				writeQueue.synchronized{
					if(writeQueue.length == 0){
						onWaitingToWrite(false)
						return
					}
					writingBuffer = Some(writeQueue.dequeue())
				}
				writingBuffer.get
		}

		// バイナリデータの出力
		// 送信しきれなかった分は次回出力可能時に続きから出力される
		val len = out.write(buffer)
		if(logger.isTraceEnabled){
			logger.trace("write %,d bytes".format(len))
		}

		// 出力待機している Future に通知
		futuresMutex.synchronized {
			futures = futures.filter{ _.consume(len) }
		}

		// 出力バッファ内のデータを全て出力し終えたらバッファを持たない状態にして次回の呼び
		// 出しで出力キューから取得
		if(buffer.remaining() == 0){
			writingBuffer = None
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

	private[this] val futuresMutex = new Object()
	private[this] var futures = List[Pipeline.Future]()

}

object Pipeline {
	private[async] val logger = Logger.getLogger(classOf[Pipeline])

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Future
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * アプリケーションがパイプラインへ出力したデータが出力チャネルに書き込まれた通知を受け
	 * るための `Future` です。
	 */
	class Future private[Pipeline](private[this] var size:Int) {

		// ======================================================================
		// 完了シグナル
		// ======================================================================
		/**
		 * 出力が完了したことを通知するためのシグナルです。
		 */
		private[this] val signal = new Object()

		// ======================================================================
		// バッファの消費
		// ======================================================================
		/**
		 * 指定されたサイズのバッファデータが消費された時に呼び出されます。
		 * @return この Future が終了した場合 false
		 */
		private[Pipeline] def consume(sz:Int):Boolean = {
			size = scala.math.min(0, size - sz)
			if(size == 0){
				signal.synchronized { signal.notify() }
				false
			} else {
				true
			}
		}

		// ======================================================================
		// 出力待機
		// ======================================================================
		/**
		 * この Future が生成された時のデータが全て出力されるまで処理をブロックします。
		 * @param timeout ブロックの最大時間 (ミリ秒)
		 * @return 指定時間内に出力が完了しなかった場合 false
		 */
		def apply(timeout:Long = 0):Boolean = {
			signal.synchronized{
				if(size > 0){
					signal.wait(timeout)
					size == 0
				} else {
					true
				}
			}
		}
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// DefaultPipeline
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * デフォルトのパイプライン。
	 */
	private[this] class DefaultPipeline(
		_in:SelectableChannel with ReadableByteChannel,
		_out:SelectableChannel with WritableByteChannel,
		sink:(ByteBuffer)=>Unit) extends Pipeline(sink){

		// ======================================================================
		// 入力元の参照
		// ======================================================================
		/**
		 * 入力元を参照します。
		 */
		def in:SelectableChannel with ReadableByteChannel = _in

		// ======================================================================
		// 出力先の参照
		// ======================================================================
		/**
		 * このパイプラインの出力先を参照します。
		 */
		def out:SelectableChannel with WritableByteChannel = out

		// ======================================================================
		// パイプラインのクローズ
		// ======================================================================
		/**
		 * このパイプラインをクローズします。
		 * スーパークラスのメソッドではセレクタへ登録されているキーを解放する
		 */
		override def close():Unit = {
			super.close()
			try{
				_in.close()
			} catch {
				case ex:IOException => logger.error("fail to close input", ex)
			}
			try{
				_out.close()
			} catch {
				case ex:IOException => logger.error("fail to close input", ex)
			}
		}

	}

	// ========================================================================
	// パイプラインの構築
	// ========================================================================
	/**
	 * 指定された非同期入出力チャネルを使用するパイプラインを構築するためのユーティリティ
	 * メソッドです。
	 * @param in 非同期入力チャネル
	 * @param out 非同期出力チャネル
	 * @param sink 入力データの通知先関数
	 * @return パイプライン
	 */
	def newPipeline(in:SelectableChannel with ReadableByteChannel, out:SelectableChannel with WritableByteChannel)(sink:(ByteBuffer)=>Unit):Pipeline = {
		new DefaultPipeline(in, out, sink)
	}

	// ========================================================================
	// パイプラインの構築
	// ========================================================================
	/**
	 * 指定された非同期入出力チャネルを使用するパイプラインを構築するためのユーティリティ
	 * メソッドです。
	 * @param channel 非同期入出力チャネル
	 * @param sink 入力データの通知先関数
	 * @return パイプライン
	 */
	def newPipeline(channel:SelectableChannel with ReadableByteChannel with WritableByteChannel)(sink:(ByteBuffer)=>Unit):Pipeline = {
		newPipeline(channel, channel)(sink)
	}

}
