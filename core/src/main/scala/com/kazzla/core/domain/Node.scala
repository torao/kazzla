/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.domain

import java.net._
import com.kazzla.core.protocol.{Region, Volume}
import java.lang.reflect.{Method, InvocationHandler, Proxy}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Node
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Node[T,U](urls: =>Seq[URL]) {
	def region(volume:Volume):Region = Proxy.newProxyInstance(
		Thread.currentThread().getContextClassLoader,
		Array(classOf[Region]), new InvocationHandler {
			def invoke(proxy:Any, method:Method, args:Array[AnyRef]):AnyRef = {

			}
		}
	)
}
