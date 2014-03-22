package com.kazzla.service.schema.code
import java.sql.Connection
import java.sql.ResultSet

case class Message(id:Int, language:String, country:Option[String], code:String, content:String, createdAt:java.sql.Timestamp, updatedAt:java.sql.Timestamp) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into code_messages(id, language, country, code, content, created_at, updated_at) values(?, ?, ?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.setString(2, language)
      if(country.isEmpty) stmt.setNull(3, 12) else stmt.setString(3, country.get)
      stmt.setString(4, code)
      stmt.setString(5, content)
      stmt.setTimestamp(6, createdAt)
      stmt.setTimestamp(7, updatedAt)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update code_messages set language=?, country=?, code=?, content=?, created_at=?, updated_at=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setString(1, language)
      if(country.isEmpty) stmt.setNull(2, 12) else stmt.setString(2, country.get)
      stmt.setString(3, code)
      stmt.setString(4, content)
      stmt.setTimestamp(5, createdAt)
      stmt.setTimestamp(6, updatedAt)
      stmt.setInt(7, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from code_messages where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Message {
  val TableName = "code_messages"

  def apply(rs:ResultSet):Message = Message(
    id = rs.getInt("id"),
    language = rs.getString("language"),
    country = Option(rs.getString("country")),
    code = rs.getString("code"),
    content = rs.getString("content"),
    createdAt = rs.getTimestamp("created_at"),
    updatedAt = rs.getTimestamp("updated_at")
  )

  def select(params:(String,AnyRef)*)(f:(Message)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Message] = {
    var r:Option[Message] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Message] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Message)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from code_messages" +
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
