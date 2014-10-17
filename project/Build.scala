/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/

import java.io._
import java.sql._
import sbt._
import Keys._
import scala.Array

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Build
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */

object KazzlaBuild extends Build {

	lazy val updateSchema = TaskKey[Unit]("update-schema", "update database schema")

	override lazy val settings = super.settings ++ Seq(
		organization := "com.kazzla",
		version := "0.1-SNAPSHOT",
		scalaVersion := "2.10.3",
		resolvers ++= Seq(
			"Sonatype snapshots" at "http://oss.sonatype.org/content/repositories/snapshots/"	// MessagePack
		),
		scalacOptions ++= Seq("-encoding", "UTF-8", "-unchecked", "-deprecation"),
		javacOptions ++= Seq("-encoding", "UTF-8"),
		libraryDependencies ++= Seq(
			"org.slf4j"   % "slf4j-log4j12" % "1.7.6",
			"org.msgpack" % "msgpack"       % "0.6.10",
			"io.netty"    % "netty-all"     % "4.0.17.Final",
			"com.kazzla"  %% "asterisk"     % "0.1-SNAPSHOT",
			"com.github.seratch" %% "scalikejdbc" % "1.6.11",
			"com.github.scopt"   %% "scopt" % "3.2.0",
			"org.specs2"  %% "specs2"       % "2.3.10"       % "test"
		),
		retrieveManaged := true
	) ++ Seq(
		updateSchema := {
			val s:TaskStreams = streams.value
			s.log.info("Building case classes for database schema")
			Schema.createCaseClasses()
		}
	)

	lazy val root = project.in(file("."))
		.aggregate(share, node, service, client)

	lazy val share = project.in(file("share")).settings(
		name := "kazzla-share"
	)

	lazy val service = project.in(file("service")).settings(
		name := "kazzla-service"
	).dependsOn(share)

	lazy val node = project.in(file("node")).settings(
		name := "kazzla-node"
	).dependsOn(share)

	lazy val client = project.in(file("client")).settings(
		name := "kazzla-client"
	).dependsOn(share)

}

object Schema {
	val dir = new File("service/src/main/scala")
	val databaseUrl = "jdbc:mysql://localhost/kazzla_development"
	def createCaseClasses():Unit = {
		implicit val con = DriverManager.getConnection(databaseUrl, "root", "")
		try {
			con.setReadOnly(true)
			con.getMetaData.getTables(null, null, "%", null)
				.toSeq
				.filter {_("table_type") == "TABLE"}
				.map {t => t("table_schem").asInstanceOf[String] -> t("table_name").asInstanceOf[String]}
				.foreach {
				case (schema, name) =>
					create("com.kazzla.service.schema", schema, name)
			}
		} catch {
			case ex:Throwable => ex.printStackTrace()
		} finally {
			con.close()
		}
	}
	def create(prefix:String, schema:String, name:String)(implicit con:Connection):Unit = {
		val stmt = con.createStatement()
		val rs = stmt.executeQuery(s"select * from $name limit 0")
		val meta = rs.getMetaData

		val className = s"$prefix.${tableToPackageAndClass(name)}"
		val baseName = className.split("\\.").last
		val outputFile = new File(s"$dir/${className.replaceAll("\\.", "/")}.scala")
		val columns = (1 to meta.getColumnCount).map{ i =>
			Column(meta.getColumnName(i), meta.getColumnType(i), meta.isNullable(i) != ResultSetMetaData.columnNoNulls, meta.getColumnType(i))
		}

		// プライマリキーのカラムを参照
		val primaryKey = columns.find{ c => c.name == "id" }
		val rsSetter = columns.map{ c => s"${c.scalaIdentifier} = ${c.resultSet("rs")}"}
		System.out.println(s"$outputFile -> $className")
		outputFile.getParentFile.mkdirs()
		val out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "UTF-8"))
		out.println(s"package ${className.split("\\.").dropRight(1).mkString(".")}")
		(Seq("java.sql.Connection", "java.sql.ResultSet") ++ columns.map{ _.stype.imports }.flatten).distinct.sorted.foreach { c =>
			out.println(s"import $c")
		}
		out.println()
		out.println(s"case class $baseName(${columns.map{c=>s"${c.scalaIdentifier}:${c.scalaType}"}.mkString(", ")}) {")
		val insertColumns = columns.map{_.name}.mkString(", ")
		val insertValues = columns.map{_=>"?"}.mkString(", ")
		out.println(s"""
		  |  def insert()(implicit con:Connection):Unit = {
		  |    val sql = ${q(s"insert into $name($insertColumns) values($insertValues)")}
		  |    val stmt = con.prepareStatement(sql)
		  |    try {
			|      ${columns.zipWithIndex.map { case (c,i) => c.stmt("stmt", i+1)}.mkString("\n      ")}
			|      stmt.executeUpdate()
			|    } finally { stmt.close() }
		  |  }""".stripMargin)
		primaryKey.foreach { pk =>
			val cols = columns.filter{_!=pk}
			out.println(s"""
			  |  def update()(implicit con:Connection):Unit = {
			  |    val sql = ${q(s"update $name set ${cols.map{c=>s"${c.name}=?"}.mkString(", ")} where ${pk.name}=?")}
			  |    val stmt = con.prepareStatement(sql)
			  |    try {
				|      ${cols.zipWithIndex.map{ case (c,i) => c.stmt("stmt", i+1)}.mkString("\n      ") }
				|      ${pk.stmt("stmt", cols.size+1)}
				|    stmt.executeUpdate()
				|    } finally { stmt.close() }
				|  }""".stripMargin)
			out.println(s"""
				|  def delete()(implicit con:Connection):Unit = {
				|    val sql = ${q(s"delete from $name where ${pk.name}=?")}
				|    val stmt = con.prepareStatement(sql)
				|    try {
				|      ${pk.stmt("stmt", 1)}
				|      stmt.executeUpdate()
				|    } finally { stmt.close() }
				|  }""".stripMargin)
		}
		out.println(s"}")
		out.println(s"object $baseName {")
		out.println(s"  val TableName = ${q(name)}")
		out.println(s"""
		  |  def apply(rs:ResultSet):$baseName = $baseName(
		  |    ${rsSetter.mkString(",\n    ")}
		  |  )""".stripMargin)
		out.println(s"""
		  |  def select(params:(String,AnyRef)*)(f:($baseName)=>Unit)(implicit con:Connection):Unit = {
		  |    _select(params, None)(f)
		  |  }""".stripMargin)
		out.println(s"""
		  |  def single(params:(String,AnyRef)*)(implicit con:Connection):Option[$baseName] = {
		  |    var r:Option[$baseName] = None
		  |    _select(params, Some(1)){ a => r = Some(a) }
		  |    r
		  |  }""".stripMargin)
		primaryKey.foreach { pk =>
			out.println(s"""
			  |  def find(id:AnyRef)(implicit con:Connection):Option[$baseName] = {
			  |    single("${pk.name}" -> id)
			  |  }""".stripMargin)
		}
		out.println(s"""
			|  private[this] def _select(params:Seq[(String,AnyRef)], limit:Option[Int])(f:($baseName)=>Unit)(implicit con:Connection):Unit = {
			|    val sql = "select * from $name" +
			|      (if(params.isEmpty) "" else params.map{_._1+"=?"}.mkString(" where "," and ","")) +
			|      limit.map{l=>s" limit $$l"}.getOrElse("")
			|    val stmt = con.prepareStatement(sql)
			|    try {
			|      (1 to params.length).foreach{ i => stmt.setObject(i, params(i-1)._2) }
			|      val rs = stmt.executeQuery()
			|      try { while(rs.next) f(apply(rs)) } finally { rs.close() }
		  |    } finally { stmt.close() }
		  |  }""".stripMargin)
		out.println("}")
		out.close()

		rs.close()
		stmt.close()
	}
	def q(s:String) = "\"" + s.replaceAll("\"", "\\\"") + "\""
	def tableToPackageAndClass(name:String):String = name.split("_", 2) match {
		case Array(pkg, cls) => pkg.toLowerCase + "." + tableToClass(cls)
		case Array(cls) => tableToClass(cls)
	}
	def tableToClass(name:String):String = {
		val cls = name.split("_").filterNot{ _.isEmpty}.map{ str => str.substring(0,1).toUpperCase + str.substring(1) }.mkString
		if(cls.endsWith("s")){
			cls.dropRight(1)
		} else {
			cls
		}
	}
	case class Column(name:String, `type`:Int, nullable:Boolean, sqlType:Int){
		def scalaIdentifier:String = name.split("_").filterNot{_.isEmpty}.zipWithIndex.map{ a =>
			if(a._2==0) a._1.toLowerCase else { a._1.substring(0, 1).toUpperCase + a._1.substring(1).toLowerCase}
		}.mkString
		def scalaType:String = {
			if(nullable)  s"Option[${stype.scalaName}]"
			else          stype.scalaName
		}
		def resultSet(rs:String):String = {
			val rsGetter = rs + "." + stype.rsGetter + "(\"" + name + "\")"
			if(nullable && stype.readConvert.isDefined){
				if(stype.ref) {
					s"Option($rsGetter).map{_x=>${stype.readConvert.get.apply("_x")}}"
				} else {
					s"{val _$scalaIdentifier=$rsGetter;if($rs.wasNull) None else Some(${stype.readConvert.get.apply(s"_$scalaIdentifier")})}"
				}
			} else if(nullable){
				if(stype.ref) {
					s"Option($rsGetter)"
				} else {
					s"{val _$scalaIdentifier=$rsGetter;if($rs.wasNull) None else Some(_$scalaIdentifier)}"
				}
			} else if(stype.readConvert.isDefined) {
				stype.readConvert.get.apply(rsGetter)
			} else {
				rsGetter
			}
		}
		def stmt(v:String, i:Int):String = {
			def setter(opt:Boolean) = {
				val value = if(stype.writeConverter.isDefined){
					stype.writeConverter.get.apply(scalaIdentifier + (if(opt) ".get" else ""))
				} else {
					scalaIdentifier + (if(opt) ".get" else "")
				}
				s"$v.${stype.setter}($i, $value)"
			}
			if(nullable) {
				s"if($scalaIdentifier.isEmpty) $v.setNull($i, $sqlType) else ${setter(opt = true)}"
			} else setter(opt = false)
		}
		lazy val stype:Type = `type` match {
			case java.sql.Types.BIT => _Boolean
			case java.sql.Types.BOOLEAN => _Boolean
			case java.sql.Types.TINYINT => _Boolean
			case java.sql.Types.SMALLINT => _Short
			case java.sql.Types.INTEGER => _Int
			case java.sql.Types.BIGINT => _Long
			case java.sql.Types.FLOAT => _Float
			case java.sql.Types.DOUBLE => _Double
			case java.sql.Types.REAL => _Double
			case java.sql.Types.DECIMAL => _BigDecimal
			case java.sql.Types.CHAR => _String
			case java.sql.Types.NCHAR => _String
			case java.sql.Types.VARCHAR => _String
			case java.sql.Types.NVARCHAR => _String
			case java.sql.Types.LONGVARCHAR => _String
			case java.sql.Types.LONGNVARCHAR => _String
			case java.sql.Types.CLOB => _String
			case java.sql.Types.BINARY =>
				if(name == "id" || name.endsWith("_id")) _UUID else _ByteArray
			case java.sql.Types.VARBINARY => _ByteArray
			case java.sql.Types.LONGVARBINARY => _ByteArray
			case java.sql.Types.BLOB => _ByteArray
			case java.sql.Types.DATE => _Date
			case java.sql.Types.TIME => _Time
			case java.sql.Types.TIMESTAMP => _Timestamp
			case java.sql.Types.ARRAY => _Array
			case java.sql.Types.JAVA_OBJECT => _AnyRef
		}
		sealed abstract class Type(val scalaName:String, val ref:Boolean, val rsGetter:String, val setter:String, val readConvert:Option[(String)=>String] = None, val writeConverter:Option[(String)=>String] = None){
			val imports = Seq[String]()
		}
		object _Boolean extends Type("Boolean", false, "getBoolean", "setBoolean")
		object _Byte extends Type("Byte", false, "getByte", "setByte")
		object _Short extends Type("Short", false, "getShort", "setShort")
		object _Int extends Type("Int", false, "getInt", "setInt")
		object _Long extends Type("Long", false, "getLong", "setLong")
		object _Float extends Type("Float", false, "getFloat", "setFloat")
		object _Double extends Type("Double", false, "getDouble", "setDouble")
		object _String extends Type("String", true, "getString", "setString")
		object _ByteArray extends Type("Array[Byte]", true, "getBytes", "setBytes")
		object _Date extends Type("java.sql.Date", true, "getDate", "setDate")
		object _Time extends Type("java.sql.Time", true, "getTime", "setTime")
		object _Timestamp extends Type("java.sql.Timestamp", true, "getTimestamp", "setTimestamp")
		object _Array extends Type("Array[_]", true, "getArray", "setArray")
		object _AnyRef extends Type("AnyRef", true, "getObject", "setObject")
		object _UUID extends Type("UUID", true, "getBytes", "setBytes", Some({ n =>
			s"{val _b=ByteBuffer.wrap($n);new UUID(_b.getLong,_b.getLong)}"
		}), Some({ n =>
			s"ByteBuffer.allocate(16).putLong($n.getMostSignificantBits).putLong($n.getLeastSignificantBits).array()"
		})){
			override val imports = Seq("java.nio.ByteBuffer", "java.util.UUID")
		}
		object _BigDecimal extends Type("BigDecimal", true, "getBigDecimal", "setBigDecimal"){
			override val imports = Seq("java.math.BigDecimal")
		}
	}
	implicit class IResultSet(rs:ResultSet) {
		def toSeq:Seq[Map[String,_]] = {
			val s = collection.mutable.Buffer[Map[String,Any]]()
			while(rs.next()){
				val meta = rs.getMetaData
				s += (1 to meta.getColumnCount).map { i =>
					meta.getColumnName(i).toLowerCase -> rs.getObject(i)
				}.toMap
			}
			s.toSeq
		}
	}
}