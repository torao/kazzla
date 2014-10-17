/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.shell

import javax.management.{ObjectName, MXBean}
import java.lang.management.ManagementFactory
import java.util.concurrent.ArrayBlockingQueue

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// ShellExecutable
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
abstract class ShellExecutable(name:String, _args:Array[String]) {
	import ShellExecutable._

	private[this] val queue = new ArrayBlockingQueue[Command](16)

	// ==============================================================================================
	// ==============================================================================================
	def apply():Unit = {
		val jmx = newController()
		val oName = ObjectName.getInstance("com.kazzla.node", "type", name)
		ManagementFactory.getPlatformMBeanServer.registerMBean(jmx, oName)
		var closed = false
		while(! closed) {
			apply(_args)
			queue.take() match {
				case Shutdown => closed = true
				case Restart  => closed = false
			}
			destroy()
		}
		ManagementFactory.getPlatformMBeanServer.unregisterMBean(oName)
	}

	// ==============================================================================================
	// ==============================================================================================
	protected def apply(args:Array[String]):Unit

	// ==============================================================================================
	// ==============================================================================================
	protected def destroy():Unit

	// ==============================================================================================
	// ==============================================================================================
	def shutdown():Unit = queue.add(Shutdown)

	// ==============================================================================================
	// ==============================================================================================
	def restart():Unit = queue.add(Restart)

	// ==============================================================================================
	// ==============================================================================================
	def status():String = {
		""
	}

	// ==============================================================================================
	// ==============================================================================================
	protected def newController():AnyRef = new DefaultJMXController()

	class DefaultJMXController extends JMXController {
		def shutdown():Unit = ShellExecutable.this.shutdown()
		def restart():Unit = ShellExecutable.this.restart()
		def status():String = ShellExecutable.this.status()
	}
}

object ShellExecutable {
	sealed abstract class Command
	case object Shutdown extends Command
	case object Restart extends Command

	@MXBean
	trait JMXController {
		def shutdown():Unit
		def restart():Unit
		def status():String
	}
}
