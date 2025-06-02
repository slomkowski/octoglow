package eu.slomkowski.octoglow.octoglowd


import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import eu.slomkowski.octoglow.octoglowd.db.SqlDelightDatabase
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import java.nio.file.Path
import java.util.*
import java.util.concurrent.Executors
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours


// "yyyy-MM-dd HH:mm:ss.SSS",
fun Instant.fmt(): String {
    val d = this.toLocalDateTime(TimeZone.currentSystemDefault())
    return String.format("%04d-%02d-%02d %02d:%02d:%02d.%03d", d.year, d.monthNumber, d.dayOfMonth, d.hour, d.minute, d.second, d.nanosecond / 1000000)
}

class DatabaseLayer(
    databaseFile: Path,
    private val coroutineExceptionHandler: CoroutineExceptionHandler
) : AutoCloseable {
    companion object {
        val logger = KotlinLogging.logger {}

        const val HISTORICAL_VALUES_TABLE_NAME = "historical_values"
        private const val CASE_COLUMN_NAME = "bucket_no"

        fun createAveragedByTimeInterval(
            tableName: String,
            fields: List<String>,
            startTime: Instant,
            interval: Duration,
            pastIntervals: Int,
            skipMostRecentRow: Boolean,
            discriminator: Pair<String, String?>? = null
        ): String {
            require(tableName.isNotBlank())
            require(fields.isNotEmpty())
            require(pastIntervals >= 1)
            require(interval.isPositive())

            val timestampCol = "created"

            val fieldExpression = fields.joinToString(", ") {
                val f = it.trim()
                require(f.isNotBlank())
                "avg($f) as $f"
            }

            val timeRanges = (0 until pastIntervals).map {
                val upperBound = startTime.minus(interval * it)
                upperBound.minus(interval) to upperBound
            }

            val rangeLimitExpr = timeRanges
                .flatMap { it.toList() }
                .let { "$timestampCol BETWEEN '${it.minOrNull()?.fmt()}' AND '${it.maxOrNull()?.fmt()}'" }

            val caseExpr = timeRanges.mapIndexed { idx, (lower, upper) ->
                "WHEN $timestampCol BETWEEN '${lower.fmt()}' AND '${upper.fmt()}' THEN $idx"
            }.joinToString(
                prefix = "CASE\n",
                separator = "\n",
                postfix = "\nELSE -1 END"
            )

            val skipMostRecentRowExpr = when (skipMostRecentRow) {
                true -> " AND $timestampCol < (SELECT MAX($timestampCol) FROM $tableName)"
                else -> ""
            }

            val discriminatorRowExpr = discriminator?.let { (columnName, columnValue) ->
                require(columnName.isNotBlank())
                " AND $columnName " + (columnValue?.let { "= '$it'" } ?: "IS NULL")
            } ?: ""

            return """SELECT
                |$caseExpr AS $CASE_COLUMN_NAME,
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
    }

    private val threadContext = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    private val driver: SqlDriver

    private val database: SqlDelightDatabase

    init {
        val jdbcString = "jdbc:sqlite:$databaseFile"

        driver = JdbcSqliteDriver(jdbcString, Properties(), SqlDelightDatabase.Schema)
        database = SqlDelightDatabase(driver)
    }

    override fun close() {
        driver.close()
    }

    fun getChangeableSettingAsync(key: ChangeableSetting): Deferred<String?> = CoroutineScope(threadContext).async {
        database.transactionWithResult {
            val row = database.changeableSettingsQueries.selectSetting(key.name).executeAsOneOrNull()
            row?.value_
        }
    }

    fun setChangeableSettingAsync(key: ChangeableSetting, value: String?): Job {
        logger.info { "Saving $key as $value." }
        return CoroutineScope(threadContext).launch(coroutineExceptionHandler) {
            database.transaction {
                val existingRow = database.changeableSettingsQueries.selectSetting(key.name).executeAsOneOrNull()

                if (existingRow != null) {
                    logger.debug { "Updating existing setting $key." }
                    database.changeableSettingsQueries.updateSetting(value, now().fmt(), key.name)
                } else {
                    logger.debug { "Inserting new setting $key." }
                    database.changeableSettingsQueries.insertSetting(value, now().fmt(), key.name)
                }
            }
        }
    }

    fun insertHistoricalValueAsync(ts: Instant, key: HistoricalValueType, value: Double): Job {
        return CoroutineScope(threadContext).launch(coroutineExceptionHandler) {
            database.transaction {
                if (database.historicalValuesQueries.selectExistingHistoricalValue(ts.fmt(), key.databaseSymbol).executeAsOneOrNull() == null) {
                    logger.debug { "Inserting data to DB: $key = $value." }
                    database.historicalValuesQueries.insertHistoricalValue(ts.fmt(), key.databaseSymbol, value)
                } else {
                    logger.debug { "Value with timestamp $ts is already in DB." }
                }
            }
        }
    }

    fun getLastHistoricalValuesByHourAsync(
        currentTime: Instant,
        key: HistoricalValueType,
        numberOfPastHours: Int
    ): Deferred<List<Double?>> {
        val query = createAveragedByTimeInterval(
            HISTORICAL_VALUES_TABLE_NAME,
            listOf("value"), currentTime, 1.hours, numberOfPastHours, true, "key" to key.databaseSymbol
        )

        // todo rewrite to fully use async and await
        return CoroutineScope(threadContext).async {
            val result = database.transactionWithResult {
                driver.executeQuery(null, query, mapper = {
                    val result = mutableListOf<Pair<Int, Double>>()

                    while (it.next().value) {
                        val bucketNo = it.getLong(0)!!
                        val value = it.getDouble(1)!!
                        result.add(bucketNo.toInt() to value)
                    }

                    app.cash.sqldelight.db.QueryResult.Value(result)
                }, 0)
            }.value

            groupByBucketNo(result, numberOfPastHours)
        }
    }
}