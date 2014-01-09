/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.core.tools

import java.io.{InputStreamReader, PrintWriter, BufferedReader}
import java.util
import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}
import sun.misc.BASE64Encoder

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// ShellTools
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class ShellTools {

	// ==============================================================================================
	// プロンプト入力
	// ==============================================================================================
	/**
	 * 現在の UI から入力を行います。
	 */
	def prompt(msg:String, default:String)(verify:(String)=>Unit):String = {
		val out = new PrintWriter(System.out)
		val in = new BufferedReader(new InputStreamReader(System.in))
		@tailrec
		def prompt():String = {
			out.print(s"$msg [$default]: ")
			out.flush()
			val line = in.readLine()
			if(line.isEmpty){
				default
			} else Try(verify(line)) match {
				case Success(b) =>
					line
				case Failure(ex) =>
					out.println(s"${ex.getMessage}: $line")
					out.flush()
					prompt()
			}
		}
		prompt()
	}

	// ==============================================================================================
	// パスワード入力
	// ==============================================================================================
	/**
	 * 現在の UI からパスワード入力を行います。
	 */
	def password(msg:String)(verify:(Array[Char])=>Unit):Array[Char] = {
		@tailrec
		def password():Array[Char] = {
			System.out.print(s"$msg: ")
			val line = System.console().readPassword()
			Try(verify(line)) match {
				case Success(b) =>
					line
				case Failure(ex) =>
					System.out.println(s"${ex.getMessage}: $line")
					password()
			}
		}
		if(System.console() == null){
			prompt(msg, ""){ p => verify(p.toCharArray) }.toCharArray
		} else {
			password()
		}
	}

	// ==============================================================================================
	// プロンプト入力
	// ==============================================================================================
	/**
	 * 現在の UI から入力を行います。
	 */

	implicit class BasicAuthURL(url:java.net.URL){
		def openConnection(userid:String, password:Array[Char]):java.net.URLConnection = {
			val con = url.openConnection()
			con.setRequestProperty("Authorization", s"Basic ${credentials(userid,password)}")
			con
		}
		def openStream(userid:String, password:Array[Char]):java.io.InputStream = {
			val con = url.openConnection(userid, password)
			con.setDoOutput(false)
			con.setDoInput(true)
			con.getInputStream
		}
		private[this] def credentials(userid:String, password:Array[Char]):String = {
			val userpass = userid.getBytes("UTF-8") ++ Array(':'.toByte) ++ password.map{ _.toByte }
			val credentials = new BASE64Encoder().encodeBuffer(userpass)
			util.Arrays.fill(userpass, 0x00.toByte)
			credentials.replace("\n", "")
		}
	}

}
