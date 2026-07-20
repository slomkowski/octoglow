@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.defaultTestConfig
import eu.slomkowski.octoglow.octoglowd.hardware.mock.HardwareMock
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Month
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class ShoppingSundayViewTest {

    @Test
    fun `should find next shopping sunday`() {
        // today is a shopping sunday
        assertThat(ShoppingSundayView.findNextShoppingSunday(LocalDate(2026, Month.AUGUST, 30)))
            .isEqualTo(LocalDate(2026, Month.AUGUST, 30))
        // day before
        assertThat(ShoppingSundayView.findNextShoppingSunday(LocalDate(2026, Month.AUGUST, 29)))
            .isEqualTo(LocalDate(2026, Month.AUGUST, 30))
        // after the last shopping sunday of the year, rolls to next year
        assertThat(ShoppingSundayView.findNextShoppingSunday(LocalDate(2026, Month.DECEMBER, 21)))
            .isEqualTo(LocalDate(2027, Month.JANUARY, 31))
    }

    @Test
    fun `should format countdown`() {
        val today = LocalDate(2026, Month.AUGUST, 24)
        assertThat(ShoppingSundayView.formatCountdown(today, today)).isEqualTo("TODAY!")
        assertThat(ShoppingSundayView.formatCountdown(today, LocalDate(2026, Month.AUGUST, 25))).isEqualTo("tomorrow")
        assertThat(ShoppingSundayView.formatCountdown(today, LocalDate(2026, Month.AUGUST, 30))).isEqualTo("in 6 days")
        assertThat(ShoppingSundayView.formatCountdown(today, LocalDate(2026, Month.DECEMBER, 6))).isEqualTo("in 14 weeks")
    }

    @Test
    fun `should format short date`() {
        assertThat(ShoppingSundayView.formatShortDate(LocalDate(2026, Month.AUGUST, 30))).isEqualTo("30 Aug")
        assertThat(ShoppingSundayView.formatShortDate(LocalDate(2026, Month.JANUARY, 25))).isEqualTo("25 Jan")
        assertThat(ShoppingSundayView.formatShortDate(LocalDate(2026, Month.DECEMBER, 6))).isEqualTo("6 Dec")
    }

    @Test
    fun testRedrawDisplayNull(): Unit = runBlocking {
        val hardware = HardwareMock()
        val view = ShoppingSundayView(defaultTestConfig, hardware)

        view.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = Instant.parse("2026-08-30T12:00:00.000Z"),
            status = null,
            instant = Unit,
        )

        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("  Shopping Sunday   ")
        assertThat(hardware.frontDisplay.line2content).isEqualTo("      no date       ")
    }

    @Test
    fun testRedrawDisplayShoppingSunday(): Unit = runBlocking {
        val hardware = HardwareMock()
        val view = ShoppingSundayView(defaultTestConfig, hardware)

        // Aug 30, 2026 is a shopping sunday
        view.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = Instant.parse("2026-08-30T12:00:00.000Z"),
            status = LocalDate(2026, Month.AUGUST, 30),
            instant = Unit,
        )

        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("  Shopping Sunday   ")
        assertThat(hardware.frontDisplay.line2content).isEqualTo("   30 Aug, TODAY!   ")
    }

    @Test
    fun testRedrawDisplayTomorrow(): Unit = runBlocking {
        val hardware = HardwareMock()
        val view = ShoppingSundayView(defaultTestConfig, hardware)

        view.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = Instant.parse("2026-08-29T12:00:00.000Z"),
            status = LocalDate(2026, Month.AUGUST, 29),
            instant = Unit,
        )

        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("  Shopping Sunday   ")
        assertThat(hardware.frontDisplay.line2content).isEqualTo("  30 Aug, tomorrow  ")
    }

    @Test
    fun testRedrawDisplaySeveralDays(): Unit = runBlocking {
        val hardware = HardwareMock()
        val view = ShoppingSundayView(defaultTestConfig, hardware)

        // Aug 24, 2026 is 6 days before Aug 30
        view.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = Instant.parse("2026-08-24T12:00:00.000Z"),
            status = LocalDate(2026, Month.AUGUST, 24),
            instant = Unit,
        )

        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("  Shopping Sunday   ")
        assertThat(hardware.frontDisplay.line2content).isEqualTo(" 30 Aug, in 6 days  ")
    }

    @Test
    fun testRedrawDisplayAfterLastOfYear(): Unit = runBlocking {
        val hardware = HardwareMock()
        val view = ShoppingSundayView(defaultTestConfig, hardware)

        // Dec 21 is after the last 2026 shopping sunday (Dec 20); next is Jan 31, 2027 = 41 days
        view.redrawDisplay(
            redrawStatic = true,
            redrawStatus = true,
            now = Instant.parse("2026-12-21T12:00:00.000Z"),
            status = LocalDate(2026, Month.DECEMBER, 21),
            instant = Unit,
        )

        println(hardware.frontDisplay.renderDisplayContent())
        assertThat(hardware.frontDisplay.line1content).isEqualTo("  Shopping Sunday   ")
        assertThat(hardware.frontDisplay.line2content).isEqualTo(" 31 Jan, in 5 weeks ")
    }
}
