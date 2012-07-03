/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain

import org.apache.log4j.Logger
import java.nio.ByteBuffer
import com.kazzla.KazzlaException

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Interactive Remote Procedure Call
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 双方向 RPC のためのパッケージです。
 * @author Takami Torao
 */
package object irpc {
	import com.kazzla.debug.makeDebugString
	val logger = Logger.getLogger(this.getClass)

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// CancelException
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	class CancelException(msg:String, ex:Throwable) extends Exception(msg, ex)

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// RemoteException
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	class RemoteException(msg:String) extends KazzlaException(msg)

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Transferable
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	sealed class Transferable private[irpc]() extends java.io.Serializable {
		private[this] var serialized:Option[Array[Byte]] = None
		def pack(coded:Codec):Transferable = {
			serialized match {
				case Some(binary) => codec.pack(this)
			}
			this
		}
		def transfer(protocol:Protocol):Unit = {
			protocol.transferUnit(ByteBuffer.wrap(serialized))
		}
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Call
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	case class Call(id:Long, timeout:Long, callback:Boolean, name:String, args:Any*) extends Transferable {

		// ======================================================================
		// インスタンスの文字列化
		// ======================================================================
		/**
		 * このインスタンスを文字列化します。
		 * @return インスタンスの文字列
		 */
		override def toString():String = {
			id + ":" + name + '(' + makeDebugString(args) + ')'
		}

	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Result
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	case class Result(id:Long, error:Option[String], result:Any*) extends Transferable {

		// ======================================================================
		// インスタンスの文字列化
		// ======================================================================
		/**
		 * このインスタンスを文字列化します。
		 * @return インスタンスの文字列
		 */
		override def toString():String = {
			id + ":" + (error match {
				case Some(msg) => msg
				case None => makeDebugString(result)
			})
		}

	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Block
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	case class Block(id:Long, sequence:Int, binary:Array[Byte]) extends Transferable {

		// ======================================================================
		// インスタンスの文字列化
		// ======================================================================
		/**
		 * このインスタンスを文字列化します。
		 * @return インスタンスの文字列
		 */
		override def toString():String = {
			id + ":[" + sequence + ":" + makeDebugString(binary, 25) + "]"
		}

	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Control
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	case class Control(id:Long, code:Byte, args:Any*) extends Transferable {

		// ======================================================================
		// インスタンスの文字列化
		// ======================================================================
		/**
		 * このインスタンスを文字列化します。
		 * @return インスタンスの文字列
		 */
		override def toString():String = {
			id + ":{" + "%05d".format(code) + ":" + makeDebugString(args) + "}"
		}

	}

}
