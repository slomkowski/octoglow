package eu.slomkowski.octoglow.octoglowd

import eu.slomkowski.octoglow.octoglowd.daemon.Demon
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import kotlinx.datetime.format.DateTimeComponents
import kotlinx.datetime.format.char
import kotlin.math.roundToInt
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class RadmonOrgSender(
    private val config: Config,
    private val eventBus: HistoricalValuesEvents,
) : Demon() {

    companion object {
        private val logger = KotlinLogging.logger { }

        private const val RADMON_ORG_API_URL = "https://radmon.org/radmon.php"

        val phpDateTimeFormat = DateTimeComponents.Format {
            year()
            char('-')
            monthNumber()
            char('-')
            dayOfMonth()
            char(' ')
            hour()
            char(':')
            minute()
            char(':')
            second()
        }

        suspend fun submitToRadmonOrg(
            username: String,
            password: String,
            ts: Instant,
            cpm: Double,
        ) {
            val tsStr = ts.format(phpDateTimeFormat)
            val cpmInt = cpm.roundToInt()
            logger.info { "Submitting to radmon.org measurement of $cpmInt CPM (user '$username')." }

            try {
                httpClient.get(RADMON_ORG_API_URL) {
                    timeout {
                        requestTimeoutMillis = 20_000
                    }
                    parameter("function", "submit")
                    parameter("user", username)
                    parameter("password", password)
                    parameter("datetime", tsStr)
                    parameter("value", cpmInt)
                    parameter("unit", "CPM")
                }
            } catch (e: Exception) {
                logger.error(e) { "Failed to submit data to radmon.org" }
            }
        }

        suspend fun getLastReading(username: String): Double {
            val responseStr = httpClient.get(RADMON_ORG_API_URL) {
                timeout {
                    requestTimeoutMillis = 20_000
                }
                parameter("function", "lastreading")
                parameter("user", username)
            }.bodyAsText().trim()
            logger.info { "Last raw reading: $responseStr." }
            return Regex("(\\d+) CPM").find(responseStr)?.let { it.groupValues[1].toDouble() } ?: error("Failed to parse $responseStr")
        }
    }

    override fun createJobs(scope: CoroutineScope): List<Job> {
        if (config.radmon?.enabled != true) {
            return listOf(scope.launch {})
        }
        return listOf(scope.launch {
            eventBus.events.collect { packet ->
                packet.values
                    .filter { it.type == RadioactivityCpm && it.value.isSuccess }
                    .forEach { radioactivityCpm ->
                        submitToRadmonOrg(
                            config.radmon.username,
                            config.radmon.password,
                            packet.timestamp.toKotlinxDatetimeInstant(),
                            radioactivityCpm.value.getOrThrow(),
                        )
                    }
            }
        })
    }
}