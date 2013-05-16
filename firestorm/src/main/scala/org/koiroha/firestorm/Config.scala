/*
 * Copyright (c) 2012 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * org.koiroha.firestorm.http://www.apache.org/licenses/LICENSE-2.0.html
 */

package org.koiroha.firestorm

import annotation.tailrec
import java.io.PrintWriter

/**
 * Firestorm server configuration.
 */
class Config {

	/**
	 * Streaming server port.
	 */
	var streamingPort = 8085

	/**
	 * Parse specified commandline parameters.
	 * @param params commandline parameters
	 * @return
	 */
	@tailrec
	final def parseCommandlineParameters(params:List[String]):Unit = params match {

		case "-p" :: port :: rest =>
			try {
				this.streamingPort = port.toInt
				if (this.streamingPort < 0 || this.streamingPort > 0xFFFF){
					throw new NumberFormatException("port must be from 0 to %d".format(0xFFFF))
				}
			} catch {
				case ex:NumberFormatException =>
					System.err.printf("ERROR: invalid port number: %s (%s)%n", port, ex)
					System.exit(1)
			}
			parseCommandlineParameters(rest)

		case unknown :: rest =>
			System.err.printf("ERROR: unsupported commandline option: %s%n", unknown)
			System.exit(1)
			None

		case List() => None
	}

}

object Config {

	def help(out:PrintWriter):Unit = {
		out.println(
			"""java -jar firestorm.jar -p [port]
			  |
			""")
	}
}