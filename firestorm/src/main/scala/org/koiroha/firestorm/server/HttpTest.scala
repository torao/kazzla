/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
 */

package org.koiroha.firestorm.server

import java.util.concurrent.{LinkedBlockingQueue, TimeUnit, ThreadPoolExecutor, Executor}
import org.koiroha.firestorm.core.Context
import org.koiroha.firestorm.http.{ConcurrentWorker, HttpServer}

/**
 * Created with IntelliJ IDEA.
 * User: torao
 * Date: 2013/01/14
 * Time: 21:59
 * To change this template use File | Settings | File Templates.
 */
object HttpTest {

	class SampleWorker(e:Executor) extends ConcurrentWorker(e) {
		def syncRun():Unit = {
			response.sendResponseCode("HTTP/1.1", 200, "OK")
			response.header("Connection") = "close"
			response.header("Content-Type") = "text/plain"
			response.print { out =>
				out.println("hello, world")
			}
		}
	}

	def main(args:Array[String]):Unit = {
		val context = new Context("http")
		val executor = new ThreadPoolExecutor(20, 20, 10, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable]());
		val server = HttpServer(context){ new SampleWorker(executor) }.listen(8085)
	}

}
