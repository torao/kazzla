/*
 * Copyright (c) 2012 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * org.koiroha.firestorm.http://www.apache.org/licenses/LICENSE-2.0.html
 */

package org.koiroha.firestorm.core

import java.io.{PrintWriter, StringWriter}
import java.text.SimpleDateFormat
import java.util.Date
import java.util.logging._
import scala.collection.JavaConversions._

object EventLog {
	val FATAL = 0
	val ERROR = 1
	val WARN = 2
	val INFO = 3
	val DEBUG = 4
	val TRACE = 5

	private[this] val formatter = new Formatter {
		def format(record:LogRecord):String = {
			val tm = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(record.getMillis))
			val msg = "[%s] %7s %s%n".format(tm, record.getLevel.getName, record.getMessage)
			if(record.getThrown != null){
				val sw = new StringWriter()
				val pw = new PrintWriter(sw)
				record.getThrown.printStackTrace(pw)
				pw.flush()
				msg + sw.toString
			} else {
				msg
			}
		}
	}

	private[this] val logger = Logger.getLogger("org.koiroha.firestorm")
	private[this] val handler = new ConsoleHandler()

	{
		handler.setFormatter(formatter)
		handler.setLevel(Level.FINEST)
		logger.setLevel(Level.FINEST)
		logger.addHandler(handler)
		logger.setUseParentHandlers(false)

		val global = Logger.getLogger("")
		global.setLevel(Level.INFO)
		global.getHandlers.foreach { handler => global.removeHandler(handler) }
		global.addHandler(handler)
	}

	def fatal(message: => String):Unit = fatal(null, message)
	def fatal(ex:Throwable, message: => String):Unit = log(FATAL, message, ex)
	def error(message: => String):Unit = error(null, message)
	def error(ex:Throwable, message: => String):Unit = log(ERROR, message, ex)
	def warn(message: => String):Unit = warn(null, message)
	def warn(ex:Throwable, message: => String):Unit = log(WARN, message, ex)
	def info(message: => String):Unit = info(null, message)
	def info(ex:Throwable, message: => String):Unit = log(INFO, message, ex)
	def debug(message: => String):Unit = debug(null, message)
	def debug(ex:Throwable, message: => String):Unit = log(DEBUG, message, ex)
	def trace(message: => String):Unit = trace(null, message)
	def trace(ex:Throwable, message: => String):Unit = log(TRACE, message, ex)

	private[this] def log(level:Int, message: =>String, ex:Throwable = null){
		val l = level match {
			case FATAL => Level.SEVERE
			case ERROR => Level.SEVERE
			case WARN => Level.WARNING
			case INFO => Level.INFO
			case DEBUG => Level.FINE
			case TRACE => Level.FINEST
		}
		if(ex == null){
			logger.log(l, message)
		} else {
			logger.log(l, message, ex)
		}
	}

}
