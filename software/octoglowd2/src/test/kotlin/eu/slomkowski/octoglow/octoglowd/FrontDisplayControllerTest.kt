package eu.slomkowski.octoglow.octoglowd

import eu.slomkowski.octoglow.octoglowd.controller.FrontDisplayController.Companion.updateViewIndex
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FrontDisplayControllerTest {

    @Test
    fun testUpdateViewIndex() {
        assertEquals(3, updateViewIndex(2, 1, 5))
        assertEquals(0, updateViewIndex(2, 1, 3))
        assertEquals(1, updateViewIndex(2, -1, 3))
        assertEquals(0, updateViewIndex(2, -2, 3))
        assertEquals(2, updateViewIndex(2, -3, 3))
        assertEquals(1, updateViewIndex(2, -6, 5))
        assertEquals(33, updateViewIndex(0, -37, 35))
        assertEquals(0, updateViewIndex(0, -36, 2))
        assertEquals(1, updateViewIndex(0, -9, 2))
    }
}