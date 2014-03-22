package com.kazzla.service.schema.storage
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

case class Block(id:UUID, accountId:Int, partitionId:Boolean, size:Int, nodeId:UUID, minReplications:Boolean) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into storage_blocks(id, account_id, partition_id, size, node_id, min_replications) values(?, ?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setBytes(1, ByteBuffer.allocate(16).putLong(id.getMostSignificantBits).putLong(id.getLeastSignificantBits).array())
      stmt.setInt(2, accountId)
      stmt.setBoolean(3, partitionId)
      stmt.setInt(4, size)
      stmt.setBytes(5, ByteBuffer.allocate(16).putLong(nodeId.getMostSignificantBits).putLong(nodeId.getLeastSignificantBits).array())
      stmt.setBoolean(6, minReplications)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update storage_blocks set account_id=?, partition_id=?, size=?, node_id=?, min_replications=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, accountId)
      stmt.setBoolean(2, partitionId)
      stmt.setInt(3, size)
      stmt.setBytes(4, ByteBuffer.allocate(16).putLong(nodeId.getMostSignificantBits).putLong(nodeId.getLeastSignificantBits).array())
      stmt.setBoolean(5, minReplications)
      stmt.setBytes(6, ByteBuffer.allocate(16).putLong(id.getMostSignificantBits).putLong(id.getLeastSignificantBits).array())
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from storage_blocks where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setBytes(1, ByteBuffer.allocate(16).putLong(id.getMostSignificantBits).putLong(id.getLeastSignificantBits).array())
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Block {
  val TableName = "storage_blocks"

  def apply(rs:ResultSet):Block = Block(
    id = {val _b=ByteBuffer.wrap(rs.getBytes("id"));new UUID(_b.getLong,_b.getLong)},
    accountId = rs.getInt("account_id"),
    partitionId = rs.getBoolean("partition_id"),
    size = rs.getInt("size"),
    nodeId = {val _b=ByteBuffer.wrap(rs.getBytes("node_id"));new UUID(_b.getLong,_b.getLong)},
    minReplications = rs.getBoolean("min_replications")
  )

  def select(params:(String,AnyRef)*)(f:(Block)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Block] = {
    var r:Option[Block] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Block] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Block)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from storage_blocks" +
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
