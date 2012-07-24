/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla

import java.io.{IOException, File}
import com.kazzla.domain.Configuration

// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Workspace
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Workspace(val dir:File) {

	// ========================================================================
	//
	// ========================================================================
	/**
	 *
	 */
	def getConfiguration():Configuration = {
		val file = getFile("conf/node.properties")
	}

	// ========================================================================
	// ファイルの参照
	// ========================================================================
	/**
	 * このワークスペースから指定されたパスのファイルを参照します。ワークスペース外のファ
	 * イルの参照を試みた場合は例外が発生します。
	 * @param path パス
	 * @return ワークスペース内のファイル
	 */
	def getFile(path:String):File = {
		val file = new File(dir, path)
		if(! file.getCanonicalPath.startsWith(dir.getCanonicalPath)){
			throw new IOException("illegal access: " + path + " is out of " + dir)
		}
		file
	}

}
