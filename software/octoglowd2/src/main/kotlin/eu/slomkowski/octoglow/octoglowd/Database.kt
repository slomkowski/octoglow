package eu.slomkowski.octoglow.octoglowd


import kotlinx.coroutines.*
import mu.KLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.nio.file.Path
import java.sql.Connection
import java.sql.ResultSet
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

abstract class TimestampedTable(name: String) : Table(name) {
    val id = long("id").autoIncrement().primaryKey()
    val timestamp = datetime("created")
}

object HistoricalValues : TimestampedTable("historical_values") {
    val key = varchar("key", 50)
    val value = double("value")

    init {
        index(true, timestamp, key)
    }
}

class DatabaseLayer(databaseFile: Path) {
    companion object : KLogging() {
        private const val caseColumnName = "bucket_no"

        fun createAveragedByTimeInterval(tableName: String,
                                         fields: List<String>,
                                         startTime: LocalDateTime,
                                         interval: Duration,
                                         pastIntervals: Int,
                                         skipMostRecentRow: Boolean,
                                         discriminator: Pair<String, String?>? = null): String {
            require(tableName.isNotBlank())
            require(fields.isNotEmpty())
            require(pastIntervals >= 1)
            require(!interval.isNegative && !interval.isZero)

            val timestampCol = "created"

            val fieldExpression = fields.joinToString(", ") {
                val f = it.trim()
                require(f.isNotBlank())
                "avg($f) as $f"
            }

            val timeRanges = (0 until pastIntervals).map {
                val upperBound = startTime.minusSeconds(interval.seconds * it)
                upperBound.minus(interval) to upperBound
            }

            fun LocalDateTime.fmt() = ZonedDateTime.of(this, ZoneId.systemDefault()).toInstant().toEpochMilli()

            val rangeLimitExpr = timeRanges
                    .flatMap { it.toList() }
                    .let { "$timestampCol BETWEEN ${it.min()?.fmt()} AND ${it.max()?.fmt()}" }

            val caseExpr = timeRanges.mapIndexed { idx, (lower, upper) ->
                "WHEN $timestampCol BETWEEN ${lower.fmt()} AND ${upper.fmt()} THEN $idx"
            }.joinToString(prefix = "CASE\n",
                    separator = "\n",
                    postfix = "\nELSE -1 END")

            val skipMostRecentRowExpr = when (skipMostRecentRow) {
                true -> " AND $timestampCol < (SELECT MAX($timestampCol) FROM $tableName)"
                else -> ""
            }

            val discriminatorRowExpr = discriminator?.let { (columnName, columnValue) ->
                require(columnName.isNotBlank())
                " AND $columnName " + (columnValue?.let { "= '$it'" } ?: "IS NULL")
            } ?: ""

            return """SELECT
                |$caseExpr AS $caseColumnName,
                |$fieldExpression
                |FROM $tableName
                |WHERE $rangeLimitExpr$skipMostRecentRowExpr$discriminatorRowExpr
                |GROUP BY 1
                |ORDER BY 1 DESC""".trimMargin()
        }

        private fun <T> groupByBucketNo(rows: Iterable<Pair<Int, T>>, size: Int): List<T?> {
            require(size >= 1) { "the output list has to have size at least 1" }

            val available = rows.groupBy { it.first }
                    .mapValues {
                        check(it.value.size == 1)
                        it.value.first().second
                    }

            return (0 until size).map { available[it] }.asReversed()
        }

        fun toJodaDateTime(d: LocalDateTime): org.joda.time.DateTime {
            return org.joda.time.DateTime(d.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        }

        val sqliteNativeDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyy-MM-dd HH:mm:ss.SSSSSS")
    }

    private val threadContext = newSingleThreadContext("database")

    init {
        Database.connect("jdbc:sqlite:$databaseFile", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

        transaction {
            SchemaUtils.create(HistoricalValues)
        }
    }

    suspend fun insertHistoricalValue(ts: LocalDateTime, key: HistoricalValueType, value: Double) = coroutineScope {
        logger.debug { "Inserting data to DB: $key = $value." }
        launch(threadContext) {
            transaction {
                HistoricalValues.insert {
                    it[timestamp] = toJodaDateTime(ts)
                    it[this.key] = key.databaseSymbol
                    it[this.value] = value
                }
            }
        }
    }

    /**
     * Code from https://github.com/JetBrains/Exposed/wiki/FAQ#q-is-it-possible-to-use-native-sql--sql-as-a-string
     */
    private fun <T : Any> String.execAndMap(transform: (ResultSet) -> T): List<T> {
        val result = arrayListOf<T>()
        TransactionManager.current().exec(this) { rs ->
            while (rs.next()) {
                result += transform(rs)
            }
        }
        return result
    }

    suspend fun getLastHistoricalValuesByHour(currentTime: LocalDateTime,
                                              key: HistoricalValueType,
                                              numberOfPastHours: Int): Deferred<List<Double?>> = coroutineScope {
        val query = createAveragedByTimeInterval(HistoricalValues.tableName,
                listOf("value"), currentTime, Duration.ofHours(1), numberOfPastHours, true, "key" to key.databaseSymbol)

        async(threadContext) {
            groupByBucketNo(transaction {
                query.execAndMap { it.getInt(caseColumnName) to it.getDouble("value") }
            }, numberOfPastHours)
        }
    }
}