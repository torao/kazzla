/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.kazzla

import java.io._
import scala._

object Kazzla {

	private[this] class Launcher {
		var server:String = ""
		var port:Option[Int] = None
		var bind:Option[String] = None
		var dir:File = new File(".")

		def apply():Unit = {
		}
	}

	def main(args:Array[String]):Unit = {
		val systemProperties = System.getProperties()
		parse(new Launcher(), List())()
	}

}
