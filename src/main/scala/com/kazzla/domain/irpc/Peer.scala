/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.irpc

import java.util.concurrent.atomic.{AtomicInteger, AtomicBoolean}
import java.io.{IOException, OutputStream, InputStream}
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import org.koiroha.wiredrive.util.Logger
import com.kazzla.domain.async.RawBuffer
import javax.security.cert.X509Certificate
import java.net.URI
import com.kazzla.domain.Service

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Peer
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * <p>
 * リモートノードを表すクラス。
 * </p>
 * @author Takami Torao
 */
class Peer(val uri:URI, myCertification:X509Certificate, stream:Protocol, bulk:Protocol, var services:Map[String,(Any*)=>Any], executor:Executor) {
	import Peer.{logger, scalaArgsToJava}
/*
パイプライン
リモート実行中の処理
ローカル実行中の処理
リモートサービス一覧
ローカルサービス(固有)
プロパティ等
ノード証明書(リモート)
 */

	// 初期化処理
	{
		// ストリーム接続、バルク接続それぞれにディスパッチャーを設定
		stream.dispatch = dispatch
		bulk.dispatch = dispatch
		// ノード証明書の送信
		val control = new Control(0, Control.INITIALIZE, myCertification.getEncoded)
		stream.send(control)
	}

	// ========================================================================
	// リモート処理中パイプ
	// ========================================================================
	/**
	 * リモート処理中のパイプです。
	 */
	val remoteProcessing = new PipePool(false)

	// ========================================================================
	// ローカル処理中パイプ
	// ========================================================================
	/**
	 * ローカル処理中のパイプです。
	 */
	val localProcessing = new PipePool(true)

	// ========================================================================
	// クローズフラグ
	// ========================================================================
	/**
	 * このコンテキストがクローズ済みかどうかを表すフラグです。
	 */
	private[this] val _closed = new AtomicBoolean(false)

	// ========================================================================
	// 相手側証明書
	// ========================================================================
	/**
	 * この通信相手の証明書です。証明書の交換が行われていない場合は None となります。
	 */
	private[this] var _peerCert:Option[X509Certificate] = None

	// ========================================================================
	// 初期化官僚通知用シグナル
	// ========================================================================
	/**
	 * 相手側と初期化処理が完了状態になった時に通知を行うためのシグナルです。
	 */
	private[this] val initSignal = new Object()

	// ========================================================================
	// サービスの設定
	// ========================================================================
	/**
	 * 指定された名前に新しいサービスをバインドします。この変更は既に生成されている `Peer`
	 * には影響しません。
	 */
	def bind(name:String, service:Service):Unit = synchronized{
		services += (name -> service)
	}

	// ========================================================================
	// サービスの削除
	// ========================================================================
	/**
	 * 指定された名前にバインドされているサービスを削除します。この変更は既に生成されている
	 * `Peer` には影響しません。
	 */
	def unbind(name:String):Unit = synchronized{
		services -= name
	}

	// ========================================================================
	// リモート証明書の参照
	// ========================================================================
	/**
	 * このピアのノード証明書を参照します。ノード証明書を受信していない場合は指定された
	 * タイムアウト時間まで受信を待機します。タイムアウト時間を過ぎても証明書が受信できない
	 * 場合は例外が発生します。
	 * @param timeout 証明書受信までのタイムアウト時間 (ミリ秒)
	 */
	def getCertification(timeout:Long) = initSignal.synchronized{
		_peerCert match {
			case Some(cert) => cert
			case None =>
				initSignal.wait(timeout)
				if(_peerCert.isDefined){
					// TODO 例外クラス追加
					throw new Exception("certification exchange timeout")
				} else {
					_peerCert.get
				}
		}
	}

	// ========================================================================
	// パイプのオープン
	// ========================================================================
	/**
	 * <p>
	 * パイプをオープンします。
	 * </p>
	 * <p>
	 * `timeout` はリモートで実行する処理が自主的に Cancel 状態で終了しても良いタイムア
	 * ウト時間をミリ秒表します。
	 * </p>
	 * <p>
	 * `callback` はリモートサービスの処理が終了した時に Close コールバックが必要かどう
	 * かを表すフラグです。処理の呼び出しのみで結果を待つ必要がない場合に false を指定する
	 * とクローズ状態の Pipe が返されます。このパイプに対しての apply() はエラーとなります。
	 * </p>
	 * @param name サービス名
	 * @param args サービスの引数
	 * @param timeout 処理のタイムアウト時間 (ミリ秒)
	 * @param callback コールバック (終了時の Close リターン) の有無
	 */
	def open(name:String, args:Array[Any], timeout:Long, callback:Boolean):Pipe = {

		// 既にクローズされていたら例外
		if(_closed.get()){
			throw new IllegalStateException("connection closed")
		}

		// パイプの作成
		val pipe = remoteProcessing.create()
		try {

			// オープン命令の送信
			val open = new Open(pipe.id, timeout, callback, name, scalaArgsToJava(args:_*))
			pipe.open = Some(open)
			stream.send(open)

		} catch {
			case ex:Throwable =>
				remoteProcessing.close(pipe.id)
				throw ex
		} finally {
			if(! callback){
				// コールバックが不要な場合はパイプをクローズ受信済みにする
				pipe.setCloseState(new Close(pipe.id, Close.Code.NONE, "resulting callback disabled"))
			}
		}
		pipe
	}

	// ========================================================================
	// ピアのクローズ
	// ========================================================================
	/**
	 * <p>
	 * このピアとの接続を終了します。
	 * </p>
	 */
	def close():Unit = {
		_closed.set(true)
		remoteProcessing.closeAll()
		localProcessing.closeAll()
	}

	// ========================================================================
	// 転送ユニットのディスパッチ
	// ========================================================================
	/**
	 * 指定された転送ユニットを受信した時に呼び出されます。
	 */
	private[this] def dispatch(unit:Transferable):Unit = unit match {

		// RPC 要求の場合はパイプを生成しスレッドプール上でサービスを実行
		case open:Open =>
			val pipe = localProcessing.create()
			executor.execute(new Runnable {
				def run():Unit = runService(pipe, open)
			})

		// ローカル実行中のスレッドに割り込みをかけパイプをプールから除去
		case close:Close =>
			localProcessing.get(close.pipeId) match {
				case Some(pipe) =>
					pipe.setCloseState(close)
					pipe.thread.foreach{ _.interrupt() }
				case None =>
					logger.debug("abandand close: " + close)
			}

		// ローカル実行中のパイプにブロックデータを設定
		case block:Block =>
			localProcessing.get(block.pipeId) match {
				case Some(pipe) =>
					pipe.in.asInstanceOf[PipeInputStream].enqueue(block)
				case None =>
					logger.warn("abandand block: " + block)
			}

		// 通信制御系処理
		case control:Control =>
			control.code match {

				// 操作なし
				case Control.NOOP =>
					logger.debug("noop caught")

				// 通信の初期化
				case Control.INITIALIZE =>
					if(_peerCert.isDefined){
						logger.warn("multiple certification specified from peer")
						close()
					} else initSignal.synchronized {
						// 証明書の復元
						// TODO 証明書の指定がない場合などのプロトコル違反ケース
						val cert = X509Certificate.getInstance(control.args(0).asInstanceOf[Array[Byte]])
						_peerCert = Some(cert)
						initSignal.notifyAll()
					}

				// ローカル処理のキャンセル
				case Control.CANCEL =>
					localProcessing.get(control.pipeId) match {
						case Some(pipe) => pipe.cancelLocalThread()
						case None => logger.warn("abandand cancel control: " + control)
					}

				// 未定義のコントロールコード
				case unknown =>
					logger.warn("unsupported control code: 0x%02X".format(control.code & 0xFF))
			}
	}

	// ========================================================================
	// サービスの実行
	// ========================================================================
	/**
	 * 指定されたサービスを実行します。このメソッドはスレッドプール内のスレッドで実行される
	 * 事を想定しています。
	 */
	private[this] def runService(pipe:PipeImpl, open:Open):Unit = {
		val close = try {
			// この実行スレッドにこのパイプを結びつける
			Pipe.setPipe(Some(pipe))
			pipe.thread = Some(Thread.currentThread())
			// 名前に該当するサービスを参照して実行
			services.get(open.name) match {
				case Some(service) =>
					val result = service(open.args)
					new Close(pipe.id, Close.Code.EXIT, "", scalaArgsToJava(result))
				case None =>
					new Close(pipe.id, Close.Code.ERROR, "unknown service: " + com.kazzla.debug.makeDebugString(open.name))
			}
		} catch {
			case ex:InterruptedException =>
				new Close(pipe.id, Close.Code.CANCEL, ex.getMessage)
			case ex:CancelException =>
				new Close(pipe.id, Close.Code.CANCEL, ex.getMessage)
			case ex:Throwable =>
				new Close(pipe.id, Close.Code.ERROR, ex.toString)
		} finally {
			// パイプをスレッドから切り離し
			Pipe.setPipe(None)
			pipe.thread = None
		}

		// コールバックが必要であればクローズを送信する
		if(open.callback){
			stream.send(close)
		}
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// PipeImpl
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * <p>
	 * パイプの実装クラスです。
	 * </p>
	 * @author Takami Torao
	 */
	private[Peer] class PipeImpl(id:Long, pool:PipePool) extends Pipe{
		assert(id != 0)

		// ======================================================================
		// シグナル
		// ======================================================================
		/**
		 * Close が到着したことを通知するためのシグナルです。
		 */
		private[this] val signal = new Object()

		// ======================================================================
		// 開始処理
		// ======================================================================
		/**
		 * パイプ処理の結果です。
		 */
		var open:Option[Open] = None

		// ======================================================================
		// キャンセルフラグ
		// ======================================================================
		/**
		 * このパイプがリモートによってキャンセル命令を受けているかのフラグです。
		 */
		private[this] val canceled = new AtomicBoolean(false)

		// ======================================================================
		// 処理結果
		// ======================================================================
		/**
		 * パイプ処理の結果です。
		 */
		private[this] var close:Option[Close] = None

		// ======================================================================
		// 処理スレッド
		// ======================================================================
		/**
		 * このパイプの処理を行なっているスレッドです。
		 */
		var thread:Option[Thread] = None

		// ======================================================================
		// 出力シーケンス番号
		// ======================================================================
		/**
		 * ブロック転送のためのシーケンス値です。
		 */
		private[this] val sequence = new AtomicInteger(0)

		// ======================================================================
		// 入力ストリーム
		// ======================================================================
		/**
		 */
		lazy val in:InputStream = new PipeInputStream()

		// ======================================================================
		// 出力ストリーム
		// ======================================================================
		/**
		 */
		lazy val out:OutputStream = new PipeOutputStream(id, 4 * 1024, stream, sequence)

		// ======================================================================
		// ローカル呼び出しパイプ判定
		// ======================================================================
		/**
		 */
		def local:Boolean = pool.local

		// ========================================================================
		// バルク転送
		// ========================================================================
		/**
		 * 指定されたバイナリデータをバルク転送します。
		 * バルク転送は到着と順序の保証がない高速な転送です。ストリーミングやブロックデバイスの
		 * 転送に使用することができます。
		 * <p>
		 * データのサイズは 40kb 以下の必要があります。
		 */
		def bulkTransfer(buffer:ByteBuffer):Unit = {
			val block = new Block(id, sequence.getAndIncrement, buffer.array())
			bulk.send(block)
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

			// キャンセル命令が行えるのはリモートからのみ
			if(local){
				throw new IllegalStateException("pipe for local service cannot cancel")
			}

			// キャンセルの制御を送信
			canceled.set(true)
			stream.send(new Control(id, Control.CANCEL, scalaArgsToJava(reason)))
		}

		// ======================================================================
		// 処理のキャンセル
		// ======================================================================
		/**
		 * このパイプのローカル処理をキャンセルします。
		 */
		def cancelLocalThread(){
			canceled.set(true)
			thread.foreach{ _.interrupt() }
		}

		// ======================================================================
		// キャンセルの判定
		// ======================================================================
		/**
		 * このパイプが自分または相手側によってキャンセルされているかを判定します。
		 * @return キャンセルされている場合 true
		 */
		def isCanceled:Boolean = canceled.get()

		// ======================================================================
		// 結果の設定
		// ======================================================================
		/**
		 * このパイプをクローズ状態にします。
		 */
		def setCloseState(code:Close.Code, msg:String, args:Any*):Unit = {
			setCloseState(new Close(id, code, msg, scalaArgsToJava(args:_*)))
		}

		// ======================================================================
		// 結果の設定
		// ======================================================================
		/**
		 * このパイプをクローズ状態にします。転送ユニットの送信は行われません。
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
	// PipeOutputStream
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	private[Peer] class PipeInputStream extends InputStream {
		private[this] val buffer = new RawBuffer("PipeInputStream")
		def read():Int = {
			// TODO 未実装
			throw new IOException("not implemented")
		}
		def read(b:Array[Byte]):Int = {
			// TODO 未実装
			throw new IOException("not implemented")
		}
		def read(b:Array[Byte], offset:Int, length:Int):Int = {
			// TODO 未実装
			throw new IOException("not implemented")
		}
		def enqueue(block:Block) = {
			// TODO ブロックの順序性の確認
			buffer.enqueue(block.binary)
		}
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// PipeOutputStream
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	private[Peer] class PipeOutputStream(pipeId:Long, bufSize:Int, protocol:Protocol, sequence:AtomicInteger) extends OutputStream {

		// ======================================================================
		// クローズフラグ
		// ======================================================================
		/**
		 * このストリームがクローズされているかのフラグです。
		 */
		private[this] val closed = new AtomicBoolean(false)

		// ======================================================================
		// バッファ
		// ======================================================================
		/**
		 * 内部バッファです。
		 */
		private[this] val buffer = ByteBuffer.allocate(bufSize)

		// ======================================================================
		// バイト値の出力
		// ======================================================================
		/**
		 * バイト値を出力します。
		 */
		def write(b:Int):Unit = buffer.synchronized{
			ensureOpened()
			if(buffer.remaining() == 0){
				flush(false)
			}
			buffer.put(b.toByte)
		}

		// ======================================================================
		// バイナリの出力
		// ======================================================================
		/**
		 * バイナリを出力します。
		 */
		def write(b:Array[Byte]):Unit = write(b, 0, b.length)

		// ======================================================================
		// バイナリの出力
		// ======================================================================
		/**
		 * バイナリを出力します。
		 */
		def write(b:Array[Byte], offset:Int, length:Int):Unit = buffer.synchronized{
			var start = offset
			var left = length
			do {
				ensureOpened()
				if(buffer.remaining() == 0){
					flush(false)
				}
				val len = scala.math.min(buffer.remaining(), left)
				buffer.put(b, start, left)
				left += len
				start += len
			} while(left > 0)
		}

		// ======================================================================
		// ストリームのフラッシュ
		// ======================================================================
		/**
		 * バッファに保存されているデータをフラッシュします。
		 */
		def flush():Unit = flush(true)

		// ======================================================================
		// ストリームのフラッシュ
		// ======================================================================
		/**
		 * バッファに保存されているデータをフラッシュします。
		 */
		private[this] def flush(waitFinish:Boolean):Unit = buffer.synchronized{
			if(waitFinish || buffer.position() > 0){
				val seq = sequence.getAndIncrement
				val binary = new Array[Byte](buffer.position())
				buffer.flip()
				buffer.get(binary)
				val future = stream.send(new Block(pipeId, seq, binary))
				future()
			}
		}

		def close():Unit = {
			flush(false)
			closed.set(true)
		}

		private[this] def ensureOpened():Unit = {
			if(closed.get()){
				throw new IOException("stream closed")
			}
		}

	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// PipePool
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	class PipePool private[irpc] (val local:Boolean) {

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
		private[this] var nextPipeId:Long = 1

		// ======================================================================
		// パイプ数の参照
		// ======================================================================
		/**
		 * 現在プールされているパイプ数を参照します。
		 */
		def pooledPipeCount = processing.size

		// ======================================================================
		//
		// ======================================================================
		/**
		 */
		def get(pipeId:Long):Option[PipeImpl] = processing.get(pipeId)

		// ======================================================================
		// パイプの作成
		// ======================================================================
		/**
		 * 新規にパイプを作成しこのプールに追加します。
		 */
		private[Peer] def create():PipeImpl = synchronized{

			// 新しいパイプ ID の取得
			// パイプ ID 0 は制御のため予約されている
			while(processing.contains(nextPipeId)){
				nextPipeId += (if(nextPipeId + 1 == 0) 2 else 1)
			}
			val pipeId = nextPipeId
			nextPipeId += (if(nextPipeId + 1 == 0) 2 else 1)

			// パイプの作成
			val pipe = new PipeImpl(pipeId, this)
			processing += (pipeId -> pipe)
			pipe
		}

		// ======================================================================
		// パイプ処理の終了
		// ======================================================================
		/**
		 * 指定されたパイプ ID の処理が終了した時に呼び出されます。
		 */
		private[Peer] def close(pipeId:Long):Unit = synchronized{
			processing -= pipeId
		}

		// ======================================================================
		// パイプ処理の終了
		// ======================================================================
		/**
		 * 指定されたパイプ ID の処理が終了した時に呼び出されます。
		 */
		private[Peer] def closeAll():Unit = synchronized{
			processing.values.foreach{ pipe =>
				try{
					if(local){
						pipe.cancelLocalThread()
					} else {
						pipe.cancel()
					}
				} catch {
					case ex:Exception => logger.error("fail to close pipe: " + pipe, ex)
				}
			}
		}

	}

}

object Peer {
	private[Peer] val logger = Logger.getLogger(classOf[Peer])

	private[Peer] def scalaArgsToJava(args:Any*):Array[Object] = {
		(args.map{
			case flag:Boolean => java.lang.Boolean.valueOf(flag)
			case num:Byte => java.lang.Byte.valueOf(num)
			case num:Short => java.lang.Short.valueOf(num)
			case num:Int => java.lang.Integer.valueOf(num)
			case num:Long => java.lang.Long.valueOf(num)
			case num:Float => java.lang.Float.valueOf(num)
			case num:Double => java.lang.Double.valueOf(num)
			case value:AnyRef => value
		}).toArray[Object]
	}

}