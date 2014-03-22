package com.kazzla.service.schema.storage
import java.sql.Connection
import java.sql.ResultSet

case class Fstype(id:Int, name:String, url:String) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into storage_fstype(id, name, url) values(?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.setString(2, name)
      stmt.setString(3, url)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update storage_fstype set name=?, url=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setString(1, name)
      stmt.setString(2, url)
      stmt.setInt(3, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from storage_fstype where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Fstype {
  val TableName = "storage_fstype"

  def apply(rs:ResultSet):Fstype = Fstype(
    id = rs.getInt("id"),
    name = rs.getString("name"),
    url = rs.getString("url")
  )

  def select(params:(String,AnyRef)*)(f:(Fstype)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Fstype] = {
    var r:Option[Fstype] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Fstype] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Fstype)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from storage_fstype" +
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
