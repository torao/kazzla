/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com

import org.apache.log4j.Logger

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// デバッグ
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
package object kazzla {

	class Config {
		private[this] val logger = Logger.getLogger(classOf[Config])

		private[this] val config = Map[String,String]()

		def get(name:String, f: =>String):String = config.get(name) match {
			case Some(value) => value
			case None => f
		}

		def get(name:String, default:Boolean):Boolean = get(name, default.toString).toBoolean

		def get(name:String, default:Int):Int = try {
			get(name, default.toString).toInt
		} catch {
			case ex:NumberFormatException =>
				logger.warn("configuration is not integer: " + name + "=" + value + "; " + ex)
				default
		}

		def get(name:String, default:Long):Long = try {
			get(name, default.toString).toLong
		} catch {
			case ex:NumberFormatException =>
				logger.warn("configuration is not long: " + name + "=" + value + "; " + ex)
				default
		}

	}

}
