/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.util

import java.io._
import com.kazzla.domain.async.RawBuffer

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// JSON
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object JSON {

	// ========================================================================
	// ストリームヘッダの参照
	// ========================================================================
	/**
	 * このコーデックを使用したストリームのヘッダを参照します。
	 */
	def toJSON(value:Any):String = value match {
		case null => "null"
		case flag:Boolean => flag.toString
		case num:Byte => num.toString
		case num:Short => num.toString
		case num:Int => num.toString
		case num:Long => num.toString
		case num:Float => num.toString
		case num:Double => num.toString
		case ch:Char => escape(ch)
		case str:String =>
			str.foldLeft(new StringBuilder(str.length + 5)){ case (buffer, ch) =>
				buffer.append(escape(ch))
			}.toString()
		case map:Map[_,_] =>
			map.map{ case (key,value) => toJSON(key.toString) + ":" + toJSON(value) }.mkString("{", ",", "}")
		case map:java.util.Map[_,_] =>
			map.map{ case (key,value) => toJSON(key.toString) + ":" + toJSON(value) }.mkString("{", ",", "}")
		case list:Seq[_] =>
			list.map{ toJSON(_) }.mkString("[", ",", "]")
		case list:java.util.List[_] =>
			list.map{ toJSON(_) }.mkString("[", ",", "]")
		case list:Array[_] =>
			list.map{ toJSON(_) }.mkString("[", ",", "]")
	}

	// ========================================================================
	// 文字のエスケープ
	// ========================================================================
	/**
	 * 指定された文字を JavaScript の文字列リテラル形式へエスケープします。
	 */
	private[this] def escape(ch:Char):String = match {
		case '\b' => "\\b"
		case '\f' => "\\f"
		case '\n' => "\\n"
		case '\r' => "\\r"
		case '\t' => "\\t"
		case '\u000B' => "\\v"
		case '\'' => "\\\'"
		case '\"' => "\\\""
		case '\\' => "\\\\"
		case c if ! Character.isDefined(c) || Character.isISOControl(c) =>
			"\\u%04X".format(c.toInt)
		case c => c.toString
	}
}
