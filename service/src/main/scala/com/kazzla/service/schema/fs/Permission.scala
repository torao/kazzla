package com.kazzla.service.schema.fs
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

case class Permission(inodeId:UUID, accountId:UUID, permission:Boolean) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into fs_permissions(inode_id, account_id, permission) values(?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setBytes(1, ByteBuffer.allocate(16).putLong(inodeId.getMostSignificantBits).putLong(inodeId.getLeastSignificantBits).array())
      stmt.setBytes(2, ByteBuffer.allocate(16).putLong(accountId.getMostSignificantBits).putLong(accountId.getLeastSignificantBits).array())
      stmt.setBoolean(3, permission)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Permission {
  val TableName = "fs_permissions"

  def apply(rs:ResultSet):Permission = Permission(
    inodeId = {val _b=ByteBuffer.wrap(rs.getBytes("inode_id"));new UUID(_b.getLong,_b.getLong)},
    accountId = {val _b=ByteBuffer.wrap(rs.getBytes("account_id"));new UUID(_b.getLong,_b.getLong)},
    permission = rs.getBoolean("permission")
  )

  def select(params:(String,AnyRef)*)(f:(Permission)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Permission] = {
    var r:Option[Permission] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Permission)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from fs_permissions" +
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
