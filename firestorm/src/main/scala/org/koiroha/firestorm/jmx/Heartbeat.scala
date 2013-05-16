/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
 */

package org.koiroha.firestorm.jmx

import java.util.{TimerTask, Timer}

/**
 * give heartbeat to JMX monitor.
 */
object Heartbeat {

	/** monitor list */
	private[this] var monitors = List[Monitor]()

	/** add monitor to heartbeat list. */
	def +=(monitor:Monitor):Unit = synchronized { monitors ::= monitor }

	/** remove monitor from heartbeat list. */
	def -=(monitor:Monitor):Unit = synchronized { monitors = monitors.filter{ _ != monitor } }

	/** heartbeat scheduler timer. */
	private[this] val timer = new Timer("Firestorm JMX Monitor", true)

	timer.scheduleAtFixedRate(new TimerTask {
		def run() {
			monitors.foreach { _.touch() }
		}
	}, 1000, 1000)

}

/** trait that called per 1 second from scheduler */
trait Monitor {
	def touch():Unit
}
