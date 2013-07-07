package com.kazzla
import java.io._
import java.util.{Timer,TimerTask}
import scala.collection.immutable._
import java.net.{URL, URLClassLoader}

/**
 * サーバ及びアプリケーションの実行コンテキストを表すクラス。
 * @param dir ディレクトリ
 * @param defaultClassLoader
 */
class Context(dir:File, defaultClassLoader:ClassLoader) extends Closeable{
	lazy val config = new File(dir, "conf")
	lazy val classLoader = Context.getClassLoader(defaultClassLoader, new File(dir, "lib"))
	private[this] var _expired = false
	def expired:Boolean = _expired
	private[this] def expired_=(expired:Boolean) = _expired = expired

	def this(dir:File) = this(dir, Thread.currentThread().getContextClassLoader)

	/**
	 * このコンテキストをクローズし使用していたリソースを解放します。
	 */
	def close():Unit = {
		Context.leave(this)
	}

	/**
	 * 指定された処理をこのコンテキスト内で実行します。
	 * @param exec
	 * @tparam T
	 * @return
	 */
	def run[T](exec: =>T):T = {
		val defaultClassLoader = Thread.currentThread().getContextClassLoader
		try {
			Context.push(this)
			Thread.currentThread().setContextClassLoader(classLoader)
			val result:T = exec
			result
		} catch {
			case ex:Throwable =>
				Context.logger.error(ex)
				throw ex
		} finally {
			Thread.currentThread().setContextClassLoader(defaultClassLoader)
			Context.pop()
		}
	}

	private[Context] def touch():Unit = {
	}

}

object Context {
	private[Context] val logger = org.apache.log4j.Logger.getLogger(Context.getClass)
	private[this] var activeContexts = List[Context]()
	private[this] val watchdog = new Timer("Kazzla Context Watchdog Timer", true)
	private[this] val task = new TimerTask(){
		override def run():Unit = activeContexts.foreach { _.touch() }
	}
	watchdog.schedule(task, 2000, 2000)

	private[Context] def leave(context:Context) = activeContexts = activeContexts.filterNot{ _ == context }

	def create(dir:File):Context = {
		val context = new Context(dir)
		activeContexts ::= context
		context
	}

	private[Context] def getClassLoader(parent:ClassLoader, lib:File):ClassLoader = {
		lazy val findLibrary:(File)=>List[URL] = { dir =>
			dir.listFiles().filter{ f =>
				f.isFile && (f.getName.endsWith(".jar") || f.getName.endsWith(".zip"))
			}.map{ _.toURI.toURL }.toList ++ dir.listFiles().filter{
				d => d.isDirectory
			}.map{
				d => findLibrary(d)
			}.flatten.toList
		}
		new URLClassLoader(findLibrary(lib).toArray, parent)
	}

	private[this] val _currentContext = new ThreadLocal[List[Context]]()

	def currentContext:Context = Option(_currentContext.get) match {
		case Some(stack) => stack(0)
		case None => throw new IllegalStateException("current thread is not bound to any context")
	}

	private[Context] def push(context:Context):Unit = {
		_currentContext.set(Option(_currentContext.get) match {
			case Some(stack) => context :: stack
			case None => List(context)
		})
	}

	private[Context] def pop():Unit = {
		_currentContext.set(Option(_currentContext.get) match {
			case Some(current :: rest) => rest
			case Some(Nil) => throw new IllegalStateException()
			case None => throw new IllegalStateException()
		})
	}

}
