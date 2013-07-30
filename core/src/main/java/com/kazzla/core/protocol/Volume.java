/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.protocol;

import com.kazzla.core.io.irpc.RemoteProcedure;

import java.util.UUID;

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// VolumeService
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
public interface Volume extends Service {

	// ==============================================================================================
	// ブロック ID 一覧の参照
	// ==============================================================================================
	/**
	 * ブロック ID を改行付きでパイプストリームへ出力します。
	 */
	@RemoteProcedure(100)
	public void listBlocks();

	// ==============================================================================================
	// ブロックの割り当て
	// ==============================================================================================
	/**
	 * 指定された長さのブロックを割り当てます。
	 */
	@RemoteProcedure(200)
	public void allocateBlock(UUID uuid, long length);

	// ==============================================================================================
	// ブロックの削除
	// ==============================================================================================
	/**
	 * 指定されたブロックを削除します。
	 */
	@RemoteProcedure(300)
	public void deleteBlock(UUID uuid);

	// ==============================================================================================
	// ブロックの読み込み
	// ==============================================================================================
	/**
	 * 指定されたブロックの領域を読み込んでパイプストリームに出力します。
	 */
	@RemoteProcedure(400)
	public void readBlock(UUID uuid, long offset, int length);

	// ==============================================================================================
	// ブロックの書き込み
	// ==============================================================================================
	/**
	 * 指定されたブロックの領域にパイプストリームから読み出した内容を書き込みます。
	 */
	@RemoteProcedure(500)
	public void writeBlock(UUID uuid, long offset);

	// ==============================================================================================
	// メッセージダイジェストの参照
	// ==============================================================================================
	/**
	 * 指定されたブロックのメッセージダイジェストを参照します。
	 */
	@RemoteProcedure(600)
	public byte[] checksum(UUID uuid, byte[] challenge);

	// ==============================================================================================
	// メッセージダイジェストアルゴリズム
	// ==============================================================================================
	/**
	 * ブロックのメッセージダイジェストを算出するアルゴリズムです。
	 */
	public static final String CHECKSUM_ALGORITHM = "SHA1";

}
