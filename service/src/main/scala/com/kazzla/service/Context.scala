/*
 * Copyright (c) 2014 koiroha.org.
 * All sources and related resources are available under Apache License 2.0.
 * http://www.apache.org/licenses/LICENSE-2.0.html
*/
package com.kazzla.service

import com.kazzla._
import com.kazzla.asterisk.using
import java.nio.ByteBuffer
import java.sql.{PreparedStatement, ResultSet, Connection}
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource
import scala.collection.mutable
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag

// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
// Context
// ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
/**
 * @author Takami Torao
 */
class Context(val dataSource:DataSource, implicit val threadPool:ExecutionContext) {

	// ==============================================================================================
	// UUID の生成
	// ==============================================================================================
	/**
	 * 新規の UUID を生成します。
	 */
	def newInodeId:UUID = UUID.randomUUID()
	def newSessionId:UUID = UUID.randomUUID()
	def newLockId:UUID = UUID.randomUUID()

	def invalidate():Unit = {
		db.invalidate()
	}

	// ==============================================================================================
	// ==============================================================================================
	/**
	  */
	object db {

		private[this] val closed = new AtomicBoolean(false)
		private[Context] def invalidate():Unit = if(closed.compareAndSet(false, true)){

		}

		/**
		 * 処理中のデータベース接続を保持するためのスレッドローカル。
		 */
		private[this] val transactions = new ThreadLocal[Connection]()

		private[this] def get:Option[Connection] = if(! closed.get()) {
			Option(transactions.get())
		} else {
			throw new IllegalStateException("invalid context")
		}

		def _separatedTrx[T](f:(Connection)=>T):T = if(! closed.get()) {
			using(dataSource.getConnection) { con =>
				con.setAutoCommit(false)
				con.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED)
				val old = transactions.get()
				transactions.set(con)
				try {
					val result = f(con)
					con.commit()
					result
				} catch {
					case ex:Throwable =>
						con.rollback()
						throw ex
				} finally {
					transactions.set(old)
				}
			}
		} else {
			throw new IllegalStateException("invalid context")
		}

		def separatedTrx[T](f: =>T):T = _separatedTrx { _ => f }

		def _trx[T](f:(Connection)=>T):T = get match {
			case Some(con) => f(con)
			case None => _separatedTrx(f)
		}

		def trx[T](f: =>T):T = _trx { _ => f }

		def commit():Unit = get match {
			case Some(con) =>
				con.commit()
				transactions.remove()
			case None => None
		}

		def rollback():Unit = get match {
			case Some(con) =>
				con.rollback()
				transactions.remove()
			case None => None
		}

		def single[T](sql:String, args:Any*)(mapper:(ResultSet)=>T):Option[T] = _trx { con =>
			using(con.prepareStatement(s"select $sql")){ stmt =>
				init(stmt, args)
				using(stmt.executeQuery()){ rs =>
					if(rs.next()){
						Some(mapper(rs))
					} else {
						None
					}
				}
			}
		}

		def singleObject[T](sql:String, args:Any*)(ct:ClassTag[T]):Option[T] = _trx { con =>
			using(con.prepareStatement(s"select $sql")){ stmt =>
				init(stmt, args)
				var result:Option[T] = None
				using(stmt.executeQuery()){ rs => Context.map(rs, ct){ r =>
					result = Some(r)
					false
				} }
				result
			}
		}

		def scalar[T](sql:String, args:Any*):Long = _trx { con =>
			using(con.prepareStatement(s"select $sql")){ stmt =>
				init(stmt, args)
				using(stmt.executeQuery()) { rs =>
					rs.next()
					rs.getLong(1)
				}
			}
		}

		def selectObject[T](sql:String, args:Any*)(f:(T)=>Boolean)(ct:ClassTag[T]):Int = {
			select(sql, args:_*){ rs => Context.map(rs, ct)(f) }.size
		}

		def select[T](sql:String, args:Any*)(mapper:(ResultSet)=>T):Traversable[T] = _trx { con =>
			using(con.prepareStatement(s"select $sql")){ stmt =>
				init(stmt, args)
				using(stmt.executeQuery()){ rs =>
					val buffer = mutable.Buffer[T]()
					while(rs.next()){
						buffer += mapper(rs)
					}
					buffer.toTraversable
				}
			}
		}

		def update(sql:String, args:Any*):Int = _trx { con =>
			using(con.prepareStatement(s"update $sql")){ stmt =>
				init(stmt, args)
				stmt.executeUpdate()
			}
		}

		def insertInto(table:String, columns:(String,Any)*):Int = {
			def nil(a:Any) = a == null || a == None
			val cols = columns.map{ _._1 }
			val values = columns.map{ _._2 }
			val sql = cols.mkString(s"insert into $table(", ",", ")") +
				values.map{ a => if(nil(a)) "null" else "?" }.mkString(" values(", ",", ")")
			_trx { con =>
				using(con.prepareStatement(sql)){ stmt =>
					init(stmt, values.filterNot(nil).filterNot(nil))
					stmt.executeUpdate()
				}
			}
		}

		def deleteFrom(sql:String, args:Any*):Int = _trx { con =>
			using(con.prepareStatement(s"delete from $sql")){ stmt =>
				init(stmt, args)
				stmt.executeUpdate()
			}
		}

		private[this] def init(stmt:PreparedStatement, args:Seq[Any]):Unit = {
			(1 to args.length).foreach{ i =>
				val value = args(i - 1) match {
					case u:UUID => u.toByteArray
					case x => x
				}
				stmt.setObject(i, value)
			}
		}
	}

}

object Context {
	private[Context] def map[T](rs:ResultSet, ct:ClassTag[T])(f:(T)=>Boolean):Int = {
		val meta = rs.getMetaData
		val columnNames = (1 to meta.getColumnCount).map{ i =>
			val name = meta.getColumnName(i)
			val expected = name.replaceAll("_", "").toLowerCase
			expected -> name
		}.toMap
		val constructors = ct.runtimeClass.getConstructors.filter { c =>
			columnNames.size == c.getParameterCount &&
				! c.getParameters.map { _.getName.toLowerCase }.exists{ name => ! columnNames.contains(name) }
		}
		if(constructors.isEmpty) {
			// TODO 適切なコンストラクタが定義されていない
		} else if(constructors.size > 1){
			// TODO 複数のコンストラクタと一致した
		}
		val c = constructors.head
		var count = 0
		while(rs.next()){
			val params = c.getParameters.map{ p =>
				val column = columnNames(p.getName.toLowerCase)
				if(p.getType == classOf[String])      rs.getString(column)
				else if(p.getType == classOf[Byte])   rs.getByte(column)
				else if(p.getType == classOf[Short])  rs.getShort(column)
				else if(p.getType == classOf[Int])    rs.getInt(column)
				else if(p.getType == classOf[Long])   rs.getLong(column)
				else if(p.getType == classOf[Double]) rs.getDouble(column)
				else if(p.getType == classOf[Float])  rs.getFloat(column)
				else if(p.getType == classOf[UUID]){
					val b = ByteBuffer.wrap(rs.getBytes(column))
					new UUID(b.getLong, b.getLong)
				} else {
					// TODO 型変換出来ない
					null
				}
			}.map{ _.asInstanceOf[Object] }.toArray
			count += 1
			if(! f(c.newInstance(params:_*).asInstanceOf[T])){
				return count
			}
		}
		count
	}

}