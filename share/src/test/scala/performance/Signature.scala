/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package performance

import java.security._
import scala.collection.JavaConversions._
import scala.util.Try
import java.util.{TimerTask, Timer}

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// SignatureTest
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
object SignatureTest {
	def main(args:Array[String]):Unit = {
		val sample = "the quick brown fox jumps over the lazy dog".getBytes("us-ascii")
		val timer = new Timer()
		Security.getAlgorithms("KeyPairGenerator").toList.sorted.foreach { kpgAlgorithm =>
			System.out.println(s"$kpgAlgorithm --------------------")
			val signAlgorithm = Security.getAlgorithms("Signature").toList.sorted
			System.out.println(signAlgorithm.mkString("\t", "\t", ""))
			// search supported keysize for this algorithm
			(0 to 0xFFFF).foreach{ keysize =>
				val kpg = KeyPairGenerator.getInstance(kpgAlgorithm)
				if(Try(kpg.initialize(keysize)).isSuccess){
					val kp = kpg.generateKeyPair()
					val costs = signAlgorithm.map { signAlgorithm =>
						var breaker = false
						val task = new TimerTask { override def run():Unit = breaker = true }
						timer.schedule(task, 3 * 1000)
						var count = 0
						try {
							val start = System.nanoTime()
							while(! breaker){
								val signer = Signature.getInstance(signAlgorithm)
								signer.initSign(kp.getPrivate)
								signer.update(sample)
								signer.sign()
								count += 1
							}
							val nanos = System.nanoTime() - start
							Some(nanos / count)
						} catch {
							case ex:InvalidKeyException => None
							case ex:SignatureException => None
						}
					}
					costs.find{ _.isDefined } match {
						case Some(_) =>
							System.out.println(s"$keysize\t${costs.map{ c => if(c.isDefined) c.get else "" }.mkString("\t")}")
						case None => None
					}
				}
			}
		}
		timer.cancel()
	}
}
