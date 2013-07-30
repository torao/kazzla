/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node.volume

import java.nio.file._
import com.kazzla.core.io.irpc._
import com.kazzla.core.io.IO
import java.io._
import java.util._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// VolumeService
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class VolumeService(dir:Path) extends com.kazzla.core.protocol.Volume {
	private[this] val volume = new Volume(dir)

	def listBlocks():Unit = Option(Pipe.currentPipe()).foreach{ pipe =>
		val out = new BufferedWriter(new OutputStreamWriter(pipe.getOutputStream, IO.UTF8))
		volume.lookup().foreach { blockId =>
			out.write(blockId.toString)
			out.write("\n")
		}
	}

	def allocateBlock(uuid:UUID, length:Long):Unit = volume.create(uuid, length)

	def deleteBlock(blockId:UUID):Unit = volume.delete(blockId)

	def readBlock(blockId:UUID, offset:Long, length:Int):Unit = Option(Pipe.currentPipe()).foreach{ pipe =>
		val out = pipe.getOutputStream
		val buffer = new Array[Byte](1024)
		var send = 0
		while(send < length){
			val len = math.min(buffer.length, length - send)
			volume.read(blockId, offset + send, buffer, 0, len)
			out.write(buffer, 0, len)
			send += len
		}
	}

	def writeBlock(blockId:UUID, offset:Long):Unit = Option(Pipe.currentPipe()).foreach{ pipe =>
		val in = pipe.getInputStream
		val buffer = new Array[Byte](1024)
		var receive = 0
		while(receive < 0){
			val len = in.read(buffer)
			if(len < 0){
				return
			}
			volume.update(blockId, offset + receive, buffer, 0, len)
		}
	}

	def checksum(blockId:UUID, challenge:Array[Byte]):Array[Byte] = volume.checksum(blockId, challenge)

}
