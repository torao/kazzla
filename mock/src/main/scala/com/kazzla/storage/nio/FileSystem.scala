/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.storage.nio

import java.lang.Iterable
import java.net.URI
import java.nio.file._
import java.nio.file.attribute.{FileAttributeView, FileStoreAttributeView, UserPrincipalLookupService}
import java.nio.file.spi.{FileSystemProvider => JFileSystemProvider}
import java.util.{Arrays, Set}

import com.kazzla.Session
import com.kazzla.storage.FileStore

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// KazzlaFS
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class KazzlaFileSystem(val session:Session) extends FileSystem {

	override def getSeparator:String = KazzlaFileSystem.Separator

	override def getRootDirectories:Iterable[Path] = Arrays.asList(KazzlaFileSystem.Root)

	override def provider():JFileSystemProvider = ???

	override def supportedFileAttributeViews():Set[String] = ???

	override def newWatchService():WatchService = ???

	override def getFileStores:Iterable[FileStore] = Arrays.asList(KazzlaST)

	override def isReadOnly:Boolean = ???

	override def getPath(first:String, more:String*):Path = {
		KazzlaFileSystem.Root.resolve((first :: more.toList).mkString(KazzlaFileSystem.Separator))
	}

	override def isOpen:Boolean = session.isOpen

	override def close():Unit = session.close()

	override def getPathMatcher(syntaxAndPattern:String):PathMatcher = ???

	override def getUserPrincipalLookupService:UserPrincipalLookupService = ???

	private[this] object KazzlaST extends FileStore {
		override def name():String = ???

		override def supportsFileAttributeView(`type`:Class[_ <: FileAttributeView]):Boolean = ???

		override def supportsFileAttributeView(name:String):Boolean = ???

		override def getUnallocatedSpace:Long = ???

		override def `type`():String = "kazzlafs"

		override def getAttribute(attribute:String):AnyRef = ???

		override def isReadOnly:Boolean = ???

		override def getFileStoreAttributeView[V <: FileStoreAttributeView](`type`:Class[V]):V = ???

		override def getUsableSpace:Long = ???

		override def getTotalSpace:Long = ???
	}
}

object KazzlaFileSystem {
	val Separator = "/"
	val Root:Path = Paths.get(URI.create("/"))

}