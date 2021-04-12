package eu.slomkowski.octoglow.octoglowd

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import mu.KLogging
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DatabaseTest {

    companion object : KLogging()

    private fun <T : Any> createTestDbContext(func: (DatabaseLayer) -> T): T {
        val dbFile = Files.createTempFile("unit-test-", ".db")
        try {
            val db = DatabaseLayer(dbFile, CoroutineExceptionHandler { coroutineContext, throwable ->
                logger.error(throwable) { "Error within the coroutine $coroutineContext." }
            })
            assertNotNull(db)
            logger.debug { "Opened DB file $dbFile" }
            return func(db)
        } finally {
            Files.deleteIfExists(dbFile)
        }
    }

    @Test
    fun testInsertHistoricalValue() {
        createTestDbContext { db ->
            val now = ZonedDateTime.now()

            runBlocking {
                listOf(
                    now.minusDays(3) to -23.3,
                    now.minusHours(2) to 15.0,
                    now.minusHours(1) to 20.0,
                    now to 25.0
                )
                    .map { (dt, temp) -> db.insertHistoricalValueAsync(dt, OutdoorTemperature, temp) }
                    .joinAll()

                val results = db.getLastHistoricalValuesByHourAsync(now, OutdoorTemperature, 5).await()
                assertEquals(5, results.size)
                assertNull(results[0])
                assertNull(results[1])
                assertNull(results[2])
                assertEquals(15.0, results[3]!!, 0.001)
                assertEquals(20.0, results[4]!!, 0.001)
            }
        }
    }

    @Test
    fun testChangeableSettings() {
        val key = ChangeableSetting.BRIGHTNESS
        createTestDbContext { db ->
            runBlocking {
                assertNull(db.getChangeableSettingAsync(key).await())
                db.setChangeableSettingAsync(key, "5").join()
                assertEquals("5", db.getChangeableSettingAsync(key).await())
                db.setChangeableSettingAsync(key, "kek").join()
                assertEquals("kek", db.getChangeableSettingAsync(key).await())
                db.setChangeableSettingAsync(key, null).join()
                assertNull(db.getChangeableSettingAsync(key).await())
            }
        }
    }

    @Test
    fun testDateTimeFormat() {
        assertEquals(
            "2019-01-25 00:32:35.212000",
            LocalDateTime.of(2019, 1, 25, 0, 32, 35, 212_000_000).format(DatabaseLayer.sqliteNativeDateTimeFormat)
        )
    }

    @Test
    fun testCreateSqlQueryForAveragedHours() {
        val d = ZonedDateTime.of(2018, 6, 24, 16, 20, 11, 0, WARSAW_ZONE_ID)

        assertEquals(
            """SELECT
        |CASE
        |WHEN created BETWEEN 1529846411000 AND 1529850011000 THEN 0
        |WHEN created BETWEEN 1529842811000 AND 1529846411000 THEN 1
        |WHEN created BETWEEN 1529839211000 AND 1529842811000 THEN 2
        |WHEN created BETWEEN 1529835611000 AND 1529839211000 THEN 3
        |WHEN created BETWEEN 1529832011000 AND 1529835611000 THEN 4
        |WHEN created BETWEEN 1529828411000 AND 1529832011000 THEN 5
        |WHEN created BETWEEN 1529824811000 AND 1529828411000 THEN 6
        |ELSE -1 END AS bucket_no,
        |avg(temperature) as temperature, avg(humidity) as humidity, avg(pressure) as pressure
        |FROM indoor_weather_report
        |WHERE created BETWEEN 1529824811000 AND 1529850011000
        |GROUP BY 1
        |ORDER BY 1 DESC""".trimMargin(), DatabaseLayer.createAveragedByTimeInterval(
                "indoor_weather_report",
                listOf("temperature", "humidity", "pressure"), d, Duration.ofHours(1), 7, false
            )
        )

        assertEquals(
            """SELECT
        |CASE
        |WHEN created BETWEEN 1529848211000 AND 1529850011000 THEN 0
        |WHEN created BETWEEN 1529846411000 AND 1529848211000 THEN 1
        |WHEN created BETWEEN 1529844611000 AND 1529846411000 THEN 2
        |WHEN created BETWEEN 1529842811000 AND 1529844611000 THEN 3
        |WHEN created BETWEEN 1529841011000 AND 1529842811000 THEN 4
        |WHEN created BETWEEN 1529839211000 AND 1529841011000 THEN 5
        |WHEN created BETWEEN 1529837411000 AND 1529839211000 THEN 6
        |WHEN created BETWEEN 1529835611000 AND 1529837411000 THEN 7
        |WHEN created BETWEEN 1529833811000 AND 1529835611000 THEN 8
        |WHEN created BETWEEN 1529832011000 AND 1529833811000 THEN 9
        |WHEN created BETWEEN 1529830211000 AND 1529832011000 THEN 10
        |WHEN created BETWEEN 1529828411000 AND 1529830211000 THEN 11
        |WHEN created BETWEEN 1529826611000 AND 1529828411000 THEN 12
        |ELSE -1 END AS bucket_no,
        |avg(temperature) as temperature
        |FROM indoor_weather_report
        |WHERE created BETWEEN 1529826611000 AND 1529850011000 AND created < (SELECT MAX(created) FROM indoor_weather_report)
        |GROUP BY 1
        |ORDER BY 1 DESC""".trimMargin(), DatabaseLayer.createAveragedByTimeInterval(
                "indoor_weather_report",
                listOf("temperature"), d, Duration.ofMinutes(30), 13, true
            )
        )

        assertEquals(
            """SELECT
        |CASE
        |WHEN created BETWEEN 1548327600000 AND 1548329400000 THEN 0
        |WHEN created BETWEEN 1548325800000 AND 1548327600000 THEN 1
        |WHEN created BETWEEN 1548324000000 AND 1548325800000 THEN 2
        |WHEN created BETWEEN 1548322200000 AND 1548324000000 THEN 3
        |ELSE -1 END AS bucket_no,
        |avg(value) as value
        |FROM tt
        |WHERE created BETWEEN 1548322200000 AND 1548329400000 AND symbol = 'WER'
        |GROUP BY 1
        |ORDER BY 1 DESC""".trimMargin(), DatabaseLayer.createAveragedByTimeInterval(
                "tt",
                listOf("value"),
                ZonedDateTime.of(LocalDateTime.of(2019, 1, 24, 12, 30), WARSAW_ZONE_ID),
                Duration.ofMinutes(30),
                4,
                false,
                "symbol" to "WER"
            )
        )

        assertEquals(
            """SELECT
        |CASE
        |WHEN created BETWEEN 1548327600000 AND 1548329400000 THEN 0
        |WHEN created BETWEEN 1548325800000 AND 1548327600000 THEN 1
        |ELSE -1 END AS bucket_no,
        |avg(value) as value
        |FROM tt
        |WHERE created BETWEEN 1548325800000 AND 1548329400000 AND created < (SELECT MAX(created) FROM tt) AND symbol IS NULL
        |GROUP BY 1
        |ORDER BY 1 DESC""".trimMargin(), DatabaseLayer.createAveragedByTimeInterval(
                "tt",
                listOf("value"),
                ZonedDateTime.of(LocalDateTime.of(2019, 1, 24, 12, 30), WARSAW_ZONE_ID),
                Duration.ofMinutes(30),
                2,
                true,
                "symbol" to null
            )
        )
    }

    @Test
    fun testCreateSqlQueryForAveragedDays() {
        assertEquals(
            """SELECT
        |CASE
        |WHEN created BETWEEN 1606863600000 AND 1606950000000 THEN 0
        |WHEN created BETWEEN 1606777200000 AND 1606863600000 THEN 1
        |WHEN created BETWEEN 1606690800000 AND 1606777200000 THEN 2
        |WHEN created BETWEEN 1606604400000 AND 1606690800000 THEN 3
        |ELSE -1 END AS bucket_no,
        |avg(value) as value
        |FROM tt
        |WHERE created BETWEEN 1606604400000 AND 1606950000000 AND symbol = 'TEST'
        |GROUP BY 1
        |ORDER BY 1 DESC""".trimMargin(), DatabaseLayer.createAveragedByTimeInterval(
                "tt",
                listOf("value"),
                ZonedDateTime.of(LocalDate.of(2020, 12, 3).atStartOfDay(), WARSAW_ZONE_ID), //todo test other zones
                Duration.ofDays(1),
                4,
                false,
                "symbol" to "TEST"
            )
        )
    }
}