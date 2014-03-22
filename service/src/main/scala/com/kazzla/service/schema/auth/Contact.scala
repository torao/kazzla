package com.kazzla.service.schema.auth
import java.sql.Connection
import java.sql.ResultSet

case class Contact(id:Int, accountId:Int, uri:String, confirmed:Boolean, confirmedAt:Option[java.sql.Timestamp], createdAt:java.sql.Timestamp, updatedAt:java.sql.Timestamp) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into auth_contacts(id, account_id, uri, confirmed, confirmed_at, created_at, updated_at) values(?, ?, ?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.setInt(2, accountId)
      stmt.setString(3, uri)
      stmt.setBoolean(4, confirmed)
      if(confirmedAt.isEmpty) stmt.setNull(5, 93) else stmt.setTimestamp(5, confirmedAt.get)
      stmt.setTimestamp(6, createdAt)
      stmt.setTimestamp(7, updatedAt)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update auth_contacts set account_id=?, uri=?, confirmed=?, confirmed_at=?, created_at=?, updated_at=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, accountId)
      stmt.setString(2, uri)
      stmt.setBoolean(3, confirmed)
      if(confirmedAt.isEmpty) stmt.setNull(4, 93) else stmt.setTimestamp(4, confirmedAt.get)
      stmt.setTimestamp(5, createdAt)
      stmt.setTimestamp(6, updatedAt)
      stmt.setInt(7, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from auth_contacts where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Contact {
  val TableName = "auth_contacts"

  def apply(rs:ResultSet):Contact = Contact(
    id = rs.getInt("id"),
    accountId = rs.getInt("account_id"),
    uri = rs.getString("uri"),
    confirmed = rs.getBoolean("confirmed"),
    confirmedAt = Option(rs.getTimestamp("confirmed_at")),
    createdAt = rs.getTimestamp("created_at"),
    updatedAt = rs.getTimestamp("updated_at")
  )

  def select(params:(String,AnyRef)*)(f:(Contact)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Contact] = {
    var r:Option[Contact] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Contact] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Contact)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from auth_contacts" +
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
