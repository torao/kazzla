/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain.irpc

import java.nio.ByteBuffer
import com.kazzla.debug._
import scala.Some

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Transferable
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
sealed abstract class Transferable private[irpc]() extends java.io.Serializable

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Open
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
final case class Open(id:Long, timeout:Long, callback:Boolean, name:String, args:Any*) extends Transferable {

	// ========================================================================
	// インスタンスの文字列化
	// ========================================================================
	/**
	 * このインスタンスを文字列化します。
	 * @return インスタンスの文字列
	 */
	override def toString():String = {
		"Open[" + id + "](" + timeout + "," + callback + "," + name + '(' + makeDebugString(args) + ')'
	}

}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Close
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
final case class Close(id:Long, code:Close.Code.Value, message:String, args:Any*) extends Transferable {

	// ========================================================================
	// インスタンスの文字列化
	// ========================================================================
	/**
	 * このインスタンスを文字列化します。
	 * @return インスタンスの文字列
	 */
	override def toString():String = {
		"Close[" + id + "](" + (code match {
			case Close.Code.CLOSE => com.kazzla.debug.makeDebugString(result)
			case _ => message
		}) + ")"
	}

}
object Close {
	object Code extends Enumeration {
		val CLOSE = Value(1)
		val CANCEL = Value(2)
		val ERROR = Value(3)
		val FATAL = Value(127)
	}
}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Block
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
final case class Block(id:Long, sequence:Int, binary:Array[Byte]) extends Transferable {

	// ========================================================================
	// インスタンスの文字列化
	// ========================================================================
	/**
	 * このインスタンスを文字列化します。
	 * @return インスタンスの文字列
	 */
	override def toString():String = {
		id + ":[" + sequence + ":" + makeDebugString(binary, 25) + "]"
	}

}

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Control
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
final case class Control(id:Long, code:Byte, args:Any*) extends Transferable {

	// ========================================================================
	// インスタンスの文字列化
	// ========================================================================
	/**
	 * このインスタンスを文字列化します。
	 * @return インスタンスの文字列
	 */
	override def toString():String = {
		id + ":{" + "%05d".format(code) + ":" + makeDebugString(args) + "}"
	}

}
