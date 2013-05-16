/*
 * Copyright (c) 2013 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
 */

package org.koiroha.firestorm.jmx

private[jmx] class History extends Monitor {
	case class Mark(tm:Long, var value:Long)
	val history = new Array[Mark](15 * 60)
	var current = Mark(System.nanoTime(), 0)
	history(0) = current

	def touch(){
		System.arraycopy(history, 0, history, 1, history.length - 1)
		current = Mark(System.nanoTime(), 0)
		history(0) = current
	}

	/**
	 * `seconds` 〜 `seconds + 1` 範囲の平均値を参照します。
	 * @param seconds
	 * @return
	 */
	def average(seconds:Int):Double = {
		val end = System.nanoTime()
		var begin = end
		var sum:Long = 0
		def result:()=>Double = { () =>
			if (begin == end){
				Double.NaN
			} else {
				sum.toDouble / (end - begin) * 1000.0 * 1000.0 * 1000.0
			}
		}
		history.zipWithIndex.foreach{ case (mark, i) =>
			begin = mark.tm
			sum += mark.value
			if (seconds == i){
				return result()
			}
		}
		result()
	}
}
