@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.TodoistView.Companion.createTodayTaskText
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import eu.slomkowski.octoglow.octoglowd.hardware.mock.HardwareMock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class TodoistViewTest {

    @Test
    fun testCreateTodayTaskText() {
        assertThat(createTodayTaskText(0, 0)).isEqualTo("0")
        assertThat(createTodayTaskText(1, 0)).isEqualTo("1")
        assertThat(createTodayTaskText(11, 0)).isEqualTo("11")
        assertThat(createTodayTaskText(12, 3)).isEqualTo("12 (3)")
        assertThat(createTodayTaskText(30, 15)).isEqualTo("30(15)")
        assertThat(createTodayTaskText(345, 15)).isEqualTo(">99(15)")
        assertThat(createTodayTaskText(345, 123)).isEqualTo(">99(99)")
    }

    @Test
    fun testRedrawDisplayEmpty() : Unit = runBlocking {
        val hardware = HardwareMock()
        val view = TodoistView(hardware)

        view.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = Clock.System.now(),
            status = null,
            instant = Unit)

        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("ping -- ms gw  -- ms")
        assertThat(hardware.frontDisplay.line2content).isEqualTo("mqtt FAIL! #########")
        assertThat(hardware.frontDisplay.scrollingTextContent[Slot.SLOT0]).isEqualTo("IP: ---.---.---.---")
    }

    @Test
    fun testRedrawDisplayFilled() : Unit = runBlocking {
        val hardware = HardwareMock()
        val view = TodoistView(hardware)

        val now = Instant.parse("2025-08-07T12:00:00.000Z")
        val today = LocalDate(2025, 8, 7   )

        val overdueTasks = (0..30).map {
            TodoistView.Task(
                UUID.randomUUID().toString(),
                today.minus(1, DateTimeUnit.DAY),
                1) }.toSet()

            val tomorrowTasks = (0..9).map {
                TodoistView.Task(
                    UUID.randomUUID().toString(),
                    today.plus(1, DateTimeUnit.DAY),
                    1) }.toSet()

        val todayTasks = (0..50).map {
            TodoistView.Task(
                UUID.randomUUID().toString(),
                today,
                1 + it % 2
            )
        }.toSet()

        view.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = now,
            status = TodoistView.Report(
                Clock.System.now(),
                5.minutes,
                overdueTasks,
                todayTasks,
                tomorrowTasks,
            ),
            instant = Unit)

        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("ping -- ms gw  -- ms")
        assertThat(hardware.frontDisplay.line2content).isEqualTo("mqtt FAIL! #########")
        assertThat(hardware.frontDisplay.scrollingTextContent[Slot.SLOT0]).isEqualTo("IP: ---.---.---.---")
    }
}