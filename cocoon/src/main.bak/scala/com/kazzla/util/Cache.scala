package com.kazzla.util
import java.io._
import java.net._
import java.util._
import java.text._

/**
 * オンメモリキャッシュを行うためのクラスです。
 *
 * @param url オブジェクトの生成元 URL
 * @param verifyAfterInMillis オブジェクトの更新を検査するための間隔
 * @param loader URL から更新日時付きでストリームを参照する処理
 * @param translator ストリームからオブジェクトを生成する処理
 * @tparam T このキャッシュが保持するオブジェクトの型
 */
class Cache[T](url:URL, verifyAfterInMillis:Long = 2000,
	loader:(URL,Long)=>Option[Cache.Source] = Cache.DEFAULT_LOADER)(translator:(InputStream, URL)=>T) {

	private[this] var value:Option[T] = None
	private[this] var lastVerified = Long.MinValue
	private[this] var lastModified = Long.MinValue

	/**
	 * このキャッシュが保持するオブジェクトを参照します。
	 * @return
	 */
	def get():T = {
		val now = System.currentTimeMillis
		if(this.lastVerified + verifyAfterInMillis <= now || value.isEmpty){
			this.lastVerified = now
			loader(url, this.lastModified) match {
				case Some(source) =>
					Cache.logger.debug("modification detected: %s (%d -> %d)".format(url, this.lastModified, source.version))
					IO.using(source.in){ in =>
						this.value = Some(translator(in, url))
						this.lastModified = source.version
					}
				case None => None
			}
		}
		this.value.get
	}

}

object Cache {
	private[Cache] val logger = org.apache.log4j.Logger.getLogger(Cache.getClass)

	case class Source(in:InputStream, version:Long)

	/**
	 * URL のスキームを判断し file または http(s) のデータ更新日時を取り扱うローダーです。
	 */
	val DEFAULT_LOADER:(URL,Long)=>Option[Cache.Source] = { (url, lastModified) =>
		url.getProtocol match {
			case "file" =>
				val file = new File(url.toURI)
				val modified = file.lastModified
				if(lastModified != modified){
					Some(Source(new FileInputStream(file), modified))
				} else {
					None
				}
			case "http" | "https" =>
				val con = url.openConnection().asInstanceOf[HttpURLConnection]
				con.setDoOutput(false)
				con.setDoInput(true)
				con.setInstanceFollowRedirects(true)
				if(lastModified != Long.MinValue){
					val fmt = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
					fmt.setTimeZone(TimeZone.getTimeZone("GMT"))
					con.setRequestProperty("If-Modified-Since", fmt.format(new Date(lastModified)))
				}
				con.getResponseCode match {
					case HttpURLConnection.HTTP_OK =>
						Some(Source(con.getInputStream, con.getLastModified))
					case HttpURLConnection.HTTP_NOT_MODIFIED =>
						None
					case code =>
						throw new IOException("unsupported response code: %s".format(code))
				}
			case _ =>
				if(lastModified == Long.MinValue){
					Some(Source(url.openStream(), 0))
				} else {
					None
				}
		}
	}

	val PROPERTIES_TRANSLATOR:(InputStream,URL)=>Properties = { (in, url) =>
		val prop = new Properties()
		prop.load(in)
		prop
	}

}

