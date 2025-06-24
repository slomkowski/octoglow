package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.httpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class TodoistView(
    private val config: Config,
    hardware: Hardware,
) : FrontDisplayView(
    hardware,
    "Number of pending tasks from todoist.com",
    10.minutes,
    15.seconds,
) {
    override val preferredDisplayTime: Duration = 13.seconds

    @Serializable
    data class Task(
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
        val results: List<Task> = emptyList(),

        @SerialName("next_cursor")
        val nextCursor: String? = null,
    )

    @Serializable
    data class LiveNotification(
        @SerialName("created_at")
        val createdAt: Instant,

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
    )

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

    companion object {
        private val logger = KotlinLogging.logger {}

        private const val HISTORIC_VALUES_LENGTH = 14

        suspend fun listenToChanges(apiToken: String) {
            val endpoint = "https://api.todoist.com/api/v1/sync"
            var syncToken = "*"

            while (true) {
                val response = httpClient.post(endpoint) {
                    parameter("sync_token", syncToken)
                    parameter("resource_types", """["items"]""")
                    header(HttpHeaders.Authorization, "Bearer $apiToken")
                }.body<SyncResponse>()

                syncToken = response.syncToken

                logger.info { "Changes: $response" }

                delay(10.seconds)
            }
        }

        suspend fun fetchPendingTasksForToday(apiToken: String): Int {
            val today = Clock.System.now().toLocalDateTime(TimeZone.UTC).date.toString()
            val query = "today | overdue"

            val allTasks = withTimeout(1.minutes) {
                val allTasks = mutableListOf<Task>()
                var cursor: String? = null
                do {
                    val tasks = httpClient.get("https://api.todoist.com/api/v1/tasks/filter") {
                        parameter("query", query)
                        parameter("lang", "en")
                        parameter("cursor", cursor)
                        header(HttpHeaders.Authorization, "Bearer $apiToken")
                    }.body<TaskResponse>()
                    logger.debug { "Retrieved page of ${tasks.results.size} tasks." }
                    cursor = tasks.nextCursor
                    allTasks.addAll(tasks.results)
                } while (cursor != null)
                allTasks
            }

            logger.info { allTasks }

            TODO()
        }

    }

    data class Report(
        val syncToken: String,
        val noTasksTomorrow: Int,
        val noTasksToday: Int,
        val noTasksOverdue: Int,
    ) {
        init {
            require(syncToken.isNotBlank()) { "Sync token must not be blank" }
            require(noTasksOverdue >= 0)
            require(noTasksToday >= 0)
            require(noTasksTomorrow >= 0)
        }
    }

    @Volatile
    private var currentReport: Report? = null

    override suspend fun poolInstantData(now: Instant) = UpdateStatus.NO_NEW_DATA

    override suspend fun poolStatusData(now: Instant): UpdateStatus = coroutineScope {
        UpdateStatus.FULL_SUCCESS
    }

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant): Unit = coroutineScope {
        val fd = hardware.frontDisplay
        val rep = currentReport

        if (redrawStatic) {
            fd.setStaticText(0, "Todoist:")
            fd.setStaticText(11, "overdue")
            fd.setStaticText(23, "today")
            fd.setStaticText(35, "tomo")
        }

        if (redrawStatus) {
            fd.setStaticText(20, rep?.noTasksToday?.toString() ?: "--")
            fd.setStaticText(29, rep?.noTasksTomorrow?.toString() ?: "--")
            fd.setStaticText(9, rep?.noTasksOverdue?.toString() ?: "--")
        }
    }

}