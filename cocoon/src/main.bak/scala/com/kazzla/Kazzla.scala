package com.kazzla

import scala.annotation.tailrec
import java.io.File
import org.koiroha.firestorm.core.Context
import com.kazzla.Context
import java.util.concurrent.{Executor, LinkedBlockingQueue, TimeUnit, ThreadPoolExecutor}
import org.koiroha.firestorm.http.{ConcurrentWorker, HttpServer}

object Kazzla {

	private[this] class Launcher {
		var server:String = ""
		var port:Option[Int] = None
		var bind:Option[String] = None
		var dir:File = new File(".")

		def apply():Unit = {
			val context = Context.create(dir)
			server match {
				case "" =>
					val context = new Context("kazzla")
					val executor = new ThreadPoolExecutor(20, 20, 10,
						TimeUnit.SECONDS, new LinkedBlockingQueue[Runnable]());
					val server = HttpServer(context){ new SampleWorker(executor) }.listen(port.getOrElse(8898))
			}
		}
	}
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
		@tailrec
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
