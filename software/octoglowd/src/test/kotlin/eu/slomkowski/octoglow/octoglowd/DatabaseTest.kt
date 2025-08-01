package eu.slomkowski.octoglow.octoglowd

import io.github.oshai.kotlinlogging.KotlinLogging
import io.mockk.mockk
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.nio.file.Files
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class DatabaseTest {

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    private fun <T : Any> createTestDbContext(func: (DatabaseDemon) -> T): T {
        val dbFile = Files.createTempFile("unit-test-", ".db")
        DatabaseDemon(dbFile, mockk()).use { db ->
            assertNotNull(db)
            logger.debug { "Opened DB file $dbFile" }
            return func(db)
        }
    }

    @Test
    fun testInsertHistoricalValue() {
        createTestDbContext { db ->
            val now = now()

            runBlocking {
                listOf(
                    now.minus(3.days) to -23.3,
                    now.minus(2.hours) to 15.0,
                    now.minus(1.hours) to 20.0,
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
    fun testCreateSqlQueryForAveragedHours() {
        val d = LocalDateTime(2018, 6, 24, 16, 20, 11).toInstant(WARSAW_ZONE_ID)

        assertEquals(
            """SELECT
        |CASE
        |WHEN created BETWEEN '2018-06-24 15:20:11.000' AND '2018-06-24 16:20:11.000' THEN 0
        |WHEN created BETWEEN '2018-06-24 14:20:11.000' AND '2018-06-24 15:20:11.000' THEN 1
        |WHEN created BETWEEN '2018-06-24 13:20:11.000' AND '2018-06-24 14:20:11.000' THEN 2
        |WHEN created BETWEEN '2018-06-24 12:20:11.000' AND '2018-06-24 13:20:11.000' THEN 3
        |WHEN created BETWEEN '2018-06-24 11:20:11.000' AND '2018-06-24 12:20:11.000' THEN 4
        |WHEN created BETWEEN '2018-06-24 10:20:11.000' AND '2018-06-24 11:20:11.000' THEN 5
        |WHEN created BETWEEN '2018-06-24 09:20:11.000' AND '2018-06-24 10:20:11.000' THEN 6
        |ELSE -1 END AS bucket_no,
        |avg(temperature) as temperature, avg(humidity) as humidity, avg(pressure) as pressure
        |FROM indoor_weather_report
        |WHERE created BETWEEN '2018-06-24 09:20:11.000' AND '2018-06-24 16:20:11.000'
        |GROUP BY 1
        |ORDER BY 1 DESC""".trimMargin(), DatabaseDemon.createAveragedByTimeInterval(
                "indoor_weather_report",
                listOf("temperature", "humidity", "pressure"),
                d,
                1.hours,
                7,
                false
            )
        )

        assertEquals(
            """SELECT
        |CASE
        |WHEN created BETWEEN '2018-06-24 15:50:11.000' AND '2018-06-24 16:20:11.000' THEN 0
        |WHEN created BETWEEN '2018-06-24 15:20:11.000' AND '2018-06-24 15:50:11.000' THEN 1
        |WHEN created BETWEEN '2018-06-24 14:50:11.000' AND '2018-06-24 15:20:11.000' THEN 2
        |WHEN created BETWEEN '2018-06-24 14:20:11.000' AND '2018-06-24 14:50:11.000' THEN 3
        |WHEN created BETWEEN '2018-06-24 13:50:11.000' AND '2018-06-24 14:20:11.000' THEN 4
        |WHEN created BETWEEN '2018-06-24 13:20:11.000' AND '2018-06-24 13:50:11.000' THEN 5
        |WHEN created BETWEEN '2018-06-24 12:50:11.000' AND '2018-06-24 13:20:11.000' THEN 6
        |WHEN created BETWEEN '2018-06-24 12:20:11.000' AND '2018-06-24 12:50:11.000' THEN 7
        |WHEN created BETWEEN '2018-06-24 11:50:11.000' AND '2018-06-24 12:20:11.000' THEN 8
        |WHEN created BETWEEN '2018-06-24 11:20:11.000' AND '2018-06-24 11:50:11.000' THEN 9
        |WHEN created BETWEEN '2018-06-24 10:50:11.000' AND '2018-06-24 11:20:11.000' THEN 10
        |WHEN created BETWEEN '2018-06-24 10:20:11.000' AND '2018-06-24 10:50:11.000' THEN 11
        |WHEN created BETWEEN '2018-06-24 09:50:11.000' AND '2018-06-24 10:20:11.000' THEN 12
        |ELSE -1 END AS bucket_no,
        |avg(temperature) as temperature
        |FROM indoor_weather_report
        |WHERE created BETWEEN '2018-06-24 09:50:11.000' AND '2018-06-24 16:20:11.000' AND created < (SELECT MAX(created) FROM indoor_weather_report)
        |GROUP BY 1
        |ORDER BY 1 DESC""".trimMargin(), DatabaseDemon.createAveragedByTimeInterval(
                "indoor_weather_report",
                listOf("temperature"), d, 30.minutes, 13, true
            )
        )

        assertEquals(
            """SELECT
        |CASE
        |WHEN created BETWEEN '2019-01-24 12:00:00.000' AND '2019-01-24 12:30:00.000' THEN 0
        |WHEN created BETWEEN '2019-01-24 11:30:00.000' AND '2019-01-24 12:00:00.000' THEN 1
        |WHEN created BETWEEN '2019-01-24 11:00:00.000' AND '2019-01-24 11:30:00.000' THEN 2
        |WHEN created BETWEEN '2019-01-24 10:30:00.000' AND '2019-01-24 11:00:00.000' THEN 3
        |ELSE -1 END AS bucket_no,
        |avg(value) as value
        |FROM tt
        |WHERE created BETWEEN '2019-01-24 10:30:00.000' AND '2019-01-24 12:30:00.000' AND symbol = 'WER'
        |GROUP BY 1
        |ORDER BY 1 DESC""".trimMargin(), DatabaseDemon.createAveragedByTimeInterval(
                "tt",
                listOf("value"),
                LocalDateTime(2019, 1, 24, 12, 30).toInstant(WARSAW_ZONE_ID),
                30.minutes,
                4,
                false,
                "symbol" to "WER"
            )
        )

        assertEquals(
            """SELECT
        |CASE
        |WHEN created BETWEEN '2019-01-24 12:00:00.000' AND '2019-01-24 12:30:00.000' THEN 0
        |WHEN created BETWEEN '2019-01-24 11:30:00.000' AND '2019-01-24 12:00:00.000' THEN 1
        |ELSE -1 END AS bucket_no,
        |avg(value) as value
        |FROM tt
        |WHERE created BETWEEN '2019-01-24 11:30:00.000' AND '2019-01-24 12:30:00.000' AND created < (SELECT MAX(created) FROM tt) AND symbol IS NULL
        |GROUP BY 1
        |ORDER BY 1 DESC""".trimMargin(), DatabaseDemon.createAveragedByTimeInterval(
                "tt",
                listOf("value"),
                LocalDateTime(2019, 1, 24, 12, 30).toInstant(WARSAW_ZONE_ID),
                30.minutes,
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
        |WHEN created BETWEEN '2020-12-02 00:00:00.000' AND '2020-12-03 00:00:00.000' THEN 0
        |WHEN created BETWEEN '2020-12-01 00:00:00.000' AND '2020-12-02 00:00:00.000' THEN 1
        |WHEN created BETWEEN '2020-11-30 00:00:00.000' AND '2020-12-01 00:00:00.000' THEN 2
        |WHEN created BETWEEN '2020-11-29 00:00:00.000' AND '2020-11-30 00:00:00.000' THEN 3
        |ELSE -1 END AS bucket_no,
        |avg(value) as value
        |FROM tt
        |WHERE created BETWEEN '2020-11-29 00:00:00.000' AND '2020-12-03 00:00:00.000' AND symbol = 'TEST'
        |GROUP BY 1
        |ORDER BY 1 DESC""".trimMargin(), DatabaseDemon.createAveragedByTimeInterval(
                "tt",
                listOf("value"),
                LocalDate(2020, 12, 3).atStartOfDayIn(WARSAW_ZONE_ID), //todo test other zones
                1.days,
                4,
                false,
                "symbol" to "TEST"
            )
        )
    }
}