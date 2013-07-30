/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node.volume

import java.nio.file._
import java.net._
import java.util.concurrent._
import com.kazzla.core.io.irpc.Context

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Server
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object Server {
	def main(args:Array[String]):Unit = {

		val dir = if(args.length == 0){
			Paths.get(".")
		} else {
			Paths.get(URI.create(args(0)))
		}

		val threads = new ThreadPoolExecutor(5, 5, 10, TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable]())
		val context = new Context(8 * 1024, threads)
		val server = com.kazzla.core.io.irpc.Server.newBuilder("VolumeNode")
			.service(new VolumeService(dir))
			.context(context)
			.bind(7777)
			.create()
	}

}
