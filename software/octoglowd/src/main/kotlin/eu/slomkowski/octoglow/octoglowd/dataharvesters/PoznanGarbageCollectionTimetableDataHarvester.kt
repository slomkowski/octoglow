@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.DataSnapshotBus
import eu.slomkowski.octoglow.octoglowd.Snapshot
import eu.slomkowski.octoglow.octoglowd.httpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import kotlinx.datetime.LocalDate
import org.jsoup.Jsoup
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class PoznanGarbageCollectionTimetableSnapshot(
    override val timestamp: Instant,
    val cycleLength: Duration,
    val timetable: Result<List<Pair<LocalDate, String>>>,
) : Snapshot

class PoznanGarbageCollectionTimetableDataHarvester(
    private val config: Config,
    dataSnapshotBus: DataSnapshotBus,
) : DataHarvester(logger, 33.minutes, dataSnapshotBus) {

    companion object {
        private val logger = KotlinLogging.logger {}

        private val monthYearRegex = Regex("(\\d{1,2})\\.(\\d{4})")

        private const val POZNAN_ODPADY_URL = "https://www.poznan.pl/mim/odpady/harmonogramy.html"

        suspend fun downloadTimetable(streetName: String, buildingNumber: String, objectNumber: String?): String {
            logger.info {
                val address = "$streetName $buildingNumber ${objectNumber.orEmpty()}".trim()
                "Downloading timetable for '$address' from $POZNAN_ODPADY_URL"
            }

            val response = httpClient.post(POZNAN_ODPADY_URL) {
                parameter("action", "search")
                parameter("co", "waste_schedule")
                parameter("ws_street", streetName.uppercase().trim())
                parameter("ws_number", buildingNumber.trim())
                parameter("ws_object", objectNumber.orEmpty().trim())

                timeout { requestTimeoutMillis = 30_000 }
            }.body<String>()

            return response
        }

        fun extractTimetableFromHtml(html: String): List<Pair<LocalDate, String>> {
            val doc = Jsoup.parse(html)
            val table = doc.selectFirst("table#schedule_0") ?: error("Can't find table#schedule_0. Is the street name valid?")
            return table.select("tr").mapNotNull { tr ->
                val garbageType = tr.selectFirst(".waste-column")?.text()?.trim() ?: return@mapNotNull null
                tr.select("[data-value]").map { td ->
                    val monthYearStr = td.attr("data-value").trim()
                    val mr = monthYearRegex.matchEntire(monthYearStr) ?: return@map null
                    val month = mr.groupValues[1].toInt()
                    val year = mr.groupValues[2].toInt()

                    val days = td.text().split(',').mapNotNull { it.trim().toIntOrNull() }

                    if (days.isEmpty()) {
                        return@map null
                    }

                    days.map { day -> LocalDate(year, month, day) to garbageType }
                }.filterNotNull().flatten()
            }.flatten().sortedWith(compareBy({ it.first }, { it.second }))
        }
    }

    override suspend fun pollForNewData(now: Instant) {
        val timetableResult = try {
            val html = downloadTimetable(config.garbageCollectionTimetable.streetName, config.garbageCollectionTimetable.buildingNumber, null)
            Result.success(extractTimetableFromHtml(html))
        } catch (e: Exception) {
            logger.error(e) { "Error while downloading garbage collection timetable;" }
            Result.failure(e)
        }

        publish(PoznanGarbageCollectionTimetableSnapshot(now, pollingInterval, timetableResult))
    }
}