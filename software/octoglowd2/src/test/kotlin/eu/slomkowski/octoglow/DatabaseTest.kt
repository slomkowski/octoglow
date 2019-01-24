package eu.slomkowski.octoglow

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
    fun testCreateSqlQueryForAveragedHours() {
        val d = LocalDateTime.of(2018, 6, 24, 16, 20, 11)

        assertEquals("""SELECT
        |CASE
		|WHEN created BETWEEN '2018-06-24T15:20:11' AND '2018-06-24T16:20:11' THEN 0
		|WHEN created BETWEEN '2018-06-24T14:20:11' AND '2018-06-24T15:20:11' THEN 1
		|WHEN created BETWEEN '2018-06-24T13:20:11' AND '2018-06-24T14:20:11' THEN 2
		|WHEN created BETWEEN '2018-06-24T12:20:11' AND '2018-06-24T13:20:11' THEN 3
		|WHEN created BETWEEN '2018-06-24T11:20:11' AND '2018-06-24T12:20:11' THEN 4
		|WHEN created BETWEEN '2018-06-24T10:20:11' AND '2018-06-24T11:20:11' THEN 5
		|WHEN created BETWEEN '2018-06-24T09:20:11' AND '2018-06-24T10:20:11' THEN 6
		|ELSE -1 END AS bucket_no,
		|avg(temperature) as temperature, avg(humidity) as humidity, avg(pressure) as pressure
		|FROM indoor_weather_report
		|WHERE created BETWEEN '2018-06-24T09:20:11' AND '2018-06-24T16:20:11'
		|GROUP BY 1
		|ORDER BY 1 DESC""".trimMargin(), DatabaseLayer.createAveragedByTimeInterval("indoor_weather_report",
                listOf("temperature", "humidity", "pressure"), d, Duration.ofHours(1), 7, false))

        assertEquals("""SELECT
		|CASE
		|WHEN created BETWEEN '2018-06-24T15:50:11' AND '2018-06-24T16:20:11' THEN 0
		|WHEN created BETWEEN '2018-06-24T15:20:11' AND '2018-06-24T15:50:11' THEN 1
		|WHEN created BETWEEN '2018-06-24T14:50:11' AND '2018-06-24T15:20:11' THEN 2
		|WHEN created BETWEEN '2018-06-24T14:20:11' AND '2018-06-24T14:50:11' THEN 3
		|WHEN created BETWEEN '2018-06-24T13:50:11' AND '2018-06-24T14:20:11' THEN 4
		|WHEN created BETWEEN '2018-06-24T13:20:11' AND '2018-06-24T13:50:11' THEN 5
		|WHEN created BETWEEN '2018-06-24T12:50:11' AND '2018-06-24T13:20:11' THEN 6
		|WHEN created BETWEEN '2018-06-24T12:20:11' AND '2018-06-24T12:50:11' THEN 7
		|WHEN created BETWEEN '2018-06-24T11:50:11' AND '2018-06-24T12:20:11' THEN 8
		|WHEN created BETWEEN '2018-06-24T11:20:11' AND '2018-06-24T11:50:11' THEN 9
		|WHEN created BETWEEN '2018-06-24T10:50:11' AND '2018-06-24T11:20:11' THEN 10
		|WHEN created BETWEEN '2018-06-24T10:20:11' AND '2018-06-24T10:50:11' THEN 11
		|WHEN created BETWEEN '2018-06-24T09:50:11' AND '2018-06-24T10:20:11' THEN 12
		|ELSE -1 END AS bucket_no,
		|avg(temperature) as temperature
		|FROM indoor_weather_report
		|WHERE created BETWEEN '2018-06-24T09:50:11' AND '2018-06-24T16:20:11' AND created < (SELECT MAX(created) FROM indoor_weather_report)
		|GROUP BY 1
		|ORDER BY 1 DESC""".trimMargin(), DatabaseLayer.createAveragedByTimeInterval("indoor_weather_report",
                listOf("temperature"), d, Duration.ofMinutes(30), 13, true))
    }
}