/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service

import java.sql.{ResultSet, Connection}
import com.kazzla.core.io._
import scala.Some
import java.security.cert.X509Certificate
import java.util.UUID
import javax.security.auth.x500.X500Principal
import java.security.Principal
import com.kazzla.asterisk.Session
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import javax.net.ssl.SSLSession
import org.slf4j.LoggerFactory
import com.kazzla.asterisk

package object domain {

	private[this] val CNamePattern = """(?i).*CN\s*=\s*([^,]*),.*""".r

	implicit class RickX500Principal(p:Principal){
		def nodeId:Option[UUID] = p match {
			case x:X500Principal =>
				x.getName match {
					case CNamePattern(cn) =>
						try {
							Some(UUID.fromString(cn))
						} catch {
							case ex:IllegalArgumentException => None
						}
					case _ => None
				}
			case _ => None
		}
	}

	implicit class RichSession(session:Session) {
		def nodeId:UUID = sslSession.getPeerPrincipal.nodeId.get
		def sessionId:UUID = sslSession.getValue("sessionId").asInstanceOf[UUID]
		def sslSession:SSLSession = Await.result(session.wire.tls, Duration.Inf) match {
			case Some(ssl) => ssl
			case _ => throw new IllegalStateException("")
		}
	}

	implicit class C(c:Connection){
		def select[T](sql:String, args:Any*)(f:(ResultSet)=>T):List[T] = {
			log(sql, args)
			using(c.prepareStatement(sql)){ s =>
				args.zipWithIndex.foreach{ case (a,i) => s.setObject(i+1, a) }
				val rs = s.executeQuery()
				var t = List[T]()
				while(rs.next()){
					t ::= f(rs)
				}
				t
			}
		}
		def single[T](sql:String, args:Any*)(f:(ResultSet)=>T):Option[T] = {
			log(sql, args)
			using(c.prepareStatement(sql)){ s =>
				args.zipWithIndex.foreach{ case (a,i) => s.setObject(i+1, a) }
				val rs = s.executeQuery()
				if(rs.next()){
					Some(f(rs))
				} else {
					None
				}
			}
		}
		def insertInto(sql:String, args:Any*):Int = {
			log(s"insert into $sql", args)
			using(c.prepareStatement(s"insert into $sql")){ s =>
				args.zipWithIndex.foreach{ case (a,i) => s.setObject(i+1, a) }
				s.executeUpdate()
			}
		}
		def deleteFrom(sql:String, args:Any*):Int = {
			log(s"delete from $sql", args)
			using(c.prepareStatement(s"delete from $sql")){ s =>
				args.zipWithIndex.foreach{ case (a,i) => s.setObject(i+1, a) }
				s.executeUpdate()
			}
		}
		private[this] def log(sql:String, args:Seq[Any]):Unit = if(sqlLog.isDebugEnabled){
			val params = args.zipWithIndex.map{ case (a,i) => s"?$i=${asterisk.debugString(a)}" }.mkString(",")
			sqlLog.debug(s"$sql: $params")
		}
	}

	val sqlLog = LoggerFactory.getLogger("com.kazzla.domain.sql")
}
