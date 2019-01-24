package eu.slomkowski.octoglow


import eu.slomkowski.octoglow.hardware.OutdoorWeatherReport
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
import java.time.format.DateTimeFormatter


object OutdoorWeatherReports : Table("outdoor_weather_report") {
    val id = long("id").autoIncrement().primaryKey()
    val timestamp = datetime("created")
    val temperature = double("temperature")
    val humidity = double("humidity")
    val batteryIsWeak = bool("weak_battery")
}

object IndoorWeatherReports : Table("indoor_weather_report") {
    val id = long("id").autoIncrement().primaryKey()
    val timestamp = datetime("created")
    val temperature = double("temperature")
    val humidity = double("humidity")
    val pressure = double("pressure")
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
                                         skipMostRecentRow: Boolean): String {
            require(tableName.isNotBlank())
            require(fields.isNotEmpty())
            require(pastIntervals >= 1)
            require(!interval.isNegative && !interval.isZero)

            val timestampCol = "created"
            val fmt = DateTimeFormatter.ISO_LOCAL_DATE_TIME

            val fieldExpression = fields.joinToString(", ") {
                val f = it.trim()
                require(f.isNotBlank())
                "avg($f) as $f"
            }

            val timeRanges = (0 until pastIntervals).map {
                val upperBound = startTime.minusSeconds(interval.seconds * it)
                upperBound.minus(interval) to upperBound
            }

            val rangeLimitExpr = timeRanges
                    .flatMap { it.toList() }
                    .let { "$timestampCol BETWEEN '${it.min()?.format(fmt)}' AND '${it.max()?.format(fmt)}'" }

            val caseExpr = timeRanges.mapIndexed { idx, (lower, upper) ->
                "WHEN $timestampCol BETWEEN '${lower.format(fmt)}' AND '${upper.format(fmt)}' THEN $idx"
            }.joinToString(prefix = "CASE\n",
                    separator = "\n",
                    postfix = "\nELSE -1 END")

            val skipMostRecentRowExpr = when (skipMostRecentRow) {
                true -> " AND $timestampCol < (SELECT MAX($timestampCol) FROM $tableName)"
                else -> ""
            }

            return """SELECT
                |$caseExpr AS $caseColumnName,
                |$fieldExpression
                |FROM $tableName
                |WHERE $rangeLimitExpr$skipMostRecentRowExpr
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
    }

    init {
        Database.connect("jdbc:sqlite:$databaseFile", "org.sqlite.JDBC")
        TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE

        transaction {
            SchemaUtils.create(IndoorWeatherReports, OutdoorWeatherReports)
        }
    }

    // todo naprawić wstawianie, by wstawiało datę z odpowiednim formatem
    fun insertOutdoorWeatherReport(ts: LocalDateTime, owr: OutdoorWeatherReport) {
        logger.debug { "Inserting data to DB: $owr" }
        transaction {
            OutdoorWeatherReports.insert {
                it[timestamp] = toJodaDateTime(ts)
                it[temperature] = owr.temperature
                it[humidity] = owr.humidity
                it[batteryIsWeak] = owr.batteryIsWeak
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

    fun getLastOutdoorWeatherReportsByHour(currentTime: LocalDateTime, numberOfPastHours: Int): List<OutdoorWeatherReportRow?> {
        val query = createAveragedByTimeInterval(OutdoorWeatherReports.tableName,
                listOf("temperature", "humidity"), currentTime, Duration.ofHours(1), numberOfPastHours, true)

        return groupByBucketNo(transaction {
            query.execAndMap { it.getInt(caseColumnName) to OutdoorWeatherReportRow(it.getDouble("temperature"), it.getDouble("humidity")) }
        }, numberOfPastHours)
    }


}