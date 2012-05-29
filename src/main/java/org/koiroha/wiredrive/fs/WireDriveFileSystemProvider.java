/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package org.koiroha.wiredrive.fs;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.Map;
import java.util.Set;

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// WireDriveFileSystemProvider
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public class WireDriveFileSystemProvider extends FileSystemProvider{

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public String getScheme() {
		return "wdfs";
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public FileSystem getFileSystem(URI uri) {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public Path getPath(URI uri) {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public void delete(Path path) throws IOException {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public void copy(Path source, Path target, CopyOption... options) throws IOException {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public void move(Path source, Path target, CopyOption... options) throws IOException {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public boolean isSameFile(Path path, Path path2) throws IOException {
		return false;  //To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public boolean isHidden(Path path) throws IOException {
		return false;  //To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public FileStore getFileStore(Path path) throws IOException {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public void checkAccess(Path path, AccessMode... modes) throws IOException {
		//To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
		return null;  //To change body of implemented methods use File | Settings | File Templates.
	}

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	@Override
	public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
		//To change body of implemented methods use File | Settings | File Templates.
	}
}
