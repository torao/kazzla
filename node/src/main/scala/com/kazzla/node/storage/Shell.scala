/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.node.storage

import com.kazzla.core.kazzla
import com.kazzla.core.io._
import com.kazzla.core.tools.ShellTools
import java.io._
import java.net.{URLEncoder, HttpURLConnection, URI, URL}
import java.security.KeyStore
import java.security.cert.{X509Certificate, CertificateFactory}
import scala.collection.JavaConversions._

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Shell
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * TODO KeyTool を使用している部分を KeyStore に置き換え
 * @author Takami Torao
 */
object Shell extends ShellTools{

	private[this] val out = new PrintWriter(System.out)

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
		}), prompt("API Root URL", "http://localhost:8088/api/"){ str =>
			new URL(str)
		}, prompt("User ID", System.getProperty("user.name","")){ _ => None }, password("Password"){ _ => None })
	}

	// ============================================================================================
	// データディレクトリの初期化
	// ============================================================================================
	/**
	 * ブロックを管理するディレクトリを初期化します。
	 */
	def activate(dir:File, url:String, userid:String, password:Array[Char]):Unit = {
		val uri = URI.create(url)
		val ks = new File(dir, "node.jks")
		val csr = new File(dir, "node.csr")
		val certs = new File(dir, "node.crt")
		val ca = new File(dir, "cacert.crt")
		val nodeid = createKeyStore(URI.create(url), userid, password, ks, ca)
		createCSR(ks, csr)
		createCerts(uri, csr, certs, userid, password, nodeid)
		importCerts(ks, certs)
		jksToPkcs12(ks, new File(dir, "node.p12"))
	}

	// ============================================================================================
	// キーストアの作成
	// ============================================================================================
	/**
	 * サーバに指定されたアカウントの新しいノード証明書に使用する DName を問い合わせ鍵を作成しキーストアへ保存し
	 * ます。
	 */
	private[this] def createKeyStore(uri:URI, userid:String, password:Array[Char], jks:File, ca:File):String = {

		// 新しいノード証明書の DName を参照
		val url = uri.resolve(s"certs/newdn").toURL
		val dname = new String(url.openStream(userid, password).readFully(), "UTF-8")
		out.println(s"DName: $dname")

		// Common Name を参照
		val dn = """(?i)cn\s*=\s*([^,]*).*""".r
		val nodeid = dname match {
			case dn(cn) => cn
			case _ => throw new IOException(s"CN was not contains in DName: $dname")
		}
		out.println(s"Node ID: $nodeid")

		// 新しい鍵ペアを含むキーストアを作成
		jks.delete()
		keytool(
			"-genkeypair",
			"-dname", dname,
			"-alias", "node",
			"-keyalg", "RSA",
			"-keypass", "000000",
			"-keystore", jks.getAbsolutePath,
			"-storepass", "000000",
			"-storetype", "JKS",
			"-validity", "180")

		// 作成した公開鍵の確認
		val ks = KeyStore.getInstance("JKS")
		using(new FileInputStream(jks)){ in => ks.load(in, "000000".toCharArray) }
		ks.getEntry("node", new KeyStore.PasswordProtection("000000".toCharArray)) match {
			case entry:KeyStore.PrivateKeyEntry =>
				val cert = entry.getCertificate
				out.println(s"Create Public Key: ${debug.fingerprint(cert.getPublicKey)}")
		}

		// ドメイン証明書 (CA 証明書) のダウンロード
		using(new FileOutputStream(ca)){ out =>
			copy(uri.resolve(s"certs/domain").toURL.openStream(userid, password), out, new Array[Byte](1024))
		}

		// インポートした CA 証明書の確認
		val cf = CertificateFactory.getInstance("X.509")
		using(new FileInputStream(ca)){ in => cf.generateCertificates(in) }.foreach{
			case c:X509Certificate =>
				out.println(s"CA Certificate: ${c.getSubjectX500Principal.getName}")
		}

		// CA 証明書のインポート
		keytool(
			"-import",
			"-noprompt",
			"-alias", "kazzla",
			"-file", ca.getAbsolutePath,
			"-keystore", jks.getAbsolutePath,
			"-storetype", "JKS",
			"-storepass", "000000")

		nodeid
	}

	// ============================================================================================
	// CSR の作成
	// ============================================================================================
	/**
	 * CSR を作成します。
	 */
	private[this] def createCSR(ks:File, file:File):Unit = {
		keytool(
			"-certreq",
			"-alias", "node",
			"-file", file.getAbsolutePath,
			"-keypass", "000000",
			"-keystore", ks.getAbsolutePath,
			"-storetype", "JKS",
			"-storepass", "000000")
	}

	// ============================================================================================
	// 証明書の取得
	// ============================================================================================
	/**
	 * 指定された公開鍵に対する証明書を取得します。
	 */
	private[this] def createCerts(uri:URI, csr:File, certs:File, userid:String, password:Array[Char], nodeid:String):Unit = {
		val url = uri.resolve(s"certs/${URLEncoder.encode(nodeid,"UTF-8")}").toURL
		val con = url.openConnection(userid, password).asInstanceOf[HttpURLConnection]
		con.setDoOutput(true)
		con.setDoInput(true)
		con.setUseCaches(false)
		con.setConnectTimeout(10 * 1000)
		con.setFixedLengthStreamingMode(csr.length())
		con.setRequestProperty("Content-Type", "application/pkcs10")
		con.setRequestProperty("Content-Length", csr.length().toString)
		val buffer = new Array[Byte](1024)
		using(new FileInputStream(csr)){ fin =>
			copy(fin, con.getOutputStream, buffer)
		}
		using(new FileOutputStream(certs)){ fout =>
			copy(con.getInputStream, fout, buffer)
		}
		con.disconnect()

		// ダウンロードした証明書の確認
		val cf = CertificateFactory.getInstance("X.509")
		using(new FileInputStream(certs)){ in =>
			cf.generateCertificates(in).foreach{ cert =>
				out.println(s"Public Key: ${debug.fingerprint(cert.getPublicKey)}")
			}
		}
		csr.delete()
	}

	// ============================================================================================
	// 証明書のインポート
	// ============================================================================================
	/**
	 * 証明書をインポートします。
	 */
	private[this] def importCerts(ks:File, certs:File):Unit = {
		keytool(
			"-importcert",
			"-alias", "node",
			"-file", certs.getAbsolutePath,
			"-keypass", "000000",
			"-noprompt",
			"-keystore", ks.getAbsolutePath,
			"-storetype", "JKS",
			"-storepass", "000000")
	}

	// ============================================================================================
	// PKCS#12 の作成
	// ============================================================================================
	/**
	 * JKS 形式のキーストアを PKCS#12 に変換します。
	 */
	def jksToPkcs12(jks:File, p12:File):Unit = {
		keytool(
			"-importkeystore",
			"-srckeystore", jks.getAbsolutePath,
			"-srcstoretype", "JKS",
			"-srcstorepass", "000000",
			"-srcalias", "node",
			"-srckeypass", "000000",
			"-destkeystore", p12.getAbsolutePath,
			"-deststoretype", "PKCS12",
			"-deststorepass", "000000",
			"-destkeypass", "000000",
			"-noprompt")
	}

	private[this] def keytool(args:String*):Unit = {
		// sun.security.tools.KeyTool.main(args.toArray)	  // Java 7
		sun.security.tools.keytool.Main.main(args.toArray)	// Java 8
	}

}
