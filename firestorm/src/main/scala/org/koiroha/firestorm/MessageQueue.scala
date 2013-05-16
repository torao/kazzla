/*
 * Copyright (c) 2012 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * org.koiroha.firestorm.http://www.apache.org/licenses/LICENSE-2.0.html
 */

package org.koiroha.firestorm

import java.util.concurrent.ArrayBlockingQueue

/**
 * Created with IntelliJ IDEA.
 * User: torao
 * Date: 2012/12/26
 * Time: 2:07
 * To change this template use File | Settings | File Templates.
 */
trait MessageQueue {

}

class LocalMessageQueue extends MessageQueue {

	private[this] val queue = new ArrayBlockingQueue(Int.MaxValue)
}