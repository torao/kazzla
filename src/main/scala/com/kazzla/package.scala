/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla

import java.text.{SimpleDateFormat, DateFormat, NumberFormat}


// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// デバッグ
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
package object debug {

	// ========================================================================
	// デバッグ文字列の作成
	// ========================================================================
	/**
	 * 指定された文字列をデバッグ出力用に整形します。
	 * @param value 整形する値
	 * @param stringLength 整形する文字列の最大長
	 * @return 整形した文字列
	 */
	def makeDebugString(value:Any, stringLength:Int = Integer.MAX_VALUE):String = {
		if(value == null){
			return "null"
		}

		// 文字のエスケープ
		def escape(buffer:StringBuilder, ch:Char):Unit = ch match {
			case '\0' => buffer.append("\\0")
			case '\b' => buffer.append("\\b")
			case '\n' => buffer.append("\\n")
			case '\r' => buffer.append("\\r")
			case '\t' => buffer.append("\\t")
			case '\\' => buffer.append("\\\\")
			case '\"' => buffer.append("\\\"")
			case '\'' => buffer.append("\\\'")
			case c if(java.lang.Character.isISOControl(c) || ! java.lang.Character.isDefined(c)) =>
				buffer.append("\\u%04X".format(c.toInt))
			case c => buffer.append(c)
		}

		lazy val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS")
		val buffer = new StringBuilder()
		value match {
			case flag:Boolean => buffer.append(flag)
			case num:Byte => buffer.append(num)
			case num:Short => buffer.append(num)
			case num:Int => buffer.append(num)
			case num:Long => buffer.append(num)
			case num:Float => buffer.append(num)
			case num:Double => buffer.append(num)
			case ch:Char =>
				buffer.append('\'')
				escape(buffer, ch)
				buffer.append('\'')
			case str:String =>
				buffer.append('\"')
				for(i <- 0 until scala.math.min(str.length, stringLength)){
					escape(buffer, str.charAt(i))
				}
				if(str.length > stringLength){
					buffer.append('…')
				}
				buffer.append('\"')
			case date:java.util.Date => buffer.append(df.format(date))
			case binary:Array[Byte] =>
				binary.foreach{ ch => buffer.append("%02X".format(ch & 0xFF)) }
			case array:Seq[Any] =>
				buffer.append('[')
				buffer.append(array.map{ makeDebugString(_) }.mkString(","))
				buffer.append(']')
			case map:Map[Any,Any] =>
				buffer.append('{')
				buffer.append(map.map{ case(k,v)=>makeDebugString(k) + "->" + makeDebugString(v) }.mkString(","))
				buffer.append('}')
			case obj =>
				buffer.append(obj)
		}
		buffer.toString()
	}

}
