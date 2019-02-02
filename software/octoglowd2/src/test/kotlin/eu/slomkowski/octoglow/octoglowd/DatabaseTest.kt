package eu.slomkowski.octoglow.octoglowd

import mu.KLogging
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime
import kotlin.test.assertEquals

class DatabaseTest {

    companion object : KLogging()

//    @Test
//    fun testDb() {
//        runBlocking {
//            val db = DatabaseLayer(Paths.get("unit-test.db"), coroutineContext)
//            assertNotNull(db)
//
//            launch {
//                sendEvent(OutdoorWeatherReport(20.0, 34.0, false, false))
//                delay(3000)
//                EventBus.close()
//            }
//        }
//    }
//
//    @Test
//    fun testGet() {
//        runBlocking {
//            val db = DatabaseLayer(Paths.get("unit-test.db"), coroutineContext)
//
//            val l = db.getLastOutdoorWeatherReportsByHour(LocalDateTime.of(2018, 1, 13, 13, 45), 5).await()
//
//            logger.info("Results: {}", l)
//
//            EventBus.close()
//        }
//    }

    @Test
    fun testDateTimeFormat() {
        assertEquals("2019-01-25 00:32:35.212000", LocalDateTime.of(2019, 1, 25, 0, 32, 35, 212_000_000).format(DatabaseLayer.sqliteNativeDateTimeFormat))
    }

    @Test
    fun testCreateSqlQueryForAveragedHours() {
        val d = LocalDateTime.of(2018, 6, 24, 16, 20, 11)

        assertEquals("""SELECT
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
        |ORDER BY 1 DESC""".trimMargin(), DatabaseLayer.createAveragedByTimeInterval("indoor_weather_report",
                listOf("temperature", "humidity", "pressure"), d, Duration.ofHours(1), 7, false))

        assertEquals("""SELECT
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
        |ORDER BY 1 DESC""".trimMargin(), DatabaseLayer.createAveragedByTimeInterval("indoor_weather_report",
                listOf("temperature"), d, Duration.ofMinutes(30), 13, true))

        assertEquals("""SELECT
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
                LocalDateTime.of(2019, 1, 24, 12, 30),
                Duration.ofMinutes(30),
                4,
                false,
                "symbol" to "WER"))

        assertEquals("""SELECT
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
                LocalDateTime.of(2019, 1, 24, 12, 30),
                Duration.ofMinutes(30),
                2,
                true,
                "symbol" to null))
    }
}