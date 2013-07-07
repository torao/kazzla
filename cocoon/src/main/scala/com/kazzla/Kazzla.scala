/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
 */
package com.kazzla

import scala.annotation._
import java.io._
import scala._

object Kazzla {

	private[this] class Launcher {
		var server:String = ""
		var port:Option[Int] = None
		var bind:Option[String] = None
		var dir:File = new File(".")

		def apply():Unit = {
			val
		}
	}

	def main(args:Array[String]):Unit = {
		lazy val parse:(Launcher,List[String])=>Launcher = (launcher, args) => args match {
			case "--port" :: port :: rest =>
				launcher.port = Some(port.toInt)
				parse(launcher, rest)
			case "--basedir" :: dir :: rest =>
				launcher.dir = new File(dir)
				parse(launcher, rest)
			case server :: rest =>
				launcher.server = server
				parse(launcher, rest)
			case Nil => launcher
		}
		parse(new Launcher(), List())()
	}

}
