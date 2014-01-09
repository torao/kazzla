/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node.storage

import com.kazzla.core.io._
import com.kazzla.core.tools.ShellTools
import java.io._
import java.net.{URLEncoder, HttpURLConnection, URI, URL}
import sun.security.tools.KeyTool

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Shell
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object Shell extends ShellTools{

	// ============================================================================================
	// データディレクトリの初期化
	// ============================================================================================
	/**
	 * ブロックを管理するディレクトリを初期化します。
	 */
	def main(args:Array[String]):Unit = {
		activate(new File(prompt("directory", "node.storage/data"){ str =>
			val dir = new File(str)
			if(! dir.isDirectory){
				throw new IOException(s"ERROR: it's not exist or not directory")
			}
		}), prompt("URL", "http://localhost:8088/api/storage/"){ str =>
			new URL(str)
		}, prompt("Token", "-"){ _ => None })
	}

	// ============================================================================================
	// データディレクトリの初期化
	// ============================================================================================
	/**
	 * ブロックを管理するディレクトリを初期化します。
	 */
	def activate(dir:File, auth:String, token:String):Unit = {
		val uri = URI.create(auth)
		val ks = new File(dir, "node.jks")
		val csr = new File(dir, "node.csr")
		val certs = new File(dir, "node.crt")
		createKeyStore(URI.create(auth), token, ks)
		createCSR(ks, csr)
		createCerts(uri, csr, certs, token)
		importCerts(ks, certs)
	}

	// ============================================================================================
	// キーストアの作成
	// ============================================================================================
	/**
	 * キーストアを作成します。
	 */
	private[this] def createKeyStore(uri:URI, token:String, file:File):Unit = {
		val url = uri.resolve(s"dname/${URLEncoder.encode(token,"UTF-8")}").toURL
		val dname = new String(url.openStream().readFully(), "UTF-8")
		file.delete()
		KeyTool.main(Array[String](
			"-genkeypair",
			"-dname", dname,
			"-alias", "node",
			"-keypass", "000000",
			"-keystore", file.getAbsolutePath,
			"-storepass", "000000",
			"-validity", "180"))
	}

	// ============================================================================================
	// CSR の作成
	// ============================================================================================
	/**
	 * CSR を作成します。
	 */
	private[this] def createCSR(ks:File, file:File):Unit = {
		KeyTool.main(Array[String](
			"-certreq",
			"-alias", "node",
			// "-sigalg", "sigalg",
			"-file", file.getAbsolutePath,
			"-keypass", "000000",
			// "-storetype", "storetype",
			"-keystore", ks.getAbsolutePath,
			"-storepass", "000000"))
	}

	// ============================================================================================
	// 証明書の取得
	// ============================================================================================
	/**
	 * 指定された公開鍵に対する証明書を取得します。
	 */
	private[this] def createCerts(uri:URI, csr:File, certs:File, token:String):Unit = {
		val url = uri.resolve(s"activate/${URLEncoder.encode(token,"UTF-8")}").toURL
		val con = url.openConnection().asInstanceOf[HttpURLConnection]
		con.setDoOutput(true)
		con.setDoInput(true)
		con.setUseCaches(false)
		con.setConnectTimeout(10 * 1000)
		con.setFixedLengthStreamingMode(csr.length())
		con.setRequestProperty("Content-Type", "application/octet-stream")
		val buffer = new Array[Byte](1024)
		using(new FileInputStream(csr)){ fin =>
			copy(fin, con.getOutputStream, buffer)
		}
		using(new FileOutputStream(certs)){ fout =>
			copy(con.getInputStream, fout, buffer)
		}
		con.disconnect()
	}

	// ============================================================================================
	// 証明書のインポート
	// ============================================================================================
	/**
	 * 証明書をインポートします。
	 */
	private[this] def importCerts(ks:File, certs:File):Unit = {
		KeyTool.main(Array[String](
			"-importcert",
			"-alias", "node",
			"-file", certs.getAbsolutePath,
			"-keypass", "000000",
			"-noprompt",
			// "-trustcacerts",
			// "-storetype", "storetype",
			"-keystore", ks.getAbsolutePath,
			"-storepass", "000000"))
	}

}
