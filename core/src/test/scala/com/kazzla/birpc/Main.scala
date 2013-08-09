/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.birpc

import java.util.concurrent.{LinkedBlockingQueue, TimeUnit, ThreadPoolExecutor}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Main
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object Main {
	def main(args:Array[String]):Unit = {

		val exec = new ThreadPoolExecutor(10, 10, 10, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable]())
		val s1 = new Session("server", true, exec, new HogeImpl1)
		val s2 = new Session("client", false, exec, new HogeImpl2)

		val hoge = s2.getRemoteInterface(classOf[Hoge])
		System.out.println(hoge.hoge("fffff"))
		exec.shutdown()
	}

	trait Hoge {
		@Export(10)
		def hoge(text:String):String
	}

	class HogeImpl1 extends Hoge {
		def hoge(text:String) = {
			Session().get.getRemoteInterface(classOf[Hoge]).hoge(text) + ":hoge"
		}
	}

	class HogeImpl2 extends Hoge {
		def hoge(text:String) = "hoge[" + text + "]"
	}
}
