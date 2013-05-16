package com.kazzla
import java.io._
import java.util.{Timer,TimerTask}
import scala.collection.immutable._
import java.net.{URL, URLClassLoader}

class Context(dir:File, defaultClassLoader:ClassLoader) {
	val config = new File(dir, "conf")
	val classLoader = Context.getClassLoader(defaultClassLoader, new File(dir, "lib"))
	var expired = false

	def this(dir:File) = this(dir, Thread.currentThread().getContextClassLoader)

	def close():Unit = {
		Context.leave(this)
	}

	def run[T](exec: =>T):T = {
		val defaultClassLoader = Thread.currentThread().getContextClassLoader
		try {
			Thread.currentThread().setContextClassLoader(classLoader)
			val result:T = exec
			result
		} catch {
			case ex:Throwable =>
				Context.logger.error(ex)
				throw ex
		} finally {
			Thread.currentThread().setContextClassLoader(defaultClassLoader)
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

}
