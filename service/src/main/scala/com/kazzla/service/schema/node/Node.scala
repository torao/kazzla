package com.kazzla.service.schema.node
import java.sql.Connection
import java.sql.ResultSet

case class Node(id:Int, name:Option[String], uuid:String, accountId:Option[Int], regionId:Option[Int], continent:Option[String], country:Option[String], state:Option[String], latitude:Option[Double], longitude:Option[Double], agent:Option[String], qos:Option[Double], status:Option[String], certificate:Array[Byte], disconnectedAt:Option[java.sql.Timestamp], createdAt:java.sql.Timestamp, updatedAt:java.sql.Timestamp) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into node_nodes(id, name, uuid, account_id, region_id, continent, country, state, latitude, longitude, agent, qos, status, certificate, disconnected_at, created_at, updated_at) values(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      if(name.isEmpty) stmt.setNull(2, 12) else stmt.setString(2, name.get)
      stmt.setString(3, uuid)
      if(accountId.isEmpty) stmt.setNull(4, 4) else stmt.setInt(4, accountId.get)
      if(regionId.isEmpty) stmt.setNull(5, 4) else stmt.setInt(5, regionId.get)
      if(continent.isEmpty) stmt.setNull(6, 12) else stmt.setString(6, continent.get)
      if(country.isEmpty) stmt.setNull(7, 12) else stmt.setString(7, country.get)
      if(state.isEmpty) stmt.setNull(8, 12) else stmt.setString(8, state.get)
      if(latitude.isEmpty) stmt.setNull(9, 7) else stmt.setDouble(9, latitude.get)
      if(longitude.isEmpty) stmt.setNull(10, 7) else stmt.setDouble(10, longitude.get)
      if(agent.isEmpty) stmt.setNull(11, 12) else stmt.setString(11, agent.get)
      if(qos.isEmpty) stmt.setNull(12, 7) else stmt.setDouble(12, qos.get)
      if(status.isEmpty) stmt.setNull(13, 12) else stmt.setString(13, status.get)
      stmt.setBytes(14, certificate)
      if(disconnectedAt.isEmpty) stmt.setNull(15, 93) else stmt.setTimestamp(15, disconnectedAt.get)
      stmt.setTimestamp(16, createdAt)
      stmt.setTimestamp(17, updatedAt)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update node_nodes set name=?, uuid=?, account_id=?, region_id=?, continent=?, country=?, state=?, latitude=?, longitude=?, agent=?, qos=?, status=?, certificate=?, disconnected_at=?, created_at=?, updated_at=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      if(name.isEmpty) stmt.setNull(1, 12) else stmt.setString(1, name.get)
      stmt.setString(2, uuid)
      if(accountId.isEmpty) stmt.setNull(3, 4) else stmt.setInt(3, accountId.get)
      if(regionId.isEmpty) stmt.setNull(4, 4) else stmt.setInt(4, regionId.get)
      if(continent.isEmpty) stmt.setNull(5, 12) else stmt.setString(5, continent.get)
      if(country.isEmpty) stmt.setNull(6, 12) else stmt.setString(6, country.get)
      if(state.isEmpty) stmt.setNull(7, 12) else stmt.setString(7, state.get)
      if(latitude.isEmpty) stmt.setNull(8, 7) else stmt.setDouble(8, latitude.get)
      if(longitude.isEmpty) stmt.setNull(9, 7) else stmt.setDouble(9, longitude.get)
      if(agent.isEmpty) stmt.setNull(10, 12) else stmt.setString(10, agent.get)
      if(qos.isEmpty) stmt.setNull(11, 7) else stmt.setDouble(11, qos.get)
      if(status.isEmpty) stmt.setNull(12, 12) else stmt.setString(12, status.get)
      stmt.setBytes(13, certificate)
      if(disconnectedAt.isEmpty) stmt.setNull(14, 93) else stmt.setTimestamp(14, disconnectedAt.get)
      stmt.setTimestamp(15, createdAt)
      stmt.setTimestamp(16, updatedAt)
      stmt.setInt(17, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from node_nodes where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Node {
  val TableName = "node_nodes"

  def apply(rs:ResultSet):Node = Node(
    id = rs.getInt("id"),
    name = Option(rs.getString("name")),
    uuid = rs.getString("uuid"),
    accountId = {val _accountId=rs.getInt("account_id");if(rs.wasNull) None else Some(_accountId)},
    regionId = {val _regionId=rs.getInt("region_id");if(rs.wasNull) None else Some(_regionId)},
    continent = Option(rs.getString("continent")),
    country = Option(rs.getString("country")),
    state = Option(rs.getString("state")),
    latitude = {val _latitude=rs.getDouble("latitude");if(rs.wasNull) None else Some(_latitude)},
    longitude = {val _longitude=rs.getDouble("longitude");if(rs.wasNull) None else Some(_longitude)},
    agent = Option(rs.getString("agent")),
    qos = {val _qos=rs.getDouble("qos");if(rs.wasNull) None else Some(_qos)},
    status = Option(rs.getString("status")),
    certificate = rs.getBytes("certificate"),
    disconnectedAt = Option(rs.getTimestamp("disconnected_at")),
    createdAt = rs.getTimestamp("created_at"),
    updatedAt = rs.getTimestamp("updated_at")
  )

  def select(params:(String,AnyRef)*)(f:(Node)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Node] = {
    var r:Option[Node] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Node] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Node)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from node_nodes" +
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
