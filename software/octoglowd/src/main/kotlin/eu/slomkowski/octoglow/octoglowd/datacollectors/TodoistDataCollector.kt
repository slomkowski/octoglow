@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.datacollectors

import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.HistoricalValuesEvents
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.TodoistView.Task
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

class TodoistDataCollector(
    private val config: Config,
    eventBus: HistoricalValuesEvents,
) : DataCollector(logger, 90.seconds, eventBus) {

    @Serializable
    data class TaskDto(
        val id: String,
        val content: String,
        val due: Due?,
    )

    @Serializable
    data class Due(
        val date: String
    )

    @Serializable
    data class TaskResponse(
        val results: List<TaskDto> = emptyList(),

        @SerialName("next_cursor")
        val nextCursor: String? = null,
    )

    @Serializable
    data class LiveNotification(
        @SerialName("created_at")
        val createdAt: kotlinx.datetime.Instant,

        @SerialName("notification_type")
        val notificationType: String,
    )

    @Serializable
    data class CompletedInfo(
        val id: String,
    )

    @Serializable
    data class Item(
        val id: String,
        @SerialName("project_id")
        val projectId: String,
        val content: String,
        val priority: Int,
        val due: Due?,
//        val completed: Boolean,
        @SerialName("user_id")
        val userId: String,
        val labels: List<String>,
        @SerialName("parent_id")
        val parentId: String?,
//        val order: Int,
        @SerialName("child_order")
        val childOrder: Int,
//        @SerialName("date_added")
//        val dateAdded: String,
        @SerialName("checked")
        val isChecked: Boolean,
        val description: String,
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

        fun toTask() = Task(id, dueDate)
    }

    @Serializable
    data class SyncResponse(
        @SerialName("full_sync")
        val fullSync: Boolean,

//        @SerialName("completed_info")
//        val completedInfo: List<CompletedInfo>,

        @SerialName("items")
        val items: List<Item>,

//        @SerialName("live_notifications")
//        val liveNotifications: List<LiveNotification>,

        @SerialName("sync_token")
        val syncToken: String,
    )

    data class TodoistMeasurementReport(
        override val timestamp: Instant,
        override val cycleLength: Duration,
        val dataItem: Result<List<Item>>,
    ) : MeasurementReport {
        override val values: List<Measurement> = emptyList()
    }

    @Volatile
    private var syncToken: String? = null

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val TODOIST_SYNC_ENDPOINT = "https://api.todoist.com/api/v1/sync"

        suspend fun callSyncApi(apiToken: String, syncToken: String = "*"): Pair<String, List<Item>> {
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

        publishPacket(TodoistMeasurementReport(now, pollInterval, items))
    }
}