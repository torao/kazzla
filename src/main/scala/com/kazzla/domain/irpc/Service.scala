/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.irpc


// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Service
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
abstract class Service protected(val name:String, peer:Peer) {

}

object Service {

	// ========================================================================
	// サービスの検索
	// ========================================================================
	/**
	 * 指定されたサービスを検索します。
	 * @return サービスのインスタンス
	 */
	def lookup[T <: Service](serviceName:String, spec:Class[T], option:String*):Option[T] = {
		// TODO
		null
	}

}