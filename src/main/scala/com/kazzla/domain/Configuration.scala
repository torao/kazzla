/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain

import java.security.{Signature, PrivateKey}
import java.util.Properties
import org.apache.log4j.Logger

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Configuration
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Configuration(config:Map[String,String]) {
	import Configuration._

	// ========================================================================
	// 電子署名アルゴリズム
	// ========================================================================
	/**
	 * このドメインで使用している電子署名のアルゴリズムです。
	 */
	lazy val signatureAlgorithm = apply("security.signature.algorithm", "SHA256withRSA")

	// ========================================================================
	// 値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する値を参照します。該当する値が存在しない場合は None を返します。
	 */
	def get(name:String):Option[String] = {
		val value = config.get(name)
		if(logger.isDebugEnabled && ! Configuration.output.contains(name)){
			logger.debug(name + "=" + (value match {
				case Some(str) => com.kazzla.debug.makeDebugString(str)
				case None => "None"
			}))
			output = output + name
		}
		value
	}

	// ========================================================================
	// 値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する値を参照します。
	 */
	def apply(name:String, default: =>String):String = {
		val value = config.getOrElse(name, default)
		if(logger.isDebugEnabled && ! Configuration.output.contains(name)){
			logger.debug(name + "=" + com.kazzla.debug.makeDebugString(value))
			output = output + name
		}
		value
	}

	// ========================================================================
	// 値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する値を参照します。
	 */
	def apply(name:String, default: =>Boolean):Boolean = {
		get(name) match {
			case Some(value) => value.toBoolean
			case None => default
		}
	}

	// ========================================================================
	// 値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する値を参照します。
	 */
	def apply(name:String, default: =>Int):Int = {
		get(name) match {
			case Some(value) =>
				try {
					value.toInt
				} catch {
					case ex:NumberFormatException =>
						logger.warn("configuration is not int: " + name + "=" + value + "; " + ex)
						default
				}
			case None => default
		}
	}

	// ========================================================================
	// 値の参照
	// ========================================================================
	/**
	 * 指定された名前に対する値を参照します。
	 */
	def apply(name:String, default: =>Long):Long = {
		get(name) match {
			case Some(value) =>
				try {
					value.toLong
				} catch {
					case ex:NumberFormatException =>
						logger.warn("configuration is not long: " + name + "=" + value + "; " + ex)
						default
				}
			case None => default
		}
	}

}

object Configuration {
	private[Configuration] val logger = Logger.getLogger(classOf[Configuration])
	private[Configuration] var output = Set[String]()
}