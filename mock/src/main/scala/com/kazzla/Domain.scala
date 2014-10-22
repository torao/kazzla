/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla

import java.util.UUID

import com.kazzla.storage.FileStore
import com.kazzla.storage.nio.KazzlaFileSystem

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Domain
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
trait Domain {

	def fileStore:FileStore

	def getFileSystem:KazzlaFileSystem = new KazzlaFileSystem(open())

	def open():Session

	def close(sessionId:UUID):Unit
}
