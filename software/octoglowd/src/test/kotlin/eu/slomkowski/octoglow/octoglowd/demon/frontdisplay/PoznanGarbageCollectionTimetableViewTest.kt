@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.hardware.Slot
import eu.slomkowski.octoglow.octoglowd.hardware.mock.HardwareMock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class PoznanGarbageCollectionTimetableViewTest {

    @Test
    fun `should format date according to pattern`() {
        val f = PoznanGarbageCollectionTimetableView.dayAndMonthFormat
        assertThat(LocalDate(2025, 8, 13).format(f)).isEqualTo("13.08")
        assertThat(LocalDate(2025, 8, 2).format(f)).isEqualTo("2.08")
        assertThat(LocalDate(2024, 12, 3).format(f)).isEqualTo("3.12")
    }

    @Test
    fun `should create next trash day info`() {
        val today = LocalDate(2025, 8, 13)
        val testCases = listOf(
            Pair(null, "-------- "),
            Pair(today, "TODAY    "),
            Pair(LocalDate(2025, 8, 14), "tomorrow "),
            Pair(LocalDate(2025, 10, 14), "in >9days"),
            Pair(LocalDate(2025, 8, 16), "in 3 days"),
            Pair(LocalDate(2025, 8, 30), "in >9days"),
            Pair(LocalDate(2025, 9, 22), "in >9days"),
        )

        for ((date, expectedResult) in testCases) {
            val actualResult = PoznanGarbageCollectionTimetableView.createNextTrashDayInfo(today, date)
            assertThat(actualResult).isEqualTo(expectedResult)
            assertThat(actualResult.length).isLessThanOrEqualTo(9)
        }
    }

    @Test
    fun testRedrawDisplayEmpty(): Unit = runBlocking {
        val hardware = HardwareMock()
        val view = PoznanGarbageCollectionTimetableView(hardware)

        view.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = Clock.System.now(),
            status = null,
            instant = Unit
        )

        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("Next trash -------- ")
        assertThat(hardware.frontDisplay.line2content).isEqualTo(" NO DATA DOWNLOADED ")
        assertThat(hardware.frontDisplay.scrollingTextContent[Slot.SLOT0]).isNull()
    }

    @Test
    fun testRedrawDisplayFilled1(): Unit = runBlocking {
        val hardware = HardwareMock()
        val view = PoznanGarbageCollectionTimetableView(hardware)

        val now = Instant.parse("2025-08-07T12:00:00.000Z")
        val today = LocalDate(2025, 8, 7)

        val dayTasks = listOf(
            LocalDate(2025, 8, 10) to listOf("Trash A"),
            LocalDate(2025, 8, 15) to listOf("Trash B", "Trash C"),
            LocalDate(2025, 8, 16) to listOf("Trash D"),
        ).map { PoznanGarbageCollectionTimetableView.DayOperation(it.first, it.second) }

        view.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = now,
            status = PoznanGarbageCollectionTimetableView.CurrentReport(
                Clock.System.now(),
                50.minutes,
                dayTasks,
            ),
            instant = Unit
        )

        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("Next trash in 3 days")
        assertThat(hardware.frontDisplay.line2content).isEqualTo("####################")
        assertThat(hardware.frontDisplay.scrollingTextContent[Slot.SLOT0]).isEqualTo("10.08: TRASH A  15.08: trash b,trash c  16.08: trash d")

        view.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = now.plus(1.days),
            status = PoznanGarbageCollectionTimetableView.CurrentReport(
                Clock.System.now(),
                50.minutes,
                dayTasks,
            ),
            instant = Unit,
        )

        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("Next trash in 2 days")
        assertThat(hardware.frontDisplay.line2content).isEqualTo("####################")
        assertThat(hardware.frontDisplay.scrollingTextContent[Slot.SLOT0]).isEqualTo("10.08: TRASH A  15.08: trash b,trash c  16.08: trash d")

        view.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = now.plus(3.days),
            status = PoznanGarbageCollectionTimetableView.CurrentReport(
                Clock.System.now(),
                50.minutes,
                dayTasks,
            ),
            instant = Unit
        )

        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("Next trash TODAY    ")
        assertThat(hardware.frontDisplay.line2content).isEqualTo("####################")
        assertThat(hardware.frontDisplay.scrollingTextContent[Slot.SLOT0]).isEqualTo("10.08: TRASH A  15.08: trash b,trash c  16.08: trash d")

        view.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = now.plus(4.days),
            status = PoznanGarbageCollectionTimetableView.CurrentReport(
                Clock.System.now(),
                50.minutes,
                dayTasks,
            ),
            instant = Unit
        )

        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("Next trash in 4 days")
        assertThat(hardware.frontDisplay.line2content).isEqualTo("####################")
        assertThat(hardware.frontDisplay.scrollingTextContent[Slot.SLOT0]).isEqualTo("15.08: TRASH B,TRASH C  16.08: trash d")
    }
}