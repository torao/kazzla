/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
 */

package org.koiroha.firestorm.jmx

import javax.management._
import java.lang.management.ManagementFactory
import java.util.concurrent.atomic.AtomicLong
import java.nio.channels.SocketChannel
import org.koiroha.firestorm.core._
import org.koiroha.firestorm.core.Endpoint
import org.koiroha.firestorm.core.Server

trait ContextMXBean {
	def getServerCount():Int
	def getEndpointCount():Int
	def getSelectPerSecond():Double
	def getAcceptConnectionPerSecond():Double
	def getReadTransmitPerSecond():Double
	def getWriteTransmitPerSecond():Double

	def shutdown():Unit
}

class ContextMXBeanImpl(context:Context) extends ContextMXBean with Monitor {
	private[this] val server = ManagementFactory.getPlatformMBeanServer
	private[this] val name = uniqueName(context.id)
	private[this] val select = new History()
	private[this] val accept = new History()
	private[this] val read = new History()
	private[this] val write = new History()
	private[this] var serverCount = 0
	private[this] var endpointCount = 0
	private[this] val listener = new Context.Listener(){
		override def onListen(server:Server):Unit = {
			serverCount += 1
		}
		override def onUnlisten(server:Server):Unit = {
			serverCount -= 1
		}
		override def onAccept(channel:SocketChannel):Unit = {
			select.current.value += 1
			accept.current.value += 1
		}
		override def onRead(ep:Endpoint, length:Int){
			select.current.value += 1
			read.current.value += length
		}
		override def onWrite(ep:Endpoint, length:Int){
			select.current.value += 1
			write.current.value += length
		}
		override def onOpen(endpoint:Endpoint){
			endpointCount += 1
		}
		override def onClosed(endpoint:Endpoint){
			endpointCount -= 1
		}
		override def onShutdown(){
			Heartbeat -= ContextMXBeanImpl.this
			server.unregisterMBean(name)
			EventLog.debug("unregister context mxbean \"%s\" at %s".format(context.id, name))
		}
	}

	context += listener
	EventLog.debug("register context mxbean \"%s\" at %s".format(context.id, name))
	server.registerMBean(this, name)
	Heartbeat += this

	def touch():Unit = {
		select.touch()
		accept.touch()
		read.touch()
		write.touch()
	}

	def getSelectPerSecond():Double = select.average(3)
	def getAcceptConnectionPerSecond():Double = accept.average(3)
	def getReadTransmitPerSecond():Double = read.average(3)
	def getWriteTransmitPerSecond():Double = write.average(3)

	def getServerCount():Int = serverCount
	def getEndpointCount():Int = endpointCount

	def shutdown():Unit = {
		// jvm exiting at shutdown if there are no normal threads
		new Thread("Firestorm Shutdown"){
			{ setDaemon(false) }
			override def run(){
				EventLog.warn("\"%s\" server shutting-down by JMX request".format(context.id))
				context.shutdown(10 * 1000)
			}
		}.start()
	}

/*
	def getMBeanInfo():MBeanInfo = {
		val attrs = Array[MBeanAttributeInfo](
			new MBeanAttributeInfo("ServerCount", "int", "Active Servers", true, false, false),
			new MBeanAttributeInfo("EndpointCount", "int", "Active Endpoints", true, false, false),
			new MBeanAttributeInfo("SelectPerSecond", "double", "Select Average [calls/sec]", true, false, false),
			new MBeanAttributeInfo("AcceptPerSecond", "double", "Accept Connections Average [calls/sec]", true, false, false),
			new MBeanAttributeInfo("ReadTransmissionPerSecond", "double", "Read Transmission Average [bytes/sec]", true, false, false),
			new MBeanAttributeInfo("WriteTransmissionPerSecond", "double", "Write Transmission Average [bytes/sec]", true, false, false)
		)
		return new MBeanInfo()
	}
*/

	private[this] def uniqueName(id:String):ObjectName = {
		val f = "org.koiroha.firestorm:type=Context,name=%s"
		var n = new ObjectName(f.format(id))
		while(server.isRegistered(n)){
			n = new ObjectName(f.format(id + "@" + ContextMXBeanImpl.uniqueSequence.addAndGet(1)))
		}
		n
	}

}

object ContextMXBeanImpl {
	/** Unique sequence generator */
	private[jmx] val uniqueSequence = new AtomicLong(0)
}
