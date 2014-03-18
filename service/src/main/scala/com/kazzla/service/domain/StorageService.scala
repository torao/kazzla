/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service.domain

import com.kazzla.asterisk._
import com.kazzla.node.RegionNode
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// StorageService
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class StorageService(domain:Domain) extends com.kazzla.asterisk.Service {

	def startup(session:Session):Unit = {
		val storage = session.bind(classOf[RegionNode])
		storage.create(UUID.randomUUID(), 1024 * 1024 * 1024)
	}

}
