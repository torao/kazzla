/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service.storage

import java.util.UUID
import com.kazzla.storage.Status
import com.kazzla.storage.fs.FileType

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Inode
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
case class Inode(id:UUID, path:String, accountId:UUID, parentId:UUID, ftype:Char, name:String, size:Long, permission:Byte, ctime:Long, utime:Long, atime:Long, lock:Option[Inode.Lock], lockShared:Int){
	def toStatus = Status(id, path, size, ctime, utime, ftype.toByte, accountId)

	def isFile = ftype == FileType.File
	def isDirectory = ftype == FileType.Directory
	def isSymbolicLink = ftype == FileType.SymbolicLink
}

object Inode {
	case class Lock(session:Int, timestamp:Long)
}
