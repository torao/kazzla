package com.kazzla.util
import java.io._
import java.net._
import scala.collection.immutable._
import scala.collection.JavaConversions._

class Config(url:URL) {
	val file = if(url.getProtocol == "file") Some(new File(url.toURI)) else None
	val lastModified = file match {
		case Some(f) => f.lastModified
		case None => 0L
	}

	private[this] val config:Map[String,String] = IO.using(url.openStream()){ in =>
		val prop = new java.util.Properties()
		prop.load(new BufferedInputStream(in))
		prop.map { e => e._1.toString -> e._2.toString }.toMap
	}
	
	def get(name:String):Option[String] = config.get(name)
	def getOrElse(name:String, default:String):String = get(name) match {
		case None => default
		case Some(value) => value
	}

	def getBoolean(name:String):Option[Boolean] = getWithType(name, value => Some(value.toBoolean))
	def getBooleanOrElse(name:String, default:Boolean):Boolean = getOrElseWithType(name, default, getBoolean)

	def getInt(name:String):Option[Int] = getNumber(name, _.toInt)
	def getIntOrElse(name:String, default:Int):Int = getOrElseWithType(name, default, getInt)

	def getLong(name:String):Option[Long] = getNumber(name, _.toLong)
	def getLongOrElse(name:String, default:Long):Long = getOrElseWithType(name, default, getLong)

	def getDouble(name:String):Option[Double] = getNumber(name, _.toDouble)
	def getDoubleOrElse(name:String, default:Double):Double = getOrElseWithType(name, default, getDouble)

	private[this] def getWithType[T](name:String, convert:(String)=>Option[T]):Option[T] = get(name) match {
		case None => None
		case Some(value) => convert(value)
	}

	private[this] def getOrElseWithType[T](name:String, default:T, getter:(String)=>Option[T]) = getter(name) match {
		case None => default
		case Some(value) => value
	}
	
	private[this] def getNumber[T](name:String, convert:String=>T):Option[T] = getWithType(name, value => try {
		Some(convert(value))
	} catch {
		case _:NumberFormatException =>
			Config.logger.warn("parameter \"%s\" is not number value: \"%s\"".format(name, value))
			None
	})
}

object Config {
	val logger = org.apache.log4j.Logger.getLogger(Config.getClass)
}
