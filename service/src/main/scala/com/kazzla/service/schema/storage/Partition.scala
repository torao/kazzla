package com.kazzla.service.schema.storage
import java.sql.Connection
import java.sql.ResultSet

case class Partition(id:Boolean, accountId:Int, label:String, fstype:Int) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into storage_partitions(id, account_id, label, fstype) values(?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setBoolean(1, id)
      stmt.setInt(2, accountId)
      stmt.setString(3, label)
      stmt.setInt(4, fstype)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update storage_partitions set account_id=?, label=?, fstype=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, accountId)
      stmt.setString(2, label)
      stmt.setInt(3, fstype)
      stmt.setBoolean(4, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from storage_partitions where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setBoolean(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Partition {
  val TableName = "storage_partitions"

  def apply(rs:ResultSet):Partition = Partition(
    id = rs.getBoolean("id"),
    accountId = rs.getInt("account_id"),
    label = rs.getString("label"),
    fstype = rs.getInt("fstype")
  )

  def select(params:(String,AnyRef)*)(f:(Partition)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Partition] = {
    var r:Option[Partition] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Partition] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Partition)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from storage_partitions" +
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
