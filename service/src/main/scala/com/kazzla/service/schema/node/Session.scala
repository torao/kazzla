package com.kazzla.service.schema.node
import java.sql.Connection
import java.sql.ResultSet

case class Session(id:Int, sessionId:Option[String], nodeId:Option[String], endpoints:Option[String], proxy:Option[String], createdAt:java.sql.Timestamp, updatedAt:java.sql.Timestamp) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into node_sessions(id, session_id, node_id, endpoints, proxy, created_at, updated_at) values(?, ?, ?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      if(sessionId.isEmpty) stmt.setNull(2, 12) else stmt.setString(2, sessionId.get)
      if(nodeId.isEmpty) stmt.setNull(3, 12) else stmt.setString(3, nodeId.get)
      if(endpoints.isEmpty) stmt.setNull(4, 12) else stmt.setString(4, endpoints.get)
      if(proxy.isEmpty) stmt.setNull(5, 12) else stmt.setString(5, proxy.get)
      stmt.setTimestamp(6, createdAt)
      stmt.setTimestamp(7, updatedAt)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update node_sessions set session_id=?, node_id=?, endpoints=?, proxy=?, created_at=?, updated_at=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      if(sessionId.isEmpty) stmt.setNull(1, 12) else stmt.setString(1, sessionId.get)
      if(nodeId.isEmpty) stmt.setNull(2, 12) else stmt.setString(2, nodeId.get)
      if(endpoints.isEmpty) stmt.setNull(3, 12) else stmt.setString(3, endpoints.get)
      if(proxy.isEmpty) stmt.setNull(4, 12) else stmt.setString(4, proxy.get)
      stmt.setTimestamp(5, createdAt)
      stmt.setTimestamp(6, updatedAt)
      stmt.setInt(7, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from node_sessions where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Session {
  val TableName = "node_sessions"

  def apply(rs:ResultSet):Session = Session(
    id = rs.getInt("id"),
    sessionId = Option(rs.getString("session_id")),
    nodeId = Option(rs.getString("node_id")),
    endpoints = Option(rs.getString("endpoints")),
    proxy = Option(rs.getString("proxy")),
    createdAt = rs.getTimestamp("created_at"),
    updatedAt = rs.getTimestamp("updated_at")
  )

  def select(params:(String,AnyRef)*)(f:(Session)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Session] = {
    var r:Option[Session] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Session] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Session)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from node_sessions" +
      (if(params.isEmpty) "" else params.map{_._1+"=?"}.mkString(" where "," and ","")) +
      limit.map{l=>s" limit $l"}.getOrElse("")
    val stmt = con.prepareStatement(sql)
    try {
      (1 to params.length).foreach{ i => stmt.setObject(i, params(i-1)._2) }
      val rs = stmt.executeQuery()
      try { while(rs.next) f(apply(rs)) } finally { rs.close() }
    } finally { stmt.close() }
  }
}
