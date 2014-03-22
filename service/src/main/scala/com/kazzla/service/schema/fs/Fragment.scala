package com.kazzla.service.schema.fs
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

case class Fragment(inodeId:UUID, offset:Long, length:Int, blockId:UUID, blockOffset:Int) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into fs_fragments(inode_id, offset, length, block_id, block_offset) values(?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setBytes(1, ByteBuffer.allocate(16).putLong(inodeId.getMostSignificantBits).putLong(inodeId.getLeastSignificantBits).array())
      stmt.setLong(2, offset)
      stmt.setInt(3, length)
      stmt.setBytes(4, ByteBuffer.allocate(16).putLong(blockId.getMostSignificantBits).putLong(blockId.getLeastSignificantBits).array())
      stmt.setInt(5, blockOffset)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Fragment {
  val TableName = "fs_fragments"

  def apply(rs:ResultSet):Fragment = Fragment(
    inodeId = {val _b=ByteBuffer.wrap(rs.getBytes("inode_id"));new UUID(_b.getLong,_b.getLong)},
    offset = rs.getLong("offset"),
    length = rs.getInt("length"),
    blockId = {val _b=ByteBuffer.wrap(rs.getBytes("block_id"));new UUID(_b.getLong,_b.getLong)},
    blockOffset = rs.getInt("block_offset")
  )

  def select(params:(String,AnyRef)*)(f:(Fragment)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Fragment] = {
    var r:Option[Fragment] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Fragment)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from fs_fragments" +
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
