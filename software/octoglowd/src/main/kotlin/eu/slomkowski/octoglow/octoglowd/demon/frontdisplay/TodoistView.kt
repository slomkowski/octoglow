@file:OptIn(ExperimentalTime::class, ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.Snapshot
import eu.slomkowski.octoglow.octoglowd.dataharvesters.TodoistDataHarvester
import eu.slomkowski.octoglow.octoglowd.dataharvesters.TodoistDataSnapshot
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import eu.slomkowski.octoglow.octoglowd.toKotlinxDatetimeInstant
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


class TodoistView(
    hardware: Hardware,
) : FrontDisplayView<TodoistView.Report, Unit>(
    hardware,
    "Todoist",
    null,
    logger,
) {
    override fun preferredDisplayTime(status: Report?) = 6.seconds

    companion object {
        private val logger = KotlinLogging.logger {}

        fun createTodayTaskText(
            noAllTodayTasks: Int?,
            noImportantTodayTasks: Int?
        ): String {
            val allTasksText: String = when (noAllTodayTasks) {
                null -> "-- "
                in 0..99 -> noAllTodayTasks.toString()
                else -> ">99"
            }

            val importantTasksText: String = when (noImportantTodayTasks) {
                null, 0 -> ""
                in 0..99 -> "($noImportantTodayTasks)"
                else -> "(99)"
            }

            return (allTasksText + if (allTasksText.length + importantTasksText.length < 6) {
                " "
            } else {
                ""
            } + importantTasksText).trim()
        }
    }

    data class Task(
        val id: String,
        val dueDate: LocalDate?,
        val priority: Int,
    ) {
        constructor(item: TodoistDataHarvester.Item) : this(
            item.id,
            item.dueDate,
            item.priority,
        )
    }

    data class Report(
        val timestamp: Instant,
        val cycleLength: Duration,
        val overdueTasks: Set<Task>,
        val todayTasks: Set<Task>,
        val tomorrowTasks: Set<Task>,
    ) {
        override fun toString(): String = "${todayTasks.size} today, ${tomorrowTasks.size} tomorrow, ${overdueTasks.size} overdue"
    }

    override suspend fun onNewDataSnapshot(snapshot: Snapshot, oldStatus: Report?): UpdateStatus {
        if (snapshot !is TodoistDataSnapshot) {
            return UpdateStatus.NoNewData
        }

        val items = snapshot.dataItem.getOrNull() ?: return UpdateStatus.NoNewData // todo na pewno?

        val today = snapshot.timestamp.toKotlinxDatetimeInstant().toLocalDateTime(TimeZone.currentSystemDefault()).date

        fun createGroupOfTasks(oldTasks: Set<Task>?, dateFilter: (LocalDate) -> Boolean): Set<Task> {
            val tasks = oldTasks?.toMutableSet() ?: mutableSetOf()

            items.filter { it.dueDate?.let(dateFilter) ?: false && !it.isDeleted }.mapTo(tasks) { Task(it) }
            tasks.removeAll { taskId ->
                items.any { taskDto ->
                    taskDto.id == taskId.id && (taskDto.isDeleted || taskDto.isChecked || (taskDto.dueDate?.let { !dateFilter(it) } ?: true))
                }
            }

            return tasks
        }

        val newTodayTasks = createGroupOfTasks(oldStatus?.todayTasks) { it == today }
        val newTomorrowTasks = createGroupOfTasks(oldStatus?.tomorrowTasks) { today.plus(1, DateTimeUnit.DAY) == it }
        val newOverdueTasks = createGroupOfTasks(oldStatus?.overdueTasks) { it < today }

        val newReport = Report(
            timestamp = snapshot.timestamp,
            cycleLength = snapshot.cycleLength,
            todayTasks = newTodayTasks,
            tomorrowTasks = newTomorrowTasks,
            overdueTasks = newOverdueTasks,
        )

        logger.info { "Tasks: $newReport." }

        return UpdateStatus.NewData(newReport)
    }

    override suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: Instant,
        status: Report?,
        instant: Unit?
    ): Unit = coroutineScope {
        val fd = hardware.frontDisplay

        if (redrawStatic) {
            fd.setStaticText(0, "todo:")
            fd.setStaticText(12, "tomorrow")
            fd.setStaticText(20, "TODAY")
        }

        if (redrawStatus) {
            val noOverdueTasks = status?.overdueTasks?.size
            val noTodayTasks = status?.todayTasks?.size
            val noTodayImportantTasks = status?.todayTasks?.count { it.priority > 1 } ?: 0

            fd.setStaticText(6, (status?.tomorrowTasks?.size?.toString() ?: "--").padStart(4))

            fd.setStaticText(26, createTodayTaskText(noTodayTasks, noTodayImportantTasks))

            if ((noOverdueTasks ?: 0) > 0 || noOverdueTasks == null) {
                fd.setScrollingText(Slot.SLOT1, 32, 8, "${noOverdueTasks ?: "---"} overdue!")
            } else {
                fd.setStaticText(32, "        ")
            }
        }

        drawProgressBar(status?.timestamp, now, status?.cycleLength)
    }
}