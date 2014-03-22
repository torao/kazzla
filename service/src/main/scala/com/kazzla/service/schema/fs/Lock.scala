package com.kazzla.service.schema.fs
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

case class Lock(id:UUID, inodeId:UUID, sessionId:UUID, exclusive:Boolean, createdAt:Long) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into fs_locks(id, inode_id, session_id, exclusive, created_at) values(?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setBytes(1, ByteBuffer.allocate(16).putLong(id.getMostSignificantBits).putLong(id.getLeastSignificantBits).array())
      stmt.setBytes(2, ByteBuffer.allocate(16).putLong(inodeId.getMostSignificantBits).putLong(inodeId.getLeastSignificantBits).array())
      stmt.setBytes(3, ByteBuffer.allocate(16).putLong(sessionId.getMostSignificantBits).putLong(sessionId.getLeastSignificantBits).array())
      stmt.setBoolean(4, exclusive)
      stmt.setLong(5, createdAt)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update fs_locks set inode_id=?, session_id=?, exclusive=?, created_at=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setBytes(1, ByteBuffer.allocate(16).putLong(inodeId.getMostSignificantBits).putLong(inodeId.getLeastSignificantBits).array())
      stmt.setBytes(2, ByteBuffer.allocate(16).putLong(sessionId.getMostSignificantBits).putLong(sessionId.getLeastSignificantBits).array())
      stmt.setBoolean(3, exclusive)
      stmt.setLong(4, createdAt)
      stmt.setBytes(5, ByteBuffer.allocate(16).putLong(id.getMostSignificantBits).putLong(id.getLeastSignificantBits).array())
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from fs_locks where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setBytes(1, ByteBuffer.allocate(16).putLong(id.getMostSignificantBits).putLong(id.getLeastSignificantBits).array())
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Lock {
  val TableName = "fs_locks"

  def apply(rs:ResultSet):Lock = Lock(
    id = {val _b=ByteBuffer.wrap(rs.getBytes("id"));new UUID(_b.getLong,_b.getLong)},
    inodeId = {val _b=ByteBuffer.wrap(rs.getBytes("inode_id"));new UUID(_b.getLong,_b.getLong)},
    sessionId = {val _b=ByteBuffer.wrap(rs.getBytes("session_id"));new UUID(_b.getLong,_b.getLong)},
    exclusive = rs.getBoolean("exclusive"),
    createdAt = rs.getLong("created_at")
  )

  def select(params:(String,AnyRef)*)(f:(Lock)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Lock] = {
    var r:Option[Lock] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Lock] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Lock)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from fs_locks" +
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
