@file:OptIn(ExperimentalTime::class, ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.datacollectors.MeasurementReport
import eu.slomkowski.octoglow.octoglowd.datacollectors.TodoistDataCollector
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.toKotlinxDatetimeInstant
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime


class TodoistView(
    hardware: Hardware,
) : FrontDisplayView2<TodoistView.Report, Unit>(
    hardware,
    "Todoist",
    null,
    logger,
) {
    override fun preferredDisplayTime(status: Report?) = 6.seconds

    companion object {
        private val logger = KotlinLogging.logger {}
    }

    data class Report(
        val overdueTasks: Set<Task>,
        val todayTasks: Set<Task>,
        val tomorrowTasks: Set<Task>,
    ) {
        override fun toString(): String = "${todayTasks.size} today, ${tomorrowTasks.size} tomorrow, ${overdueTasks.size} overdue"
    }

    override suspend fun onNewMeasurementReport(report: MeasurementReport, oldReport: Report?): UpdateStatus {
        if (report !is TodoistDataCollector.TodoistMeasurementReport) {
            return UpdateStatus.NoNewData
        }

        val items = report.dataItem.getOrNull() ?: return UpdateStatus.NoNewData // todo na pewno?

        val today = report.timestamp.toKotlinxDatetimeInstant().toLocalDateTime(TimeZone.currentSystemDefault()).date

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

        return UpdateStatus.NewData(newReport)
    }

    data class Task(
        val id: String,
        val dueDate: LocalDate?,
    )

    override suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: kotlin.time.Instant,
        status: Report?,
        instant: Unit?
    ): Unit = coroutineScope {
        val fd = hardware.frontDisplay

        if (redrawStatic) {
            fd.setStaticText(0, "Todoist:")
            fd.setStaticText(13, "overdue")
            fd.setStaticText(24, "today")
            fd.setStaticText(36, "tomo")
        }

        if (redrawStatus) {
            fd.setStaticText(20, status?.todayTasks?.size?.toString() ?: "--")
            fd.setStaticText(31, status?.tomorrowTasks?.size?.toString() ?: "--")
            fd.setStaticText(9, status?.overdueTasks?.size?.toString() ?: "--")
        }
    }
}