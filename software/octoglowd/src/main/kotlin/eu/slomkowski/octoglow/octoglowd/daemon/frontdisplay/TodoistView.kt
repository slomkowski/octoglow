package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.httpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


class TodoistView(
    private val config: Config,
    hardware: Hardware,
) : FrontDisplayView(
    hardware,
    "Number of pending tasks from todoist.com",
    90.seconds,
    15.seconds,
) {
    override val preferredDisplayTime: Duration = 6.seconds

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

    data class Report(
        val overdueTasks: Set<Task>,
        val todayTasks: Set<Task>,
        val tomorrowTasks: Set<Task>,
    ) {
        override fun toString(): String = "${todayTasks.size} today, ${tomorrowTasks.size} tomorrow, ${overdueTasks.size} overdue"
    }

    @Volatile
    private var currentReport: Report? = null

    @Volatile
    private var syncToken: String? = null

    override suspend fun poolInstantData(now: Instant) = UpdateStatus.NO_NEW_DATA

    override suspend fun poolStatusData(now: Instant): UpdateStatus = coroutineScope {
        val (newSyncToken, items) = try {
            callSyncApi(config.todoist.apiKey, syncToken ?: "*")
        } catch (e: Exception) {
            logger.error(e) { "Error while calling Todoist sync endpoint;" }
            currentReport = null
            syncToken = null
            return@coroutineScope UpdateStatus.FAILURE
        }

        syncToken = newSyncToken

        if (items.isEmpty()) {
            return@coroutineScope UpdateStatus.NO_NEW_DATA
        }

        val oldReport = currentReport
        val today = now.toLocalDateTime(TimeZone.currentSystemDefault()).date

        fun createGroupOfTasks(oldTasks: Set<Task>?, dateFilter: (LocalDate) -> Boolean): Set<Task> {
            val tasks = oldTasks?.toMutableSet() ?: mutableSetOf()

            items.filter { it.dueDate?.let(dateFilter) ?: false && !it.isDeleted }.mapTo(tasks) { it.toTask() }
            tasks.removeAll { taskId ->
                items.any { taskDto ->
                    taskDto.id == taskId.id && (taskDto.isDeleted || taskDto.isChecked || (taskDto.dueDate?.let { !dateFilter(it) } ?: true))
                }
            }

            return tasks
        }

        val newTodayTasks = createGroupOfTasks(oldReport?.todayTasks) { it == today }
        val newTomorrowTasks = createGroupOfTasks(oldReport?.tomorrowTasks) { today.plus(1, DateTimeUnit.DAY) == it }
        val newOverdueTasks = createGroupOfTasks(oldReport?.overdueTasks) { it < today }

        val newReport = Report(
            todayTasks = newTodayTasks,
            tomorrowTasks = newTomorrowTasks,
            overdueTasks = newOverdueTasks,
        )

        logger.info { "Tasks: $newReport." }
        currentReport = newReport

        UpdateStatus.FULL_SUCCESS
    }

    data class Task(
        val id: String,
        val dueDate: LocalDate?,
    )

    override suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant): Unit = coroutineScope {
        val fd = hardware.frontDisplay
        val rep = currentReport

        if (redrawStatic) {
            fd.setStaticText(0, "Todoist:")
            fd.setStaticText(13, "overdue")
            fd.setStaticText(24, "today")
            fd.setStaticText(36, "tomo")
        }

        if (redrawStatus) {
            fd.setStaticText(20, rep?.todayTasks?.size?.toString() ?: "--")
            fd.setStaticText(31, rep?.tomorrowTasks?.size?.toString() ?: "--")
            fd.setStaticText(9, rep?.overdueTasks?.size?.toString() ?: "--")
        }
    }

}