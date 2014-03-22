package com.kazzla.service.schema.client
import java.sql.Connection
import java.sql.ResultSet

case class Session(id:Int, agentId:Int) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into client_sessions(id, agent_id) values(?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.setInt(2, agentId)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update client_sessions set agent_id=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, agentId)
      stmt.setInt(2, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from client_sessions where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Session {
  val TableName = "client_sessions"

  def apply(rs:ResultSet):Session = Session(
    id = rs.getInt("id"),
    agentId = rs.getInt("agent_id")
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
    val sql = "select * from client_sessions" +
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
