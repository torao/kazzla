package com.kazzla.service.schema.auth
import java.sql.Connection
import java.sql.ResultSet

case class Account(id:Int, hashedPassword:String, salt:String, name:String, language:String, timezone:String, roleId:Option[Int], createdAt:java.sql.Timestamp, updatedAt:java.sql.Timestamp) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into auth_accounts(id, hashed_password, salt, name, language, timezone, role_id, created_at, updated_at) values(?, ?, ?, ?, ?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.setString(2, hashedPassword)
      stmt.setString(3, salt)
      stmt.setString(4, name)
      stmt.setString(5, language)
      stmt.setString(6, timezone)
      if(roleId.isEmpty) stmt.setNull(7, 4) else stmt.setInt(7, roleId.get)
      stmt.setTimestamp(8, createdAt)
      stmt.setTimestamp(9, updatedAt)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update auth_accounts set hashed_password=?, salt=?, name=?, language=?, timezone=?, role_id=?, created_at=?, updated_at=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setString(1, hashedPassword)
      stmt.setString(2, salt)
      stmt.setString(3, name)
      stmt.setString(4, language)
      stmt.setString(5, timezone)
      if(roleId.isEmpty) stmt.setNull(6, 4) else stmt.setInt(6, roleId.get)
      stmt.setTimestamp(7, createdAt)
      stmt.setTimestamp(8, updatedAt)
      stmt.setInt(9, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from auth_accounts where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Account {
  val TableName = "auth_accounts"

  def apply(rs:ResultSet):Account = Account(
    id = rs.getInt("id"),
    hashedPassword = rs.getString("hashed_password"),
    salt = rs.getString("salt"),
    name = rs.getString("name"),
    language = rs.getString("language"),
    timezone = rs.getString("timezone"),
    roleId = {val _roleId=rs.getInt("role_id");if(rs.wasNull) None else Some(_roleId)},
    createdAt = rs.getTimestamp("created_at"),
    updatedAt = rs.getTimestamp("updated_at")
  )

  def select(params:(String,AnyRef)*)(f:(Account)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Account] = {
    var r:Option[Account] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Account] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Account)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from auth_accounts" +
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
