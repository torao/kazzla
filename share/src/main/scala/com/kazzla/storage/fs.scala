/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.storage

package object fs {

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// WriteOption
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	object WriteOption {
		val CreateNew = 1 << 0
		val Overwrite = 1 << 1
		val Default = CreateNew | Overwrite
		val Append = 1 << 2
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Permission
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	object Permission {
		val Read:Byte = (1 << 2).toByte
		val Write:Byte = (1 << 1).toByte
		val Execute:Byte = (1 << 0).toByte
	}

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// Permission
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 * @author Takami Torao
	 */
	object FileType {
		val File:Char = 'f'
		val Directory:Char = 'd'
		val SymbolicLink:Char = 's'
	}
}
