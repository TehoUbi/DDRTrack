package com.tehoubi.ddrtracker.server.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import javax.sql.DataSource

/**
 * A small layer over JDBC. The queries that matter here (ranking with
 * window functions, DISTINCT ON for personal bests) are clearest as plain
 * SQL, so there is no ORM.
 */
class Database(val dataSource: DataSource) {

    /** Runs [block] in a transaction on the IO dispatcher. */
    suspend fun <T> transaction(block: Connection.() -> T): T = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.block().also { conn.commit() }
            } catch (e: Throwable) {
                conn.rollback()
                throw e
            }
        }
    }

    fun migrate() {
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate()
    }

    companion object {
        fun connect(url: String, user: String, password: String, poolSize: Int = 10): Database {
            val config = HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                maximumPoolSize = poolSize
                poolName = "ddr-leaderboard"
            }
            return Database(HikariDataSource(config))
        }
    }
}

fun Connection.prepare(sql: String, vararg params: Any?): PreparedStatement =
    prepareStatement(sql).apply {
        params.forEachIndexed { i, p ->
            when (p) {
                is java.time.Instant -> setObject(i + 1, java.time.OffsetDateTime.ofInstant(p, java.time.ZoneOffset.UTC))
                else -> setObject(i + 1, p)
            }
        }
    }

fun <T> Connection.query(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> =
    prepare(sql, *params).use { st ->
        st.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } }
    }

fun Connection.update(sql: String, vararg params: Any?): Int = prepare(sql, *params).use { it.executeUpdate() }

fun ResultSet.instant(column: String): java.time.Instant? =
    getObject(column, java.time.OffsetDateTime::class.java)?.toInstant()
