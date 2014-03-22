package com.kazzla.service.schema.code
import java.sql.Connection
import java.sql.ResultSet

case class Timezone(id:Int, code:String, name:String, utcOffset:Int, daylightSaving:Int, createdAt:java.sql.Timestamp, updatedAt:java.sql.Timestamp) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into code_timezones(id, code, name, utc_offset, daylight_saving, created_at, updated_at) values(?, ?, ?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.setString(2, code)
      stmt.setString(3, name)
      stmt.setInt(4, utcOffset)
      stmt.setInt(5, daylightSaving)
      stmt.setTimestamp(6, createdAt)
      stmt.setTimestamp(7, updatedAt)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update code_timezones set code=?, name=?, utc_offset=?, daylight_saving=?, created_at=?, updated_at=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setString(1, code)
      stmt.setString(2, name)
      stmt.setInt(3, utcOffset)
      stmt.setInt(4, daylightSaving)
      stmt.setTimestamp(5, createdAt)
      stmt.setTimestamp(6, updatedAt)
      stmt.setInt(7, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from code_timezones where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Timezone {
  val TableName = "code_timezones"

  def apply(rs:ResultSet):Timezone = Timezone(
    id = rs.getInt("id"),
    code = rs.getString("code"),
    name = rs.getString("name"),
    utcOffset = rs.getInt("utc_offset"),
    daylightSaving = rs.getInt("daylight_saving"),
    createdAt = rs.getTimestamp("created_at"),
    updatedAt = rs.getTimestamp("updated_at")
  )

  def select(params:(String,AnyRef)*)(f:(Timezone)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Timezone] = {
    var r:Option[Timezone] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Timezone] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Timezone)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from code_timezones" +
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
