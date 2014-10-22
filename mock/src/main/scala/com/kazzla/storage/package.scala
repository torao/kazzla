/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla

import java.net.URI
import java.util.UUID

package object storage {

	/**
	 * ファイルストアのステータス。
	 */
	case class FileStoreStatus(availableSize:Long, totalSize:Long)

	/**
	 * ファイルのステータス。
	 * @param fileId ファイルID
	 * @param path ファイルの正式な表記のパス
	 * @param length ファイルの長さ
	 * @param createdAt ファイルの作成日時
	 * @param updatedAt ファイルの更新日時
	 * @param fstype ファイルタイプ
	 * @param fsaccess アクセスパーミッション
	 * @param owner ファイルの所有者
	 */
	case class FileStatus(
		fileId:UUID, path:String, length:Long, createdAt:Long, updatedAt:Long,
		fstype:FileType, fsaccess:Set[FilePermission], owner:UUID
	){
		lazy val uri = new URI(path)
		lazy val isReadable = fsaccess.contains(FilePermission.Read)
		lazy val isWritable = fsaccess.contains(FilePermission.Write)
		lazy val isExecutable = fsaccess.contains(FilePermission.Execute)
		lazy val isHidden = fsaccess.contains(FilePermission.Execute)
	}

	/**
	 * ファイルタイプ。
	 */
	abstract sealed class FileType(val id:Byte)
	object FileType {
		/** ファイルであることを示します。 */
		case object File extends FileType('f'.toByte)
		/** ディレクトリを示します。 */
		case object Directory extends FileType('d'.toByte)
		/** シンボリックリンクを示します。 */
		case object SymLink extends FileType('l'.toByte)
	}

	/**
	 * ファイルパーミッション。これらのビット論理和が使用される。
	 */
	abstract sealed class FilePermission(val id:Byte)
	object FilePermission {
		case object Hidden extends FilePermission((1 << 4).toByte)
		case object Read extends FilePermission((1 << 2).toByte)
		case object Write extends FilePermission((1 << 1).toByte)
		case object Execute extends FilePermission((1 << 0).toByte)
		def apply(perm:Byte):Set[FilePermission] = {
			Seq(Hidden, Read, Write, Execute).filter{ p => (perm & p.id) == p.id }.toSet
		}
	}

	/**
	 * ファイル移動オプション。
	 */
	abstract sealed class MoveOption(val id:Byte)
	object MoveOption {
		/** ファイルがすでに存在する場合、上書きすることを示します。 */
		case object ReplaceExisting extends MoveOption(1.toByte)
	}

	/**
	 * ファイルコピーオプション。
	 */
	abstract sealed class CopyOption(val id:Byte)
	object CopyOption {
		/** ファイルがすでに存在する場合、上書きすることを示します。 */
		case object ReplaceExisting extends CopyOption((1 << 0).toByte)
		/** ファイルの属性もコピーすることを示します。 */
		case object CopyAttributes extends CopyOption((1 << 1).toByte)
	}

	/**
	 * ファイル領域の割り当てオプション。
	 */
	abstract sealed class AllocateOption(val id:Byte)
	object AllocateOption {
		/** ファイルが存在しない場合は新規に割り当てる。すでに存在する場合は失敗する。アトミック操作。 */
		case object Create extends AllocateOption((1 << 0).toByte)
		/** ファイルが存在する場合に上書きする。ファイルが存在しない場合は失敗する。アトミック操作。 */
		case object OverWrite extends AllocateOption((1 << 1).toByte)
		/** 既存のファイルの続きとして割り当てを行う。指定されていなかった場合は 0 バイト目からの割り当てとなる。 */
		case object Append extends AllocateOption((1 << 2).toByte)
	}

}
