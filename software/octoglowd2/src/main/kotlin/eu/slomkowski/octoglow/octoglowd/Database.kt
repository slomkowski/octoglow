package eu.slomkowski.octoglow.octoglowd


import eu.slomkowski.octoglow.octoglowd.hardware.OutdoorWeatherReport
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

object OutdoorWeatherReports : TimestampedTable("outdoor_weather_report") {
    val temperature = double("temperature")
    val humidity = double("humidity")
    val batteryIsWeak = bool("weak_battery")
}

object IndoorWeatherReports : TimestampedTable("indoor_weather_report") {
    val temperature = double("temperature")
    val humidity = double("humidity")
    val pressure = double("pressure")
}

object CryptocurrencyValues : TimestampedTable("cryptocurrency_value") {
    val symbol = varchar("symbol", 10)
    val valueInDollars = double("value_in_dollars")
}

data class OutdoorWeatherReportRow(
        val temperature: Double,
        val humidity: Double) {
    init {
        require(temperature in -40.0..60.0)
        require(humidity in 0.0..100.0)
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
            SchemaUtils.create(IndoorWeatherReports, OutdoorWeatherReports, CryptocurrencyValues)
        }
    }

    suspend fun insertCryptocurrencyValue(ts: LocalDateTime, symbol: String, value: Double) = coroutineScope {
        logger.debug { "Inserting data to DB: $symbol = \$ $value." }
        launch(threadContext) {
            transaction {
                CryptocurrencyValues.insert {
                    it[timestamp] = toJodaDateTime(ts)
                    it[this.symbol] = symbol
                    it[valueInDollars] = value
                }
            }
        }
    }

    suspend fun insertOutdoorWeatherReport(ts: LocalDateTime, owr: OutdoorWeatherReport) = coroutineScope {
        logger.debug { "Inserting data to DB: $owr" }
        launch(threadContext) {
            transaction {
                OutdoorWeatherReports.insert {
                    it[timestamp] = toJodaDateTime(ts)
                    it[temperature] = owr.temperature
                    it[humidity] = owr.humidity
                    it[batteryIsWeak] = owr.batteryIsWeak
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

    suspend fun getLastOutdoorWeatherReportsByHour(currentTime: LocalDateTime, numberOfPastHours: Int): Deferred<List<OutdoorWeatherReportRow?>> = coroutineScope {
        val query = createAveragedByTimeInterval(OutdoorWeatherReports.tableName,
                listOf("temperature", "humidity"), currentTime, Duration.ofHours(1), numberOfPastHours, true)

        async(threadContext) {
            groupByBucketNo(transaction {
                query.execAndMap { it.getInt(caseColumnName) to OutdoorWeatherReportRow(it.getDouble("temperature"), it.getDouble("humidity")) }
            }, numberOfPastHours)
        }
    }

    suspend fun getLastCryptoCurrencyValuesByHour(currentTime: LocalDateTime, symbol: String, numberOfPastHours: Int): Deferred<List<Double?>> = coroutineScope {
        val query = createAveragedByTimeInterval(CryptocurrencyValues.tableName,
                listOf("value_in_dollars"), currentTime, Duration.ofHours(1), numberOfPastHours, true, "symbol" to symbol)

        async(threadContext) {
            groupByBucketNo(transaction {
                query.execAndMap { it.getInt(caseColumnName) to it.getDouble("value_in_dollars") }
            }, numberOfPastHours)
        }
    }
}