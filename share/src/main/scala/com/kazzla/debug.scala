/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla

import java.lang.reflect.Method
import java.security.MessageDigest

package object debug {

	// ==============================================================================================
	// フィンガープリントの参照
	// ==============================================================================================
	/**
	 * 指定された公開鍵のフィンガープリントを参照します。
	 */
	def fingerprint(publicKey:java.security.PublicKey):String = {
		val bin = publicKey.getEncoded
		val hex = MessageDigest.getInstance("MD5").digest(bin).map{ b => f"${b&0xFF}%02x" }.mkString(":")
		val alg = publicKey.getAlgorithm
		s"${bin.length} $hex ($alg)"
	}

	implicit class RichMethod(method:Method){
		def getSimpleName:String = {
			method.getDeclaringClass.getSimpleName + "." + method.getName + "(" + method.getParameterTypes.map { p =>
				p.getSimpleName
			}.mkString(",") + "):" + method.getReturnType.getSimpleName
		}
	}

}
