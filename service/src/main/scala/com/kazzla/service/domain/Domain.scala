/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service.domain

import com.kazzla.core.ConsistencyException
import com.kazzla.core.cert._
import com.kazzla.core.io._
import com.kazzla.service.domain.Domain.CA
import java.io._
import java.lang.management.ManagementFactory
import java.net.URI
import java.security.MessageDigest
import java.security.cert.{CertificateFactory, X509Certificate}
import java.sql.{ResultSet, Timestamp, Connection}
import java.util.{UUID, TimeZone, Date}
import javax.sql.DataSource
import org.slf4j.LoggerFactory
import scala.util.{Failure, Success, Try}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Domain
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
case class Domain(id:String, ca:CA, dataSource:DataSource) {

	import Domain._

	// ============================================================================================
	// 認証の実行
	// ============================================================================================
	/**
	 * 認証を行います。
	 */
	def authenticate(username:String, password:String):Option[Account] = getAccount(username) match {
		case Some(account) =>
			if(account.authenticate(password)){
				Some(account)
			} else {
				None
			}
		case None => None
	}

	// ============================================================================================
	// 認証の実行
	// ============================================================================================
	/**
	 * 認証を行います。
	 */
	def getAccount(username:String):Option[Account] = trx { c =>
		def resultSetToAccount(r:ResultSet):Account = {
			val tz = TimeZone.getTimeZone(r.getString("timezone"))
			Account(this,
				r.getInt("id"), r.getString("hashed_password"), r.getString("salt"), r.getString("name"),
				r.getString("language"), tz, r.getInt("role_id"))
		}
		c.single("select * from auth_accounts where name=?", username.toLowerCase)(resultSetToAccount) orElse {
			c.single("select account_id from auth_contacts where uri=?", s"mailto:${username.toLowerCase}"){ r =>
				r.getInt("account_id")
			} match {
				case Some(accountId) =>
					c.single("select * from auth_accounts where id=?", accountId)(resultSetToAccount)
				case None => None
			}
		}
	}

	// ==============================================================================================
	// ノード証明書の登録
	// ==============================================================================================
	/**
	 * 指定されたノード証明書をアカウントに関連づけて保存します。
	 */
	def registerNodeCertificate(account:Account, cert:X509Certificate):Unit = {
		cert.getSubjectX500Principal.commonName match {
			case Some(uuid) =>
				val now = new java.sql.Timestamp(System.currentTimeMillis())
				trx { c =>
					c.insertInto("node_nodes(account_id,uuid,certificate,updated_at,created_at) values(?,?,?,?,?)",
						account.id, uuid, cert.getEncoded, now, now)
				}
			case None =>
				throw new ConsistencyException(s"invalid distinguished name: ${cert.getSubjectX500Principal.getName}")
		}
	}

	// ==============================================================================================
	// ノードセッションの作成
	// ==============================================================================================
	/**
	 * ノードのセッションを作成します。
	 * @return セッション ID
	 */
	def openNodeSession(nodeId:UUID, sessionId:UUID, endpoints:Array[String]):Unit = trx { c =>
		// TODO 既に同じノード ID でセッションが存在している場合の仕様を決める
		logger.debug(s"openNodeSession($nodeId,$sessionId,${endpoints.mkString(",")})")
		val now = new Timestamp(System.currentTimeMillis())
		c.insertInto("node_sessions(session_id,node_id,endpoints,proxy,created_at,updated_at) values(?,?,?,?,?,?)",
			sessionId.toString, nodeId.toString, endpoints.mkString(","),
			ManagementFactory.getRuntimeMXBean.getName, now, now)
	}

	// ==============================================================================================
	// ノードセッションの終了
	// ==============================================================================================
	/**
	 * ノードのセッションを終了します。
	 */
	def closeNodeSession(sessionId:UUID):Unit = trx { c =>
		logger.debug(s"closeNodeSession($sessionId)")
		c.deleteFrom("node_sessions where session_id=?", sessionId)
	}

	def createTempFile(prefix:String, suffix:String):File = {
		File.createTempFile(prefix, suffix)
	}

	def createTempFileAndWrite(prefix:String, suffix:String)(f:(OutputStream)=>Unit):File = {
		val file = createTempFile(prefix, suffix)
		usingOutput(file){ out => f(out) }
		file
	}

	// ==============================================================================================
	// 新規 UUID の発行
	// ==============================================================================================
	/**
	 * 新しい UUID を発行します。
	 */
	def newUUID():UUID = UUID.randomUUID()

	def newCertificateDName(account:Account):String = {
		s"CN=${newUUID()}, OU=${account.name}, OU=Node, O=Kazzla, ST=Tokyo, C=JP"
	}

	def trx[T](f:(Connection)=>T):T = using(dataSource.getConnection){ c =>
		Try {
			c.setAutoCommit(false)
			c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED)
			f(c)
		} match {
			case Success(result) =>
				c.commit()
				result
			case Failure(ex) =>
				c.rollback()
				throw ex
		}
	}

}

object Domain {
	private[Domain] val logger = LoggerFactory.getLogger(classOf[Domain])

	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	// CA
	// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
	/**
	 *
	 * @param dir CA ディレクトリ
	 */
	class CA(dir:File) {
		val certFile:File = new File(dir, "cacert.pem")
		def rawCert:Array[Byte] = using(new FileInputStream(certFile)){ _.readFully() }
		def cert:X509Certificate = {
			CertificateFactory.getInstance("X.509")
				.generateCertificate(new ByteArrayInputStream(rawCert))
				.asInstanceOf[X509Certificate]
		}


		// ============================================================================================
		// 証明書の発行
		// ============================================================================================
		/**
		 * 指定された CSR ファイルに署名し証明書を発行します。
		 */
		def issue(csr:File):X509Certificate = {
			val pb = new ProcessBuilder(
				"/usr/bin/openssl", "ca",
				"-config", new File(dir, "openssl.cnf").getAbsolutePath,
				"-batch",
				"-in", csr.getAbsolutePath,
				"-days", "3650",
				"-passin", "pass:kazzla",
				"-keyfile", new File(dir, "private/cakey.pem").getAbsolutePath)
			val proc = pb.start()
			val buffer = new Array[Byte](1024)
			val out = new ByteArrayOutputStream()
			copy(proc.getInputStream, out, buffer)
			copy(proc.getErrorStream, System.err, buffer)
			proc.waitFor()
			if(proc.exitValue() != 0){
				throw new IOException()
			}

			CertificateFactory.getInstance("X.509")
				.generateCertificate(new ByteArrayInputStream(out.toByteArray))
				.asInstanceOf[X509Certificate]
		}
	}
}

case class Account(domain:Domain, id:Int, hashedPassword:String, salt:String, name:String, language:String, timezone:TimeZone, roleId:Int){
	lazy val contacts:Seq[Account.Contact] = domain.trx{ c =>
		c.select("select id,uri,confirmed_at from auth_contacts where account_id=?", id){ r =>
			Account.Contact(r.getInt("id"), URI.create(r.getString("uri")), Option(r.getTimestamp("confirmed_at")))
		}
	}
	lazy val role:Option[Account.Role] = domain.trx { c =>
		c.single("select id,name,permissions from auth_role where id=?", roleId){ r =>
			Account.Role(r.getInt(id), r.getString("name"), r.getString("permissions").split("\\s*,\\s*").toSet)
		}
	}

	def authenticate(password:String):Boolean = {
		hashedPassword.split(":", 2) match {
			case Array(scheme, hash) =>
				val md = MessageDigest.getInstance(scheme.toLowerCase match {
					case "md5" => "MD5"
					case "sha1" => "SHA-1"
					case "sha256" => "SHA-256"
					case "sha384" => "SHA-384"
					case "sha512" => "SHA-512"
				})
				val bin = (password + ":" + salt).getBytes("UTF-8")
				md.digest(bin).toHexString.toLowerCase == hash
			case _ =>
				throw new ConsistencyException(s"invalid hashed-password format: $hashedPassword")
		}
	}
}

object Account {
	case class Contact(id:Int, uri:URI, confirmedAt:Option[Date])
	case class Role(id:Int, name:String, permissions:Set[String])
}
