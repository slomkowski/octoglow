@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd


import app.cash.sqldelight.Query
import app.cash.sqldelight.TransacterImpl
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import app.cash.sqldelight.driver.jdbc.JdbcDriver
import eu.slomkowski.octoglow.octoglowd.db.SqlDelightDatabase
import eu.slomkowski.octoglow.octoglowd.demon.Demon
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.*
import java.util.concurrent.Executors
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


/**
 * SQLDelight's own [app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver] opens a fresh SQLite
 * connection for every transaction when the database lives in a file: its connection manager keeps a
 * connection only while a transaction is in progress and closes it as soon as the transaction ends.
 * Every query therefore started with an empty page cache and re-applied the connection pragmas.
 *
 * All database work in [DatabaseDemon] is confined to one thread, so keeping a single connection open
 * is both safe and much cheaper.
 */
private class SingleConnectionSqliteDriver(url: String, properties: Properties) : JdbcDriver() {

    private val connection: Connection = DriverManager.getConnection(url, properties)

    override fun getConnection(): Connection = connection

    override fun closeConnection(connection: Connection) {
        // deliberately left open - that is the whole point; it is closed once in close()
    }

    override fun close() {
        connection.close()
    }

    override fun addListener(vararg queryKeys: String, listener: Query.Listener) = Unit

    override fun removeListener(vararg queryKeys: String, listener: Query.Listener) = Unit

    override fun notifyListeners(vararg queryKeys: String) = Unit
}

/**
 * Creates or migrates the schema from the current `user_version`, mirroring what SQLDelight's
 * `JdbcSqliteDriver(url, properties, schema)` factory does for its own driver.
 */
private fun SqlDriver.createOrMigrateSchema(schema: SqlSchema<QueryResult.Value<Unit>>) {
    val driver = this
    object : TransacterImpl(driver) {}.transaction {
        val version = driver.executeQuery(
            null,
            "PRAGMA user_version",
            { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else null) },
            0,
        ).value ?: 0L

        when {
            version == 0L -> schema.create(driver).value
            version < schema.version -> schema.migrate(driver, version, schema.version).value
            else -> return@transaction
        }

        driver.execute(null, "PRAGMA user_version = ${schema.version}", 0)
    }
}

// "yyyy-MM-dd HH:mm:ss.SSS",
fun Instant.fmt(): String {
    val d = this.toLocalDateTime(TimeZone.currentSystemDefault())
    return String.format(Locale.ROOT, "%04d-%02d-%02d %02d:%02d:%02d.%03d", d.year, d.month.number, d.day, d.hour, d.minute, d.second, d.nanosecond / 1000000)
}

class DatabaseDemon(
    databaseFile: Path,
    private val eventBus: DataSnapshotBus,
    private val historicalValuesRetention: Duration? = null,
) : Demon {
    companion object {
        private val logger = KotlinLogging.logger {}

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

    private val threadContext = Executors.newSingleThreadExecutor { Thread(it, "Database") }.asCoroutineDispatcher()
    private val threadWorkerScope = CoroutineScope(SupervisorJob() + threadContext)

    private val driver: SqlDriver

    private val database: SqlDelightDatabase

    init {
        val jdbcString = "jdbc:sqlite:$databaseFile"

        // Without these the driver uses SQLite's defaults: rollback journal and synchronous=FULL, so
        // every write means creating a journal file, two fsyncs and deleting it again. On the SD card
        // in the Orange Pi that regularly costs hundreds of milliseconds per sample inserted.
        // WAL plus synchronous=NORMAL keeps the data safe across a process crash (only an OS-level
        // crash can lose the last transactions) at a fraction of the I/O.
        val sqliteProperties = Properties().apply {
            setProperty("journal_mode", "WAL")
            setProperty("synchronous", "NORMAL")
            setProperty("busy_timeout", "5000")
            setProperty("cache_size", "-2000") // negative means KiB rather than pages, so about 2 MB
        }

        driver = SingleConnectionSqliteDriver(jdbcString, sqliteProperties)
        driver.createOrMigrateSchema(SqlDelightDatabase.Schema)
        database = SqlDelightDatabase(driver)
    }

    override fun close(scope: CoroutineScope) {
        threadWorkerScope.cancel()
        threadContext.close()
        driver.close()
    }

    fun getChangeableSettingAsync(key: ChangeableSetting): Deferred<String?> = threadWorkerScope.async {
        database.transactionWithResult {
            val row = database.changeableSettingsQueries.selectSetting(key.name).executeAsOneOrNull()
            row?.value_
        }
    }

    fun setChangeableSettingAsync(key: ChangeableSetting, value: String?): Job {
        return threadWorkerScope.launch {
            logger.info { "Saving $key as $value." }

            database.transaction {
                val existingRow = database.changeableSettingsQueries.selectSetting(key.name).executeAsOneOrNull()
                val now = Clock.System.now()

                if (existingRow != null) {
                    logger.debug { "Updating existing setting $key." }
                    database.changeableSettingsQueries.updateSetting(value, now.fmt(), key.name)
                } else {
                    logger.debug { "Inserting new setting $key." }
                    database.changeableSettingsQueries.insertSetting(value, now.fmt(), key.name)
                }
            }
        }
    }

    fun insertHistoricalValueAsync(ts: Instant, key: DbDataSampleType, value: Double): Job =
        insertHistoricalValuesAsync(ts, listOf(key to value))

    /**
     * Stores every sample of one snapshot in a single transaction. Each transaction is its own commit
     * on the SD card, and a local sensor cycle publishes eight samples at once, so doing them
     * separately multiplied the write cost by eight for no benefit.
     */
    fun insertHistoricalValuesAsync(ts: Instant, values: List<Pair<DbDataSampleType, Double>>): Job {
        return threadWorkerScope.launch {
            if (values.isEmpty()) {
                return@launch
            }

            val timestamp = ts.fmt()
            database.transaction {
                for ((key, value) in values) {
                    logger.debug { "Inserting data to DB: $key = ${"%.4f".format(Locale.ROOT, value)}." }
                    // the statement is INSERT OR IGNORE, so the unique (created, key) index does the
                    // duplicate check that used to need a SELECT of its own
                    database.historicalValuesQueries.insertHistoricalValue(timestamp, key.databaseSymbol, value)
                }
            }
        }
    }

    fun deleteHistoricalValuesOlderThanAsync(cutoff: Instant): Job = threadWorkerScope.launch {
        logger.info { "Deleting historical values older than $cutoff." }
        database.historicalValuesQueries.deleteHistoricalValuesOlderThan(cutoff.fmt())
    }

    fun getLastHistoricalValuesByHourAsync(
        currentTime: Instant,
        key: DbDataSampleType,
        numberOfPastHours: Int
    ): Deferred<List<Double?>> {
        val query = createAveragedByTimeInterval(
            HISTORICAL_VALUES_TABLE_NAME,
            listOf("value"), currentTime, 1.hours, numberOfPastHours, true, "key" to key.databaseSymbol
        )

        // TODO("dodać maxTimestamp, żeby ogarniczyć ewentualny wynik nowowstawiowny")

        return threadWorkerScope.async {
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

    override fun createJobs(scope: CoroutineScope): List<Job> = listOfNotNull(
        scope.launch {
            eventBus.snapshots.collect { packet ->
                if (packet !is DataSnapshot) {
                    return@collect
                }

                val storableValues = packet.values.mapNotNull { savableData ->
                    val type = savableData.type as? DbDataSampleType ?: return@mapNotNull null
                    savableData.value.getOrNull()?.let { type to it }
                }

                insertHistoricalValuesAsync(packet.timestamp, storableValues)
            }
        },

        // Opt-in: with no retention configured the history is kept forever, which is what the daemon
        // has always done. The views never look back further than about a day, so setting this only
        // bounds a table that otherwise grows for the lifetime of the device.
        historicalValuesRetention?.let { retention ->
            scope.launch {
                while (isActive) {
                    deleteHistoricalValuesOlderThanAsync(Clock.System.now() - retention).join()
                    delay(1.days)
                }
            }
        },
    )
}