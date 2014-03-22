package com.kazzla.service.schema.node
import java.sql.Connection
import java.sql.ResultSet

case class Region(id:Int, name:String, continent:Option[String], country:Option[String], state:Option[String], createdAt:java.sql.Timestamp, updatedAt:java.sql.Timestamp) {

  def insert()(implicit con:Connection):Unit = {
    val sql = "insert into node_regions(id, name, continent, country, state, created_at, updated_at) values(?, ?, ?, ?, ?, ?, ?)"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.setString(2, name)
      if(continent.isEmpty) stmt.setNull(3, 12) else stmt.setString(3, continent.get)
      if(country.isEmpty) stmt.setNull(4, 12) else stmt.setString(4, country.get)
      if(state.isEmpty) stmt.setNull(5, 12) else stmt.setString(5, state.get)
      stmt.setTimestamp(6, createdAt)
      stmt.setTimestamp(7, updatedAt)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def update()(implicit con:Connection):Unit = {
    val sql = "update node_regions set name=?, continent=?, country=?, state=?, created_at=?, updated_at=? where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setString(1, name)
      if(continent.isEmpty) stmt.setNull(2, 12) else stmt.setString(2, continent.get)
      if(country.isEmpty) stmt.setNull(3, 12) else stmt.setString(3, country.get)
      if(state.isEmpty) stmt.setNull(4, 12) else stmt.setString(4, state.get)
      stmt.setTimestamp(5, createdAt)
      stmt.setTimestamp(6, updatedAt)
      stmt.setInt(7, id)
    stmt.executeUpdate()
    } finally { stmt.close() }
  }

  def delete()(implicit con:Connection):Unit = {
    val sql = "delete from node_regions where id=?"
    val stmt = con.prepareStatement(sql)
    try {
      stmt.setInt(1, id)
      stmt.executeUpdate()
    } finally { stmt.close() }
  }
}
object Region {
  val TableName = "node_regions"

  def apply(rs:ResultSet):Region = Region(
    id = rs.getInt("id"),
    name = rs.getString("name"),
    continent = Option(rs.getString("continent")),
    country = Option(rs.getString("country")),
    state = Option(rs.getString("state")),
    createdAt = rs.getTimestamp("created_at"),
    updatedAt = rs.getTimestamp("updated_at")
  )

  def select(params:(String,AnyRef)*)(f:(Region)=>Unit)(implicit con:Connection):Unit = {
    _select(params, None)(f)
  }

  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[Region] = {
    var r:Option[Region] = None
    _select(params, Some(1)){ a => r = Some(a) }
    r
  }

  def find(id:AnyRef)(implicit con:Connection):Option[Region] = {
    single("id" -> id)
  }

  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:(Region)=>Unit)(implicit con:Connection):Unit = {
    val sql = "select * from node_regions" +
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
