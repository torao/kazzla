/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.client.storage

import com.kazzla.asterisk._
import com.kazzla.storage.StorageNode
import com.kazzla.storage.{Status, Fragment, Location, StorageService}
import java.io._
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.util.concurrent.{ArrayBlockingQueue, LinkedBlockingDeque}
import scala.Some
import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Promise, Await, Future}
import scala.util.Try

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Storage
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Storage(session:Session)(implicit ctx:ExecutionContext) {

	private[this] val storage = session.bind(classOf[StorageService])

	// ==============================================================================================
	// ファイルステータスの参照
	// ==============================================================================================
	/**
	 * 指定されたファイルまたはディレクトリのステータスを参照します。
	 */
	def status(path:String):Status = Await.result(storage.status(path), Duration.Inf)

	// ==============================================================================================
	// ファイル一覧の参照
	// ==============================================================================================
	/**
	 * 指定されたディレクトリ直下に存在するファイルの一覧をストリームで参照します。非同期パイプに対してファイル名の
	 * 文字列をブロック送信します。
	 *
	 * @param path ディレクトリのパス
	 * @return ディレクトリのステータス
	 */
	def list(path:String):Seq[String]
		= Await.result(session.open(101, path){ _.src.filterNot{ _.isEOF }.map{ _.getString("UTF-8") }.toSeq }, Duration.Inf).asInstanceOf[Seq[String]]

	// ==============================================================================================
	// ファイルフラグメントの参照
	// ==============================================================================================
	/**
	 * 指定されたファイルのフラグメントロケーションを取得します。非同期パイプに対してシリアライズされた [[Fragment]]
	 * をブロック送信します。
	 */
	def getInputStream(path:String):InputStream
		= Await.result(session.open(102, path){ read }, Duration.Inf).asInstanceOf[InputStream]

	// ==============================================================================================
	// ファイル領域の割り当て
	// ==============================================================================================
	/**
	 * 指定されたファイルに対する領域割り当てを行います。非同期パイプに対して残りのデータサイズ (不明な場合は負の値)
	 * を送信することでリージョンサービスはファイルの新しい領域を割り当てて [[Fragment]] で応答します。クライアント
	 * は割り当てられた領域すべてにデータを書き終えたら残りのデータサイズを送信して次の領域を割り当てます。
	 */
	def getOutputStream(path:String, option:Int):OutputStream = {
		val out = Promise[OutputStream]()
		session.open(103, path, option){ pipe =>
			val promise = Promise[Unit]()
			out.success(new O(pipe, promise))
			promise.future
		}
		Await.result(out.future, Duration.Inf)
	}

	// ==============================================================================================
	// ファイルの削除
	// ==============================================================================================
	/**
	 * 指定されたファイルを削除します。
	 */
	def delete(path:String):Unit = Await.result(storage.delete(path), Duration.Inf)

	private[this] def read(pipe:Pipe):Future[InputStream] = {
		val in = new PipedInputStream()
		val out = new PipedOutputStream(in)
		val queue = new LinkedBlockingDeque[Fragment]()
		@tailrec
		def f():Unit = Option(queue.take()) match {
			case Some(fragment) =>
				var written = 0L
				fragment.locations.find{ l =>
					val addr = new InetSocketAddress(l.host, l.port)
					Try {
						val future = using(Await.result(pipe.session.node.connect(addr, None), Duration.Inf)) { node =>
							node.open(StorageNode.Read, fragment.copy(locations = Seq(l))) { np =>
								np.useInputStream()
								concurrent.future {
									np.in.skip(written)
									copy(new Array[Byte](1024), np.in, out){ len => written += len }
								}
							}
						}
						Await.result(future, Duration.Inf)
					}.isSuccess
				}
				f()
			case None => None
		}
		concurrent.future { f() }
		pipe.src.foreach{ block => queue.put(Fragment.fromBlock(block)) }
		Future(in)
	}

	@tailrec
	private[this] def copy(buffer:Array[Byte], in:InputStream, out:OutputStream)(f:(Int)=>Unit):Unit = {
		val len = in.read(buffer)
		if(len > 0){
			out.write(buffer, 0, len)
			f(len)
			copy(buffer, in, out)(f)
		}
	}

}

private[storage] class O(pipe:Pipe, promise:Promise[Unit]) extends OutputStream {
	private case class State(pipe:Pipe, fragment:Fragment, promise:Promise[Unit])
	private[this] var state:Option[State] = None

	val buffer = ByteBuffer.allocate(4 * 1024)
	val minus = new Array[Byte](4)
	locally {
		ByteBuffer.wrap(minus).putInt(-1)
	}
	var writtenSizeToNode = 0

	private[this] val queue = new ArrayBlockingQueue[Block](1)
	pipe.src.foreach{ block => queue.add(block) }

	override def write(b:Int):Unit = {
		if(! buffer.hasRemaining){
			flush()
		}
		buffer.put(b.toByte)
	}
	override def write(b:Array[Byte]):Unit = write(b, 0, b.length)
	override def write(b:Array[Byte], offset:Int, length:Int):Unit = {
		var written = 0
		while(written < length){
			if(! buffer.hasRemaining){
				flush()
			}
			if(length - written <= buffer.remaining()){
				buffer.put(b, offset + written, length - written)
				written += length - written
			} else {
				val len = buffer.remaining()
				buffer.put(b, offset + written, len)
				written += len
			}
		}
	}
	override def flush() = if(buffer.position() > 0){
		buffer.flip()
		_flush()
	}
	override def close() = {
		flush()
		state.foreach { _.promise.success(()) }
		state = None
		promise.success(())
	}
	@tailrec
	private[this] def _flush():Unit = {
		if(state.isEmpty){
			takeOver()
		}
		val s = state.get
		if(writtenSizeToNode + buffer.remaining() <= s.fragment.length){
			s.pipe.sink.send(buffer.array(), buffer.arrayOffset(), buffer.remaining())
			writtenSizeToNode += buffer.remaining()
			buffer.clear()
		} else {
			val len = s.fragment.length - writtenSizeToNode
			s.pipe.sink.send(buffer.array(), buffer.arrayOffset(), len)
			writtenSizeToNode += len
			buffer.position(buffer.position() + len)
		}
		if(writtenSizeToNode == s.fragment.length){
			state.foreach { _.promise.success(()) }
			state = None
			_flush()
		}
	}
	private[this] def takeOver():Unit = {
		writtenSizeToNode = 0

		// 残りのサイズを送信 (未定のため負の値)
		pipe.sink.send(minus)
		// 割り当てられた領域を受信
		val fragment = Fragment.fromBlock(queue.take())
		fragment.locations.foreach{ case Location(id, host, port) =>
			try {
				// 割り当てられた領域のノードと接続して書き込み処理を呼び出し
				val future = pipe.session.node.connect(new InetSocketAddress(host, port), None)
				val session = Await.result(future, Duration.Inf)
				session.open(StorageNode.Write, fragment){ p =>
					val promise = Promise[Unit]()
					state = Some(State(pipe, fragment, promise))
					promise.future
				}
				return
			} catch {
				case ex:InterruptedException =>
					val e = new InterruptedIOException(s"operation interrupted")
					e.initCause(ex)
					throw e
				case ex:ThreadDeath => throw ex
				case ex:Throwable => None
			}
		}
		throw new IOException(s"fail to connect to allocated nodes: ${fragment.locations.mkString(",")}")
	}

}