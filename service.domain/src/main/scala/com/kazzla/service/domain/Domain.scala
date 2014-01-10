/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service.domain

import org.slf4j.LoggerFactory
import java.io._
import com.kazzla.core.io._
import java.security.cert.{CertificateFactory, X509Certificate}
import com.kazzla.service.domain.Domain.CA

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Domain
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
case class Domain(id:String, ca:CA) {

	def createTempFile(prefix:String, suffix:String):File = {
		File.createTempFile(prefix, suffix)
	}

	def createTempFileAndWrite(prefix:String, suffix:String)(f:(OutputStream)=>Unit):File = {
		val file = createTempFile(prefix, suffix)
		using(new FileOutputStream(file)){ out => f(out) }
		file
	}

}

object Domain {
	private[Domain] val logger = LoggerFactory.getLogger(classOf[Domain])

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
