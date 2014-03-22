package com.kazzla.service.schema.code
import java.sql.Connection
import java.sql.ResultSet

case class Continent(id:Int, code:String, name:String, createdAt:java.sql.Timestamp, updatedAt:java.sql.Timestamp) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into code_continents(id, code, name, created_at, updated_at) values(?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.setString(2, code)
      stmt.setString(3, name)
      stmt.setTimestamp(4, createdAt)
      stmt.setTimestamp(5, updatedAt)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update code_continents set code=?, name=?, created_at=?, updated_at=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setString(1, code)
      stmt.setString(2, name)
      stmt.setTimestamp(3, createdAt)
      stmt.setTimestamp(4, updatedAt)
      stmt.setInt(5, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from code_continents where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Continent {
  val TableName = "code_continents"

  def apply(rs:ResultSet):Continent = Continent(
    id = rs.getInt("id"),
    code = rs.getString("code"),
    name = rs.getString("name"),
    createdAt = rs.getTimestamp("created_at"),
    updatedAt = rs.getTimestamp("updated_at")
  )

  def select(params:(String,AnyRef)*)(f:(Continent)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Continent] = {
    var r:Option[Continent] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Continent] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Continent)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from code_continents" +
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
