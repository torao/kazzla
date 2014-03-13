/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core

import java.security.KeyStore
import javax.net.ssl.{TrustManagerFactory, KeyManagerFactory, SSLContext}
import javax.security.auth.x500.X500Principal
import scala.Some

package object cert {

	implicit class RichKeyStore(ks:KeyStore) {

		// ============================================================================================
		// SSLContext の作成
		// ============================================================================================
		/**
		 * キーストアから SSL Context を作成します。
		 */
		def getSSLContext(pass:Array[Char]):SSLContext = {
			val ssl = SSLContext.getInstance("TLS")
			ssl.init({
				val km = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
				km.init(ks, pass)
				km.getKeyManagers
			}, {
				val tm = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
				tm.init(ks)
				tm.getTrustManagers
			}, null)
			ssl
		}
	}

	implicit class RichX500Principal(p:X500Principal) {

		// ============================================================================================
		// Common Name の参照
		// ============================================================================================
		/**
		 * 指定された X509Principal から Common Name を参照します。
		 */
		def commonName:Option[String] = distinguishedNames.get("cn") match {
			case Some(cnames) => Some(cnames.head)
			case None => None
		}

		// ============================================================================================
		// Common Name の参照
		// ============================================================================================
		/**
		 * 指定された X509Principal から Common Name を参照します。
		 */
		def distinguishedNames:Map[String,Seq[String]] = p.getName.split("\\s*,\\s*").toSeq
			.filter{ _.trim.length > 0 }
			.map{ _.split("\\s*=\\s*", 2) }
			.map{ a => a(0).toLowerCase -> a(1) }
			.foldLeft(Map[String,List[String]]()){ case (map, (key, value)) =>
			map.get(key) match {
				case Some(list) => map.updated(key, value :: list)
				case None => map.updated(key, List(value))
			}
		}
	}

}
