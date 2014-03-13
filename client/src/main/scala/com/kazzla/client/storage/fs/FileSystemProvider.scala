/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.client.storage.fs

import java.net.URI
import java.util
import java.nio.file._
import java.nio.file
import java.nio.file.attribute.{BasicFileAttributes, FileAttributeView, FileAttribute}
import java.nio.channels.SeekableByteChannel
import java.nio.file.DirectoryStream.Filter

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// FileSystemProvider
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class FileSystemProvider extends java.nio.file.spi.FileSystemProvider {
	override def setAttribute(path: Path, attribute: String, value: scala.Any, options: LinkOption*): Unit = ???

	override def readAttributes(path: Path, attributes: String, options: LinkOption*): util.Map[String, AnyRef] = ???

	override def readAttributes[A <: BasicFileAttributes](path: Path, `type`: Class[A], options: LinkOption*): A = ???

	override def getFileAttributeView[V <: FileAttributeView](path: Path, `type`: Class[V], options: LinkOption*): V = ???

	override def checkAccess(path: Path, modes: AccessMode*): Unit = ???

	override def getFileStore(path: Path): FileStore = ???

	override def isHidden(path: Path): Boolean = ???

	override def isSameFile(path: Path, path2: Path): Boolean = ???

	override def move(source: Path, target: Path, options: CopyOption*): Unit = ???

	override def copy(source: Path, target: Path, options: CopyOption*): Unit = ???

	override def delete(path: Path): Unit = ???

	override def createDirectory(dir: Path, attrs: FileAttribute[_]*): Unit = ???

	override def newDirectoryStream(dir: Path, filter: Filter[_ >: Path]): DirectoryStream[Path] = ???

	override def newByteChannel(path: Path, options: util.Set[_ <: OpenOption], attrs: FileAttribute[_]*): SeekableByteChannel = ???

	override def getPath(uri: URI): Path = ???

	override def getFileSystem(uri: URI): file.FileSystem = ???

	override def newFileSystem(uri: URI, env: util.Map[String, _]): FileSystem = ???

	override def getScheme: String = ???
}
