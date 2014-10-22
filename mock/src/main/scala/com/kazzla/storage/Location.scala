/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.storage

import java.util.UUID

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Location
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * ノードの接続先を表します。
 *
 * @param nodeId ノードID
 * @param host ホスト名
 * @param port ポート番号
 */
case class Location(nodeId:UUID, host:String, port:Int)
