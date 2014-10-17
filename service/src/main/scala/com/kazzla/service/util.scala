/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service

import com.kazzla._
import com.kazzla.asterisk.Session
import com.kazzla.service.storage.StorageEngine
import java.security.cert.X509Certificate
import java.sql.ResultSet
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration.Duration

package object util {

	implicit class IResultSet(rs:ResultSet){
		def getUUID(s:String):UUID = rs.getBytes(s).toUUID
		def getUUID(i:Int):UUID = rs.getBytes(i).toUUID
		def getChar(s:String):Char = rs.getString(s).head
		def getChar(i:Int):Char = rs.getString(i).head
	}

	val SessionId = "com.kazzla.session.id"
	val SessionCertificates = "com.kazzla.session.certificates"
	val SessionAccountId = "com.kazzla.account.id"
	val SessionStorageEngine = "com.kazzla.storage.engine"

	implicit class ISession(session:Session)(implicit ctx:Context) {

		session.onClosed ++ { _ =>
		}

		def id:UUID = getOrElseUpdate(SessionId, ctx.newSessionId)

		def certificates:Seq[X509Certificate] = getOrElseUpdate(SessionCertificates, {
			val sslSession = Await.result(session.wire.tls, Duration.Inf)
			if (sslSession.isEmpty) {
				throw EC.AuthFailure("no a tls")
			}
			sslSession.get.getPeerCertificates.collect { case c:X509Certificate => c }
		})

		def accountId:UUID = getOrElseUpdate(SessionAccountId, {
			val publicKeys = certificates.map {_.getPublicKey}
			val fingerprint = publicKeys.map {_.getEncoded.toMD5}
			if (fingerprint.isEmpty) {
				throw EC.AuthFailure("x.509 certification not specified")
			}
			val ids = ctx.db.select("account_id, public_key" +
				" from auth_public_keys" +
				" where public_key_md5 in(" + ("?" * fingerprint.length).mkString(",") + ")", fingerprint:_*) {
				rs =>
					(rs.getUUID("account_id"), rs.getBytes("public_key"))
			}.filter {
				case (id, publicKey) =>
					publicKeys.exists {pk => java.util.Arrays.equals(publicKey, pk.getEncoded)}
			}.map {_._1}
			if (ids.isEmpty) {
				throw EC.AuthFailure("specified ")
			} else if (ids.size > 1) {
				throw EC.AuthFailure("no a tls")
			}
			ids.head
		})

		def storage:StorageEngine = getOrElseUpdate(SessionStorageEngine, new StorageEngine(accountId, id))

		private[this] def getOrElseUpdate[T](name:String, create: =>T):T = session.getAttribute(name) match {
			case Some(value) => value.asInstanceOf[T]
			case None =>
				val value = create
				session.setAttribute(name, value)
				value
		}
	}

}
