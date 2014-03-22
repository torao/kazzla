/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.storage.fs

import scala.collection.mutable

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Path
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object Path {

	// ==============================================================================================
	// エスケープ文字
	// ==============================================================================================
	/**
	 * ファイルセパレータをエスケープするために使用する文字です。
	 */
	val Escape:Char = '\\'

	// ==============================================================================================
	// ファイルセパレータ
	// ==============================================================================================
	/**
	 * ファイルシステムで使用するファイルセパレータです。
	 */
	val SeparatorChar:Char = '/'

	// ==============================================================================================
	// ファイルセパレータ
	// ==============================================================================================
	/**
	 * ファイルシステムで使用するファイルセパレータです。
	 */
	val Separator:String = SeparatorChar.toString

	// ==============================================================================================
	// 使用できない文字
	// ==============================================================================================
	/**
	 * ファイルシステムで使用するファイルセパレータです。
	 */
	val InvalidChars:String = "\0"

	// ==============================================================================================
	// 正規形式の変換
	// ==============================================================================================
	/**
	 * 指定されたパスを正規形式に変換します。
	 */
	def canonical(path:String):String = canonicalSplit(path).map{ escape }.mkString(Separator)

	// ==============================================================================================
	// 分割パスの参照
	// ==============================================================================================
	/**
	 * 指定されたパスをファイルセパレータで分割した正規形式に変換します。
	 */
	def canonicalSplit(path:String):Seq[String] = {
		// 階層コンポーネントごとに分割
		val cmps = mutable.Buffer[String]()
		val buffer = new StringBuilder(path.length)
		var i = 0
		while(i < path.length){
			path.charAt(i) match {
				case Escape =>
					buffer.append(if (i + 1 < path.length) path.charAt(i) else '\\')
					i += 1
				case SeparatorChar =>
					cmps += buffer.toString()
					buffer.setLength(0)
				case ch =>
					if(InvalidChars.indexOf(ch) >= 0){
						// TODO 使用できない文字が含まれる例外
					}
					buffer.append(ch)
			}
			i += 1
		}
		if(buffer.length > 0){
			cmps += buffer.toString
		}
		// 空階層、相対位置を解決
		cmps.foldLeft(Seq[String]()){ case (c, n) =>
			n match {
				case ".." =>
					if(c.size == 0){
						// TODO 不正な階層指定の例外
					}
					c.dropRight(1)
				case "." => c
				case "" => c
				case cmp => c :+ cmp
			}
		}
	}

	// ==============================================================================================
	// パスコンポーネントのエスケープ
	// ==============================================================================================
	/**
	 * ファイル名をエスケープします。
	 */
	private[this] def escape(cmp:String):String = cmp.foldLeft(new StringBuffer(cmp.length)){ case (buffer, ch) =>
		if(ch == Escape)              buffer.append(Escape).append(Escape)
		else if(ch == SeparatorChar)  buffer.append(Escape).append(SeparatorChar)
		else                          buffer.append(ch)
		buffer
	}.toString

}
