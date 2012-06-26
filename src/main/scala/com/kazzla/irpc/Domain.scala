/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.irpc

import java.net.URL
import xml.XML
import com.kazzla.KazzlaException


// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Domain
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * <p>
 * </p>
 * @author Takami Torao
 */
class Domain private[Domain](val url:URL, val name:String, val displayName:String, authServers:Seq[String], regServers:Seq[String]) {
	// プロパティ情報とセッションの管理のみのクラス

	// ========================================================================
	// セッション一覧
	// ========================================================================
	/**
	 * このドメイン上で使用されているセッションの一覧です。
	 */
	private[this] var sessions = List[Session]()

	// ========================================================================
	// セッションの構築
	// ========================================================================
	/**
	 * このドメイン上の新しいセッションを構築します。
	 * @return セッション
	 */
	def newSession():Session = {
		val session = new Session(this)
		synchronized{
			sessions += session
		}
		session
	}

	// ========================================================================
	// ドメインのクローズ
	// ========================================================================
	/**
	 * このドメインで使用していた全てのリソースをクリアします。
	 */
	def close():Unit = synchronized{
		sessions.foreach{ _.close() }
		sessions = List[Session]()
	}

}

object Domain {

	// シャットダウンフックを登録し正常終了時に必ず
	Runtime.getRuntime.addShutdownHook(new Thread("DomainShutdown"){
		override def run(){ shutdown() }
	})

	/**
	 * ドメインが見つからない時に発生する例外です。
	 */
	class NotFoundException(msg: String, ex: Seq[Throwable]) extends KazzlaException(msg, null, ex: _*)

	// ========================================================================
	// プロフィールのキャッシュ
	// ========================================================================
	/**
	 * 読み出し済みのドメインプロフィールのキャッシュです。
	 */
	private[this] var cache = Map[URL,Domain]()

	// ========================================================================
	// ドメインの参照
	// ========================================================================
	/**
	 * 指定された URL のいずれかからドメインのプロフィールを参照しドメインのインスタンスを
	 * 構築します。全ての URL が示すプロフィールの内容は同じである必要があります。
	 * @param urls ドメインプロフィールの URL
	 * @return ドメインのインスタンス
	 * @throws NotFoundException
	 */
	def getDomain(urls:URL*): Domain = {
		var exceptions = List[Throwable]()
		scala.util.Random.shuffle(urls.toList).foreach {
			url =>
				try {
					return getDomain(getProfile(url))
				} catch {
					case ex: Throwable => exceptions ::= ex
				}
		}

		throw new NotFoundException("" + urls.mkString("[", ",", "]"), exceptions)
	}

	// ========================================================================
	// 全ドメインのシャットダウン
	// ========================================================================
	/**
	 * 全てのドメインをクローズし使用していたリソースを開放します。
	 */
	def shutdown():Unit = synchronized{
		cache.values.foreach{ _.close() }
		cache = Map[URL,Domain]()
	}

	// ========================================================================
	// ドメインの削除
	// ========================================================================
	/**
	 * 指定されたドメインを削除します。
	 */
	private[Domain] def remove(domain:Domain):Unit = synchronized{
		cache = cache - domain.url
	}

	// ========================================================================
	// プロフィールの構築
	// ========================================================================
	/**
	 * 指定されたストリームから XML を読み込みドメインプロフィールを構築します。
	 */
	private[this] def getProfile(url:URL):Domain = {
		cache.get(url) match {
			case Some(profile) => profile
			case None =>
				this.synchronized {
					// プロフィールの構築
					val doc = XML.load(url)
					val root = doc \\ "domain"
					val name = (root \\ "@name").text
					val displayName = (root \\ "@display-name").text
					val version = (root \\ "@version").text.toInt
					val auth = (root \\ "service" \\ "authentication").map {
						node => node.text
					}.toSeq
					val reg = (root \\ "service" \\ "registry").map {
						node => node.text
					}.toSeq
					val domain = new Domain(name, displayName, version, auth, reg)
					cache += (url -> domain)
					domain
				}
		}
	}

}