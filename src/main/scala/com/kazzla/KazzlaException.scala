/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla

import java.io.PrintWriter

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// KazzlaException
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * <p>
 * </p>
 * @author Takami Torao
 */
class KazzlaException(msg:String, ex:Throwable, par:Throwable*) extends Exception(msg, ex){
	// ※Exception のメッセージメッセージと下層例外は null を渡して良いという Java 仕様
	def this() = this(null, null)
	def this(msg:String) = this(msg, null)
	def this(ex:Throwable) = this(null, ex)
	override def printStackTrace(out:PrintWriter){
		par.foreach{ e => if(out != null) e.printStackTrace(out) }
		super.printStackTrace(out)
	}
}
