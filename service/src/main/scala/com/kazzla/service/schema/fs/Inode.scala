package com.kazzla.service.schema.fs
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

case class Inode(id:UUID, accountId:UUID, parentId:Option[UUID], ftype:String, name:String, size:Long, permission:Option[Boolean], ctime:Long, utime:Long, atime:Long, lockSession:Option[Int], lockTimestamp:Option[Long], lockShared:Int) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into fs_inodes(id, account_id, parent_id, ftype, name, size, permission, ctime, utime, atime, lock_session, lock_timestamp, lock_shared) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setBytes(1, ByteBuffer.allocate(16).putLong(id.getMostSignificantBits).putLong(id.getLeastSignificantBits).array())
      stmt.setBytes(2, ByteBuffer.allocate(16).putLong(accountId.getMostSignificantBits).putLong(accountId.getLeastSignificantBits).array())
      if(parentId.isEmpty) stmt.setNull(3, -2) else stmt.setBytes(3, ByteBuffer.allocate(16).putLong(parentId.get.getMostSignificantBits).putLong(parentId.get.getLeastSignificantBits).array())
      stmt.setString(4, ftype)
      stmt.setString(5, name)
      stmt.setLong(6, size)
      if(permission.isEmpty) stmt.setNull(7, -6) else stmt.setBoolean(7, permission.get)
      stmt.setLong(8, ctime)
      stmt.setLong(9, utime)
      stmt.setLong(10, atime)
      if(lockSession.isEmpty) stmt.setNull(11, 4) else stmt.setInt(11, lockSession.get)
      if(lockTimestamp.isEmpty) stmt.setNull(12, -5) else stmt.setLong(12, lockTimestamp.get)
      stmt.setInt(13, lockShared)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update fs_inodes set account_id=?, parent_id=?, ftype=?, name=?, size=?, permission=?, ctime=?, utime=?, atime=?, lock_session=?, lock_timestamp=?, lock_shared=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setBytes(1, ByteBuffer.allocate(16).putLong(accountId.getMostSignificantBits).putLong(accountId.getLeastSignificantBits).array())
      if(parentId.isEmpty) stmt.setNull(2, -2) else stmt.setBytes(2, ByteBuffer.allocate(16).putLong(parentId.get.getMostSignificantBits).putLong(parentId.get.getLeastSignificantBits).array())
      stmt.setString(3, ftype)
      stmt.setString(4, name)
      stmt.setLong(5, size)
      if(permission.isEmpty) stmt.setNull(6, -6) else stmt.setBoolean(6, permission.get)
      stmt.setLong(7, ctime)
      stmt.setLong(8, utime)
      stmt.setLong(9, atime)
      if(lockSession.isEmpty) stmt.setNull(10, 4) else stmt.setInt(10, lockSession.get)
      if(lockTimestamp.isEmpty) stmt.setNull(11, -5) else stmt.setLong(11, lockTimestamp.get)
      stmt.setInt(12, lockShared)
      stmt.setBytes(13, ByteBuffer.allocate(16).putLong(id.getMostSignificantBits).putLong(id.getLeastSignificantBits).array())
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from fs_inodes where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setBytes(1, ByteBuffer.allocate(16).putLong(id.getMostSignificantBits).putLong(id.getLeastSignificantBits).array())
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Inode {
  val TableName = "fs_inodes"

  def apply(rs:ResultSet):Inode = Inode(
    id = {val _b=ByteBuffer.wrap(rs.getBytes("id"));new UUID(_b.getLong,_b.getLong)},
    accountId = {val _b=ByteBuffer.wrap(rs.getBytes("account_id"));new UUID(_b.getLong,_b.getLong)},
    parentId = Option(rs.getBytes("parent_id")).map{_x=>{val _b=ByteBuffer.wrap(_x);new UUID(_b.getLong,_b.getLong)}},
    ftype = rs.getString("ftype"),
    name = rs.getString("name"),
    size = rs.getLong("size"),
    permission = {val _permission=rs.getBoolean("permission");if(rs.wasNull) None else Some(_permission)},
    ctime = rs.getLong("ctime"),
    utime = rs.getLong("utime"),
    atime = rs.getLong("atime"),
    lockSession = {val _lockSession=rs.getInt("lock_session");if(rs.wasNull) None else Some(_lockSession)},
    lockTimestamp = {val _lockTimestamp=rs.getLong("lock_timestamp");if(rs.wasNull) None else Some(_lockTimestamp)},
    lockShared = rs.getInt("lock_shared")
  )

  def select(params:(String,AnyRef)*)(f:(Inode)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Inode] = {
    var r:Option[Inode] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Inode] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Inode)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from fs_inodes" +
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
