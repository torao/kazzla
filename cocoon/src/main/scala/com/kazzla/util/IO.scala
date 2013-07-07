package com.kazzla.util

import java.io._

/**
 * 入出力ユーティリティ
 */
object IO {
	private[IO] val logger = org.apache.log4j.Logger.getLogger(IO.getClass)

	/**
	 * 指定された Closeable なオブジェクトのスコープを保障して使用するためのユーティリティです。
	 */
	def using[T <: Closeable,U](c:T)(f:(T)=>U) = try {
		f(c)
	} finally {
		try {
			Option(c).foreach{ _.close }
		} catch {
			case ex:IOException =>
				logger.warn("fail to close stream of %s".format(c.getClass.getName), ex)
		}
	}

}
