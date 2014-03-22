package com.kazzla.service.schema.client
import java.sql.Connection
import java.sql.ResultSet

case class CertifiedAgent(id:Int, group:String, name:String) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into client_certified_agents(id, group, name) values(?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.setString(2, group)
      stmt.setString(3, name)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update client_certified_agents set group=?, name=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setString(1, group)
      stmt.setString(2, name)
      stmt.setInt(3, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from client_certified_agents where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object CertifiedAgent {
  val TableName = "client_certified_agents"

  def apply(rs:ResultSet):CertifiedAgent = CertifiedAgent(
    id = rs.getInt("id"),
    group = rs.getString("group"),
    name = rs.getString("name")
  )

  def select(params:(String,AnyRef)*)(f:(CertifiedAgent)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[CertifiedAgent] = {
    var r:Option[CertifiedAgent] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[CertifiedAgent] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(CertifiedAgent)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from client_certified_agents" +
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
