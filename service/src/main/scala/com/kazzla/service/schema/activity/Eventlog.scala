package com.kazzla.service.schema.activity
import java.sql.Connection
import java.sql.ResultSet

case class Eventlog(id:Int, accountId:Option[Int], level:Int, code:Int, remote:String, message:String, createdAt:java.sql.Timestamp, updatedAt:java.sql.Timestamp) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into activity_eventlogs(id, account_id, level, code, remote, message, created_at, updated_at) values(?, ?, ?, ?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      if(accountId.isEmpty) stmt.setNull(2, 4) else stmt.setInt(2, accountId.get)
      stmt.setInt(3, level)
      stmt.setInt(4, code)
      stmt.setString(5, remote)
      stmt.setString(6, message)
      stmt.setTimestamp(7, createdAt)
      stmt.setTimestamp(8, updatedAt)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update activity_eventlogs set account_id=?, level=?, code=?, remote=?, message=?, created_at=?, updated_at=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      if(accountId.isEmpty) stmt.setNull(1, 4) else stmt.setInt(1, accountId.get)
      stmt.setInt(2, level)
      stmt.setInt(3, code)
      stmt.setString(4, remote)
      stmt.setString(5, message)
      stmt.setTimestamp(6, createdAt)
      stmt.setTimestamp(7, updatedAt)
      stmt.setInt(8, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from activity_eventlogs where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Eventlog {
  val TableName = "activity_eventlogs"

  def apply(rs:ResultSet):Eventlog = Eventlog(
    id = rs.getInt("id"),
    accountId = {val _accountId=rs.getInt("account_id");if(rs.wasNull) None else Some(_accountId)},
    level = rs.getInt("level"),
    code = rs.getInt("code"),
    remote = rs.getString("remote"),
    message = rs.getString("message"),
    createdAt = rs.getTimestamp("created_at"),
    updatedAt = rs.getTimestamp("updated_at")
  )

  def select(params:(String,AnyRef)*)(f:(Eventlog)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Eventlog] = {
    var r:Option[Eventlog] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Eventlog] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Eventlog)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from activity_eventlogs" +
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
