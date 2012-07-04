/* Copyright (C) 2012 BJöRFUAN
 * This source and related resources are distributed under Apache License, Version 2.0.
 */
package com.kazzla.domain

import java.net.URL
import xml.XML
import com.kazzla.KazzlaException
import org.apache.log4j.Logger
import java.util.{TimerTask, Timer}
import java.util.concurrent.atomic.AtomicBoolean


// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Domain
// +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * <p>
 * </p>
 * @author Takami Torao
 */
class Domain private[Domain](val config:Configuration, val url:URL){
	private[Domain] var _name:String = null
	private[Domain] var _displayName:String = null
	private[Domain] var _authServers = Seq[String]()
	private[Domain] var _regServers = Seq[String]()
	private[this] val _closed = new AtomicBoolean(false)

	def name = _name
	def displayName = _displayName
	def authServers = _authServers
	def registryServers = _regServers

	// ========================================================================
	// タイムアウト監視タイマー
	// ========================================================================
	/**
	 * 全てのセッション上で実行されている呼び出し処理のタイムアウトを監視するタイマーです。
	 */
	private[this] val timer = new Timer("SessionTimeoutWatchdog", true)

	{
		val interval = config("domain.monitor.interval", 3000)
		timer.scheduleAtFixedRate(new TimerTask{
			def run():Unit = cleanup()
		}, interval, interval)
	}

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
	def newSession(): Session = {
		if(closed){
			throw new IllegalStateException("domain closed")
		}
		val session = new Session(this)
		synchronized {
			sessions ::= session
		}
		session
	}

	// ========================================================================
	// ドメインのクローズ
	// ========================================================================
	/**
	 * このドメインで使用していた全てのリソースをクリアします。
	 */
	def close(): Unit = synchronized {
		_closed.set(true)
		sessions.foreach { _.close() }
		sessions = List()
		timer.cancel()
	}

	// ========================================================================
	// クローズ判定
	// ========================================================================
	/**
	 * このドメインがクローズされているかを判定します。
	 */
	def closed = _closed.get()

	// ========================================================================
	// セッションのクローズ
	// ========================================================================
	/**
	 * 指定されたセッションがクローズされた時に呼び出されます。
	 */
	private[domain] def remove(session:Session):Unit = synchronized {
		sessions = sessions.filterNot{ _.eq(session) }
	}

	// ========================================================================
	// ドメインのクリーンアップ
	// ========================================================================
	/**
	 * このドメイン上で確保されている不必要なリソースを開放します。
	 */
	private[this] def cleanup():Unit = {
		sessions.foreach { _.cleanup() }
	}

}

object Domain {
	private[Domain] val logger = Logger.getLogger(classOf[Domain])

	// シャットダウンフックを登録し正常終了時には必ず全ドメインをシャットダウン
	Runtime.getRuntime.addShutdownHook(new Thread("DomainShutdown") {
		override def run():Unit = shutdown()
	})

	/**
	 * ドメインが見つからない時に発生する例外です。
	 */
	class NotFoundException(msg:String, ex:Seq[Throwable]) extends KazzlaException(msg, null, ex:_*)

	// ========================================================================
	// ドメインのインスタンス
	// ========================================================================
	/**
	 * 構築済みドメインのインスタンスです。
	 */
	private[this] var activeDomains = List[Domain]()

	// ========================================================================
	// ドメインの参照
	// ========================================================================
	/**
	 * 指定された URL のいずれかからドメインのプロフィールを参照しドメインのインスタンスを
	 * 構築します。全ての URL が示すプロフィールの内容は同じである必要があります。
	 * @param conf 接続設定
	 * @param urls ドメインプロフィールの URL
	 * @return ドメインのインスタンス
	 * @throws NotFoundException
	 */
	def newDomain(conf:Configuration, urls:URL*): Domain = {
		var exceptions = List[Throwable]()
		scala.util.Random.shuffle(urls.toList).foreach { url =>
			try {
				return create(conf, url)
			} catch {
				case ex:Throwable => exceptions ::= ex
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
	def shutdown(): Unit = synchronized {
		activeDomains.foreach { _.close() }
		activeDomains = List()
	}

	// ========================================================================
	// ドメインの削除
	// ========================================================================
	/**
	 * 指定されたドメインを削除します。
	 */
	private[Domain] def remove(domain: Domain): Unit = synchronized {
		activeDomains = activeDomains.filterNot{ _.eq(domain) }
	}

	// ========================================================================
	// ドメインの構築
	// ========================================================================
	/**
	 * 指定されたストリームから XML を読み込みドメインを構築します。
	 */
	private[this] def create(conf:Configuration, url: URL): Domain = {
		val domain = new Domain(conf, url)

		// ドメインのプロパティを設定
		val doc = XML.load(url)
		val root = doc \\ "domain"
		domain._name = (root \\ "@name").text
		domain._displayName = (root \\ "@display-name").text
		domain._authServers = (root \\ "services" \\ "authentication").map {
			node => node.text
		}.toSeq
		domain._regServers = (root \\ "services" \\ "registry").map {
			node => node.text
		}.toSeq

		// 完成したドメインを登録
		synchronized{
			activeDomains ::= domain
		}
		domain
	}

}