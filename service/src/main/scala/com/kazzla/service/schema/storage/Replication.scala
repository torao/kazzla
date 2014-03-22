package com.kazzla.service.schema.storage
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

case class Replication(blockId:UUID, nodeId:UUID) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into storage_replications(block_id, node_id) values(?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setBytes(1, ByteBuffer.allocate(16).putLong(blockId.getMostSignificantBits).putLong(blockId.getLeastSignificantBits).array())
      stmt.setBytes(2, ByteBuffer.allocate(16).putLong(nodeId.getMostSignificantBits).putLong(nodeId.getLeastSignificantBits).array())
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Replication {
  val TableName = "storage_replications"

  def apply(rs:ResultSet):Replication = Replication(
    blockId = {val _b=ByteBuffer.wrap(rs.getBytes("block_id"));new UUID(_b.getLong,_b.getLong)},
    nodeId = {val _b=ByteBuffer.wrap(rs.getBytes("node_id"));new UUID(_b.getLong,_b.getLong)}
  )

  def select(params:(String,AnyRef)*)(f:(Replication)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Replication] = {
    var r:Option[Replication] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Replication)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from storage_replications" +
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
