/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node

import scala._

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Context
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Context(name:String) {
	import Context._

	private[this] var dispatcher:Option[Dispatcher] = None

	def start():Unit = dispatcher match {
		case Some(t) =>
			logger.debug("context already running")
		case None =>
			dispatcher = Some(new Dispatcher(name))
			dispatcher.get.start()
	}

	def stop():Unit = dispatcher match {
		case Some(t) =>
			t.shutdown()
			dispatcher = None
		case None =>
			logger.debug("context not running")
	}

	def running:Boolean = dispatcher.isDefined

}

object Context {
	private[Context] val logger = org.apache.log4j.Logger.getLogger(classOf[Context])

}