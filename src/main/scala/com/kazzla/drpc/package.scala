/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla

import org.apache.log4j.Logger

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Duplex Remote Procedure Call
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * 双方向 RPC のためのパッケージです。
 * @author Takami Torao
 */
package object drpc {
	val logger = Logger.getLogger(this.getClass)

	class Transferable private[drpc]()

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Call
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	case class Call(id:Long, timeout:Long, name:String, args:Any*) extends Transferable {

		// ======================================================================
		// インスタンスの文字列化
		// ======================================================================
		/**
		 * このインスタンスを文字列化します。
		 * @return インスタンスの文字列
		 */
		override def toString():String = {
			id + ":" + name + '(' + com.kazzla.debug.makeDebugString(args) + ')'
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
				case None => com.kazzla.debug.makeDebugString(result)
			})
		}

	}
}
