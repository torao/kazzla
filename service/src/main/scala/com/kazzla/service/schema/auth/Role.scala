package com.kazzla.service.schema.auth
import java.sql.Connection
import java.sql.ResultSet

case class Role(id:Int, name:String, permissions:String, createdAt:java.sql.Timestamp, updatedAt:java.sql.Timestamp) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into auth_roles(id, name, permissions, created_at, updated_at) values(?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.setString(2, name)
      stmt.setString(3, permissions)
      stmt.setTimestamp(4, createdAt)
      stmt.setTimestamp(5, updatedAt)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update auth_roles set name=?, permissions=?, created_at=?, updated_at=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setString(1, name)
      stmt.setString(2, permissions)
      stmt.setTimestamp(3, createdAt)
      stmt.setTimestamp(4, updatedAt)
      stmt.setInt(5, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from auth_roles where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Role {
  val TableName = "auth_roles"

  def apply(rs:ResultSet):Role = Role(
    id = rs.getInt("id"),
    name = rs.getString("name"),
    permissions = rs.getString("permissions"),
    createdAt = rs.getTimestamp("created_at"),
    updatedAt = rs.getTimestamp("updated_at")
  )

  def select(params:(String,AnyRef)*)(f:(Role)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Role] = {
    var r:Option[Role] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Role] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Role)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from auth_roles" +
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
