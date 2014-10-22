/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.storage.nio

import java.net.URI
import java.nio.channels.SeekableByteChannel
import java.nio.file.DirectoryStream.Filter
import java.nio.file._
import java.nio.file.attribute.{BasicFileAttributes, FileAttribute, FileAttributeView}
import java.nio.file.spi.{FileSystemProvider => JFileSystemProvider}
import java.util

import com.kazzla.Domain
import com.kazzla.storage.FileStore

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// KazzlaFSProvider
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class FileSystemProvider(val domain:Domain) extends JFileSystemProvider {

	override def getScheme:String = "kazzlafs"

	override def move(source:Path, target:Path, options:CopyOption*):Unit = {

	}

	override def checkAccess(path:Path, modes:AccessMode*):Unit = ???

	override def createDirectory(dir:Path, attrs:FileAttribute[_]*):Unit = ???

	override def getFileSystem(uri:URI):FileSystem = ???

	override def newByteChannel(path:Path, options:util.Set[_ <: OpenOption], attrs:FileAttribute[_]*):SeekableByteChannel = ???

	override def isHidden(path:Path):Boolean = ???

	override def copy(source:Path, target:Path, options:CopyOption*):Unit = ???

	override def delete(path:Path):Unit = ???

	override def newDirectoryStream(dir:Path, filter:Filter[_ >: Path]):DirectoryStream[Path] = ???

	override def setAttribute(path:Path, attribute:String, value:scala.Any, options:LinkOption*):Unit = ???

	override def getPath(uri:URI):Path = ???

	override def newFileSystem(uri:URI, env:util.Map[String, _]):FileSystem = ???

	override def readAttributes[A <: BasicFileAttributes](path:Path, `type`:Class[A], options:LinkOption*):A = ???

	override def readAttributes(path:Path, attributes:String, options:LinkOption*):util.Map[String, AnyRef] = ???

	override def isSameFile(path:Path, path2:Path):Boolean = ???

	override def getFileAttributeView[V <: FileAttributeView](path:Path, `type`:Class[V], options:LinkOption*):V = ???

	override def getFileStore(path:Path):FileStore = ???
}
