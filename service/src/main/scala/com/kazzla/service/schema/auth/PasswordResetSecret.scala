package com.kazzla.service.schema.auth
import java.sql.Connection
import java.sql.ResultSet

case class PasswordResetSecret(id:Int, accountId:Int, secret:String, issuedAt:java.sql.Timestamp, createdAt:java.sql.Timestamp, updatedAt:java.sql.Timestamp) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into auth_password_reset_secrets(id, account_id, secret, issued_at, created_at, updated_at) values(?, ?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.setInt(2, accountId)
      stmt.setString(3, secret)
      stmt.setTimestamp(4, issuedAt)
      stmt.setTimestamp(5, createdAt)
      stmt.setTimestamp(6, updatedAt)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update auth_password_reset_secrets set account_id=?, secret=?, issued_at=?, created_at=?, updated_at=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, accountId)
      stmt.setString(2, secret)
      stmt.setTimestamp(3, issuedAt)
      stmt.setTimestamp(4, createdAt)
      stmt.setTimestamp(5, updatedAt)
      stmt.setInt(6, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from auth_password_reset_secrets where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object PasswordResetSecret {
  val TableName = "auth_password_reset_secrets"

  def apply(rs:ResultSet):PasswordResetSecret = PasswordResetSecret(
    id = rs.getInt("id"),
    accountId = rs.getInt("account_id"),
    secret = rs.getString("secret"),
    issuedAt = rs.getTimestamp("issued_at"),
    createdAt = rs.getTimestamp("created_at"),
    updatedAt = rs.getTimestamp("updated_at")
  )

  def select(params:(String,AnyRef)*)(f:(PasswordResetSecret)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[PasswordResetSecret] = {
    var r:Option[PasswordResetSecret] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[PasswordResetSecret] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(PasswordResetSecret)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from auth_password_reset_secrets" +
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
