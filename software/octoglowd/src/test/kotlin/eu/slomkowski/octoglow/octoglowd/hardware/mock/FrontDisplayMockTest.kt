package eu.slomkowski.octoglow.octoglowd.hardware.mock

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test

class FrontDisplayMockTest {

    private val logger = KotlinLogging.logger {}

    private val frontDisplay = FrontDisplayMock()

    @Test
    fun testSetUpperBar(): Unit = runBlocking {
        frontDisplay.apply {
            clear()
            setUpperBar(listOf(0, 1, 3, 19))
            assertDisplayContent(
                "** *               *",
                "                    ",
                "                    ",
            )

            setUpperBar(listOf(0, 1, 3, 19), invert = true)
            assertDisplayContent(
                "  * *************** ",
                "                    ",
                "                    ",
            )

            setUpperBar(
                listOf(
                    0, 1, 0, 0, 1,
                    1, 1, 0, 0, 1,
                    0, 1, 1, 0, 1,
                    0, 1, 1, 0, 1,
                ).map { it != 0 }
                    .toBooleanArray())
            setStaticText(18, "Lorem ipsum ąęęśłłt")
            assertDisplayContent(
                " *  ***  * ** * ** *",
                "                  Lo",
                "rem ipsum ąęęśłłt   ",
            )

            clear()
            assertDisplayContent(
                "                    ",
                "                    ",
                "                    ",
            )
        }
    }

    @Test
    fun testSetStaticText(): Unit = runBlocking {
        frontDisplay.apply {
            clear()
            setStaticText(5, "Hello")
            assertDisplayContent(
                "                    ",
                "     Hello          ",
                "                    ",
            )

            setStaticText(35, "End")
            assertDisplayContent(
                "                    ",
                "     Hello          ",
                "               End  ",
            )

            clear()
            setStaticText(0, "Start")
            assertDisplayContent(
                "                    ",
                "Start               ",
                "                    ",
            )

            clear()
            assertDisplayContent(
                "                    ",
                "                    ",
                "                    ",
            )
        }
    }
}