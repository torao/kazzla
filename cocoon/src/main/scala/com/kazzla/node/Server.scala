/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.kazzla.node

import com.kazzla.util._
import scala._

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Server
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 *
 * @author Takami Torao
 */
class Server(name:String, config:Config){

	private[this] var queue:List[()=>Unit] = List()

	def listen(port:Int):Server = {
		queue ::= (() => {

		})
		this
	}

	def exec():Unit = {
		
	}
}

object Server {
	val logger = org.apache.log4j.Logger.getLogger(classOf[Server])

}