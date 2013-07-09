/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.volume;

import com.kazzla.IO;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Volume
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * ブロックに対する CRUD を提供します。
 * 排他はサーバ側で制御しているためこのクラスでは実装しません。
 * @author Takami Torao
 */
public class Volume {

	// =========================================================================
	// ディレクトリ
	// =========================================================================
	/**
	 * このインスタンスがボリュームとして使用するディレクトリ。
	 */
	public final File dir;

	// =========================================================================
	// コンストラクタ
	// =========================================================================
	/**
	 */
	public Volume(File dir){
		this.dir = dir;
		dir.mkdirs();
		return;
	}

	// =========================================================================
	// 実体ファイルの参照
	// =========================================================================
	/**
	 * 指定された UUID のブロックに対するローカルファイルを参照します。
	 * @return ブロックファイル
	 */
	public Iterable<UUID> lookup() throws IOException {
		String[] fileNames = dir.list();
		if(fileNames == null){
			throw new IOException();
		}
		List<UUID> list = new ArrayList<UUID>();
		for(String fileName: fileNames){
			try {
				list.add(UUID.fromString(fileName));
			} catch(IllegalArgumentException ex){
			}
		}
		return list;
	}

	// =========================================================================
	// 実体ファイルの参照
	// =========================================================================
	/**
	 * 指定された UUID のブロックに対するローカルファイルを参照します。
	 * @param blockId ブロック ID
	 * @return ブロックファイル
	 */
	public File getLocalFile(UUID blockId){
		String fileName = blockId.toString();
		return new File(dir, fileName);
	}

	// =========================================================================
	// ブロックの作成
	// =========================================================================
	/**
	 * 指定されたブロックを作成します。
	 * @param blockId ブロック ID
	 * @param size ブロックサイズ
	 */
	public void create(UUID blockId, long size) throws IOException {
		File block = getLocalFile(blockId);
		RandomAccessFile file = new RandomAccessFile(block, "rw");
		try {
			file.setLength(size);
		} finally {
			IO.close(file);
		}
		return;
	}

	// =========================================================================
	// ブロックの読み込み
	// =========================================================================
	/**
	 * 指定されたブロックの領域を読み込みます。
	 * @param blockId ブロック ID
	 */
	public void read(UUID blockId, long pos, byte[] buffer, int offset, int length) throws IOException {
		File block = getLocalFile(blockId);
		RandomAccessFile file = new RandomAccessFile(block, "r");
		try {
			file.seek(pos);
			file.readFully(buffer, offset, length);
		} finally {
			IO.close(file);
		}
		return;
	}

	// =========================================================================
	// ブロックの書き込み
	// =========================================================================
	/**
	 * 指定されたブロックの領域を書き込みます。
	 * @param blockId ブロック ID
	 */
	public void update(UUID blockId, long pos, byte[] buffer, int offset, int length) throws IOException {
		File block = getLocalFile(blockId);
		RandomAccessFile file = new RandomAccessFile(block, "rw");
		try {
			file.seek(pos);
			file.write(buffer, offset, length);
		} finally {
			IO.close(file);
		}
		return;
	}

	// =========================================================================
	// ブロックの削除
	// =========================================================================
	/**
	 * 指定されたブロックを削除します。
	 * @param blockId ブロック ID
	 */
	public void delete(UUID blockId) throws IOException {
		File block = getLocalFile(blockId);
		if(! block.delete()){
			throw new IOException(block.toString());
		}
		return;
	}

}
