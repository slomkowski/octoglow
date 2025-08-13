@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.DataSnapshotBus
import eu.slomkowski.octoglow.octoglowd.Snapshot
import eu.slomkowski.octoglow.octoglowd.dataharvesters.TodoistDataHarvester.Item
import eu.slomkowski.octoglow.octoglowd.httpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class TodoistDataSnapshot(
    override val timestamp: Instant,
    val cycleLength: Duration,
    val dataItem: Result<List<Item>>,
) : Snapshot

class TodoistDataHarvester(
    private val config: Config,
    eventBus: DataSnapshotBus,
) : DataHarvester(logger, 55.seconds, eventBus) {

    @Serializable
    data class Due(
        val date: String
    )

    @Serializable
    data class Item(
        val id: String,
        val description: String,
        val content: String,
        val priority: Int,
        val due: Due?,
        @SerialName("user_id")
        val userId: String,
        @SerialName("checked")
        val isChecked: Boolean,
        @SerialName("is_deleted")
        val isDeleted: Boolean,
    ) {
        val dueDate: LocalDate?
            get() = due?.date?.let {
                try {
                    LocalDateTime.parse(it).date
                } catch (e: IllegalArgumentException) {
                    try {
                        LocalDate.parse(it)
                    } catch (e: IllegalArgumentException) {
                        logger.error { "Cannot parse $it" }
                        null
                    }
                }
            }
    }

    @Serializable
    data class SyncResponse(
        @SerialName("full_sync")
        val fullSync: Boolean,

        @SerialName("items")
        val items: List<Item>,

        @SerialName("sync_token")
        val syncToken: String,
    )

    @Volatile
    private var syncToken: String? = null

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val TODOIST_SYNC_ENDPOINT = "https://api.todoist.com/api/v1/sync"

        suspend fun callSyncApi(apiToken: String, syncToken: String = "*"): Pair<String, List<Item>> {
            logger.info { "Calling Todoist API, syncToken: $syncToken." }
            val response = httpClient.post(TODOIST_SYNC_ENDPOINT) {
                parameter("sync_token", syncToken)
                parameter("resource_types", """["items"]""")
                header(HttpHeaders.Authorization, "Bearer $apiToken")
                timeout {
                    requestTimeoutMillis = 30_000
                }
            }.body<SyncResponse>()

            return response.syncToken to response.items
        }
    }

    override suspend fun pollForNewData(now: Instant) {
        val (newSyncToken, items) = try {
            callSyncApi(config.todoist.apiKey, syncToken ?: "*").let { it.first to Result.success(it.second) }
        } catch (e: Exception) {
            logger.error(e) { "Error while calling Todoist sync endpoint;" }
            null to Result.failure(e)
        }

        syncToken = newSyncToken

        publish(TodoistDataSnapshot(now, pollingInterval, items))
    }
}