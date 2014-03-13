/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.client.storage.fs

import java.nio.file.spi.FileSystemProvider
import java.lang.Iterable
import java.nio.file.{WatchService, PathMatcher, FileStore, Path}
import java.util
import java.nio.file.attribute.UserPrincipalLookupService

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// FileSystem
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class FileSystem extends java.nio.file.FileSystem {
	override def provider(): FileSystemProvider = ???

	override def newWatchService(): WatchService = ???

	override def getUserPrincipalLookupService: UserPrincipalLookupService = ???

	override def getPathMatcher(syntaxAndPattern: String): PathMatcher = ???

	override def getPath(first: String, more: String*): Path = ???

	override def supportedFileAttributeViews(): util.Set[String] = ???

	override def getFileStores: Iterable[FileStore] = ???

	override def getRootDirectories: Iterable[Path] = ???

	override def getSeparator: String = ???

	override def isReadOnly: Boolean = ???

	override def isOpen: Boolean = ???

	override def close(): Unit = ???
}
