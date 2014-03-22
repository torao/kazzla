package com.kazzla.service.schema.schema
import java.sql.Connection
import java.sql.ResultSet

case class Migration(version:String) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into schema_migrations(version) values(?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setString(1, version)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Migration {
  val TableName = "schema_migrations"

  def apply(rs:ResultSet):Migration = Migration(
    version = rs.getString("version")
  )

  def select(params:(String,AnyRef)*)(f:(Migration)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Migration] = {
    var r:Option[Migration] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Migration)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from schema_migrations" +
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
