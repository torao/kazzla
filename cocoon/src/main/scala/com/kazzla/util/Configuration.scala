/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.util

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Configuration
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 * @param config 設定
 */
class Configuration(private val parent:Configuration, private val config:Map[String,String]) {
	import Configuration.logger

	// ========================================================================
	// コンストラクタ
	// ========================================================================
	/**
	 * システムプロパティをデフォルトとするコンフィグレーションを構築します。
	 * @param config 設定
	 */
	def this(config:Map[String,String]) = this(Configuration.SYSTEM_PROPERTIES, config)

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を Option で参照します。
	 * @param name 設定の名前
	 * @return 設定値
	 */
	def get(name:String):Option[String] = getAsType(name){ _.toString }

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を Option で参照します。
	 * @param name 設定の名前
	 * @return 設定値
	 */
	def getBoolean(name:String):Option[Boolean] = getAsType(name){
		_ match {
			case "true"  | "yes" | "on" => true
			case "false" | "no"  | "off" => false
			case other => throw new IllegalArgumentException("%s is not boolean value".format(other))
		}
	}

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を Option で参照します。
	 * @param name 設定の名前
	 * @return 設定値
	 */
	def getInt(name:String):Option[Int] = getAsType(name){ _.toInt }

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を Option で参照します。
	 * @param name 設定の名前
	 * @return 設定値
	 */
	def getLong(name:String):Option[Long] = getAsType(name){ _.toLong }

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を Option で参照します。
	 * @param name 設定の名前
	 * @return 設定値
	 */
	def getDouble(name:String):Option[Double] = getAsType(name){ _.toDouble }

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
	def getOrElse(name:String, default:String):String = getAsType(name, default){ _.toString }

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
	 * 指定された名前に対する設定値を参照します。名前に該当する値が存在しない場合や型に適切
	 * な値と見なされなければ None を返します。
	 * @param name 設定の名前
	 * @tparam T
	 * @return 設定値
	 */
	private[this] def getAsType[T](name:String)(translator:(String)=>T):Option[T] = getValue(name) match {
		case Some(value) => try {
			val result = translator(value)
			logger.trace("%s=%s".format(name, result))
			Option(result)
		} catch {
			case ex:Exception =>
				logger.trace("%s=None (%s)".format(name, ex))
				None
		}
		case None =>
			logger.trace("%s=None".format(name))
			None
	}

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
	private[this] def getAsType[T](name:String, default:T)(translator:(String)=>T):T = getValue(name) match {
		case Some(value) => try {
			val result = translator(value)
			logger.trace("%s=%s".format(name, result))
			result
		} catch {
			case ex:Exception =>
				logger.warn("%s=%s (%s)".format(name, default, ex))
				default
		}
		case None =>
			logger.trace("%s=%s (default)".format(name, default))
			default
	}

	// ========================================================================
	// 設定の参照
	// ========================================================================
	/**
	 * 指定された名前の設定をフォーマットして返します。
	 * @return 設定
	 */
	private[Configuration] def getValue(name:String):Option[String] = config.get(name) match {
		case Some(value) => Some(format(value))
		case None => parent.getValue(name)
	}

	// ========================================================================
	// サブ設定の参照
	// ========================================================================
	/**
	 * この設定から指定されたプレフィクスを持つ設定のサブ設定を参照します。
	 * @param prefix 抽出するプレフィクス
	 * @return サブ設定
	 */
	def subconfig(prefix:String):Configuration = {
		new Configuration(
			config.map{ case(key, value) =>
				(if(key == prefix){
					""
				} else if(key.startsWith(prefix + ".")){
					key.substring(prefix.length + 1)
				} else {
					null
				}) -> value
			}.filter{ case(key, _) => key != null }.toMap
		)
	}

	// ========================================================================
	// 文字列のフォーマット
	// ========================================================================
	/**
	 * 指定された文字列をこのコンフィギュレーションの値を使用してフォーマットします。
	 * $name, ${name}, ${name,default}
	 * $$ は文字 $ のエスケープと見なされます。
	 */
	def format(format:String):String = {
		val regex = """\$[a-zA-Z0-9\.\-_]+|\$\{[^\}]+\}|\$\$""".r
		regex.replaceAllIn(format, { m =>
			val elem = m.group(0)
			if(elem == "$$"){
				"$"
			} else if(elem.startsWith("${") && elem.endsWith("}")){
				val inner = elem.substring(2, elem.length - 1)
				val sep = inner.indexOf(":-")
				if(sep >= 0){
					getOrElse(inner.substring(0, sep).trim(), inner.substring(sep + 2))
				} else {
					getOrElse(inner, "")
				}
			} else if(elem.startsWith("$")){
				getOrElse(elem.substring(1), "")
			} else {
				logger.warn("unexpected placeholder format: " + elem)
				elem
			}
		})
	}

}

object Configuration {
	val logger = org.apache.log4j.Logger.getLogger(Configuration.getClass)

	private[this] class SystemProperties extends Configuration(Map()){
		def getValue(name:String):Option[String] = Option(System.getProperty(name))
	}
	val SYSTEM_PROPERTIES:Configuration = new SystemProperties()

}
