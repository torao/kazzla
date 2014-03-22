package com.kazzla.service.schema.auth
import java.nio.ByteBuffer
import java.sql.Connection
import java.sql.ResultSet
import java.util.UUID

case class PublicKey(id:Int, accountId:UUID, algorithm:String, publicKey:Array[Byte], publicKeyMd5:Array[Byte], createdAt:java.sql.Timestamp, updatedAt:java.sql.Timestamp) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into auth_public_keys(id, account_id, algorithm, public_key, public_key_md5, created_at, updated_at) values(?, ?, ?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.setBytes(2, ByteBuffer.allocate(16).putLong(accountId.getMostSignificantBits).putLong(accountId.getLeastSignificantBits).array())
      stmt.setString(3, algorithm)
      stmt.setBytes(4, publicKey)
      stmt.setBytes(5, publicKeyMd5)
      stmt.setTimestamp(6, createdAt)
      stmt.setTimestamp(7, updatedAt)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update auth_public_keys set account_id=?, algorithm=?, public_key=?, public_key_md5=?, created_at=?, updated_at=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setBytes(1, ByteBuffer.allocate(16).putLong(accountId.getMostSignificantBits).putLong(accountId.getLeastSignificantBits).array())
      stmt.setString(2, algorithm)
      stmt.setBytes(3, publicKey)
      stmt.setBytes(4, publicKeyMd5)
      stmt.setTimestamp(5, createdAt)
      stmt.setTimestamp(6, updatedAt)
      stmt.setInt(7, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from auth_public_keys where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object PublicKey {
  val TableName = "auth_public_keys"

  def apply(rs:ResultSet):PublicKey = PublicKey(
    id = rs.getInt("id"),
    accountId = {val _b=ByteBuffer.wrap(rs.getBytes("account_id"));new UUID(_b.getLong,_b.getLong)},
    algorithm = rs.getString("algorithm"),
    publicKey = rs.getBytes("public_key"),
    publicKeyMd5 = rs.getBytes("public_key_md5"),
    createdAt = rs.getTimestamp("created_at"),
    updatedAt = rs.getTimestamp("updated_at")
  )

  def select(params:(String,AnyRef)*)(f:(PublicKey)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[PublicKey] = {
    var r:Option[PublicKey] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[PublicKey] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(PublicKey)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from auth_public_keys" +
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
