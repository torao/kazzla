/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package org.koiroha.wiredrive.util

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Configuration
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Configuration(config:Map[String,String]) {
	import Configuration.logger

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を Option で参照します。
	 * @param name 設定の名前
	 * @return 設定値
	 */
	def get(name:String):Option[String] = config.get(name)

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を参照します。名前に該当する値が存在しなければデフォルト
	 * 値を返します。
	 * @param name 設定の名前
	 * @param default デフォルト値
	 * @return 設定値
	 */
	def getOrElse(name:String, default:String):String = config.getOrElse(name, default)

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を参照します。名前に該当する値が存在しなければデフォルト
	 * 値を返します。
	 * @param name 設定の名前
	 * @param default デフォルト値
	 * @return 設定値
	 */
	def getOrElse(name:String, default:Int):Int = getAsType(name, default){ _.toInt }

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を参照します。名前に該当する値が存在しなければデフォルト
	 * 値を返します。
	 * @param name 設定の名前
	 * @param default デフォルト値
	 * @return 設定値
	 */
	def getOrElse(name:String, default:Long):Long = getAsType(name, default){ _.toLong }

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を参照します。名前に該当する値が存在しなければデフォルト
	 * 値を返します。
	 * @param name 設定の名前
	 * @param default デフォルト値
	 * @return 設定値
	 */
	def getOrElse(name:String, default:Float):Float = getAsType(name, default){ _.toFloat }

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を参照します。名前に該当する値が存在しなければデフォルト
	 * 値を返します。
	 * @param name 設定の名前
	 * @param default デフォルト値
	 * @return 設定値
	 */
	def getOrElse(name:String, default:Double):Double = getAsType(name, default){ _.toDouble }

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を参照します。名前に該当する値が存在しなければデフォルト
	 * 値を返します。
	 * @param name 設定の名前
	 * @param default デフォルト値
	 * @return 設定値
	 */
	private def getAsType[T](name:String, default:T)(conv:(String)=>T):T = {
		get(name) match {
			case Some(value) => try {
				val result = conv(value)
				logger.trace("%s=%s", name, result)
				result
			} catch {
				case ex:Exception =>
					logger.warn("%s=%s (%s)", name, default, ex)
					default
			}
			case None =>
				logger.trace("%s=%s (default)", name, default)
				default
		}
	}

}

object Configuration {
	val logger = Logger.getLogger(classOf[Configuration])
}
