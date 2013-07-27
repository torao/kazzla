package com.kazzla.util

import java.io._
import java.net._
import scala.collection.JavaConversions._
import scala._

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Config
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 *
 * @author Takami Torao
 * @param config 設定
 */
sealed class Config(private[this] val config:Map[String,String]) {
	import Config._
	def this(prop:java.util.Properties) = this(prop.map { e => e._1.toString -> e._2.toString }.toMap)
	def this(in:InputStream) = this(Config.load(in))
	def this(url:URL) = this(Config.load(url))
	def this(file:File) = this(file.toURI.toURL)

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
	def getOrElse(name:String, default:String):String = get(name) match {
		case None => default
		case Some(value) => value
	}

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を Option で参照します。
	 * @param name 設定の名前
	 * @return 設定値
	 */
	def getBoolean(name:String):Option[Boolean] = getWithType(name, value => {
		value match {
			case "true"  | "yes" | "on" => Some(true)
			case "false" | "no"  | "off" => Some(false)
			case other =>
				Config.logger.warn("parameter \"%s\" is not a boolean value: \"%s\"".format(name, other))
				None
		}
	})

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
	def getBooleanOrElse(name:String, default:Boolean):Boolean = getOrElseWithType(name, default, getBoolean)

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を Option で参照します。
	 * @param name 設定の名前
	 * @return 設定値
	 */
	def getInt(name:String):Option[Int] = getNumber(name, _.toInt)

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
	def getIntOrElse(name:String, default:Int):Int = getOrElseWithType(name, default, getInt)

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を Option で参照します。
	 * @param name 設定の名前
	 * @return 設定値
	 */
	def getLong(name:String):Option[Long] = getNumber(name, _.toLong)

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
	def getLongOrElse(name:String, default:Long):Long = getOrElseWithType(name, default, getLong)

	// ========================================================================
	// 設定値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する設定値を Option で参照します。
	 * @param name 設定の名前
	 * @return 設定値
	 */
	def getDouble(name:String):Option[Double] = getNumber(name, _.toDouble)

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
	def getDoubleOrElse(name:String, default:Double):Double = getOrElseWithType(name, default, getDouble)

	/**
	 * 設定値を適切な型に変換して返します。
	 * @param name 参照する値の名前
	 * @param convert 変換処理
	 * @tparam T 変換する値の型
	 * @return 設定値
	 */
	private[this] def getWithType[T](name:String, convert:(String)=>Option[T]):Option[T] = get(name) match {
		case None => None
		case Some(value) => convert(value)
	}

	/**
	 * 設定値を適切な型に変換して返します。値が設定されていない場合や要求された型に変換で
	 * きない場合はデフォルト値を返します。
	 * @param name 参照する値の名前
	 * @param default デフォルト値
	 * @param getter
	 * @tparam T
	 * @return
	 */
	private[this] def getOrElseWithType[T](name:String, default:T, getter:(String)=>Option[T]) = getter(name) match {
		case None => default
		case Some(value) => value
	}

	/**
	 * 設定値を数値型に変換して返します。変換処理内での NumberFormatException 発生で
	 * None を返す共通処理です。
	 * @param name
	 * @param convert
	 * @tparam T
	 * @return
	 */
	private[this] def getNumber[T](name:String, convert:String=>T):Option[T] = getWithType(name, value => try {
		Some(convert(value))
	} catch {
		case _:NumberFormatException =>
			Config.logger.warn("parameter \"%s\" is not a number value: \"%s\"".format(name, value))
			None
	})

	// ========================================================================
	// 設定値の結合
	// ========================================================================
	/**
	 * このインスタンスの下層に指定された設定を持つ新しい設定を構築して返します。このメソッ
	 * ドはデフォルト値を持つ新しい設定を作成するために使用します。
	 * @param default デフォルト値となる設定
	 */
	def combine(default:Config):Config = {
		new Config(config.foldLeft(default.toMap()){ (config, e) => config + e }.toMap)
	}

	// ========================================================================
	// サブ設定の参照
	// ========================================================================
	/**
	 * この設定から指定されたプレフィクスを持つ設定のサブ設定を参照します。
	 * @param prefix 抽出するプレフィクス
	 * @return サブ設定
	 */
	def subconfig(prefix:String):Config = {
		new Config(
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

	// ========================================================================
	// インスタンスのマップ化
	// ========================================================================
	/**
	 * この設定値を Map 化して返します。
	 * @return 設定値の文字列-文字列マップ
	 */
	def toMap():Map[String,String] = config

}

object Config {
	private[Config] val logger = org.apache.log4j.Logger.getLogger(Config.getClass)

	/**
	 * システムプロパティを Config として使用するためのクラス。
	 */
	private[this] class SystemProperties extends Config(new java.util.Properties()) {
		override def get(name:String):Option[String] = Option(System.getProperty(name))
	}

	// ========================================================================
	// システムプロパティ
	// ========================================================================
	/**
	 * システムプロパティを設定として使用するためのインスタンスです。実行中にシステムプロ
	 * パティが変更された場合、新しい値が反映されます。
	 */
	val SYSTEM_PROPERTIES = new SystemProperties()

	/**
	 * 指定された入力ストリームからプロパティをロードして返します。メソッド内でストリームの
	 * クローズは行われません。
	 * @param in プロパティを読み出す入力ストリーム
	 * @return ロードしたプロパティ
	 */
	private[Config] def load(in:InputStream):java.util.Properties = {
		val prop = new java.util.Properties()
		prop.load(new BufferedInputStream(in))
		prop
	}

	/**
	 * 指定された URL からプロパティをロードして返します。
	 * @param url プロパティをロードする URL
	 * @return ロードしたプロパティ
	 */
	private[Config] def load(url:URL):java.util.Properties = IO.using(url.openStream()){ in => load(in) }

}
