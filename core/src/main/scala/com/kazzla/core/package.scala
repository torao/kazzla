/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core

import javax.security.auth.x500.X500Principal

package object cert {

	// ==============================================================================================
	// Common Name の参照
	// ==============================================================================================
	/**
	 * 指定された X509Principal から Common Name を参照します。
	 */
	def getCommonName(p:X500Principal):Option[String] = parseDistinguishedNames(p.getName).get("cn") match {
		case Some(cnames) => Some(cnames.head)
		case None => None
	}

	// ==============================================================================================
	// Common Name の参照
	// ==============================================================================================
	/**
	 * 指定された X509Principal から Common Name を参照します。
	 */
	def parseDistinguishedNames(name:String):Map[String,Seq[String]] = name.split("\\s*,\\s*").toSeq
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
