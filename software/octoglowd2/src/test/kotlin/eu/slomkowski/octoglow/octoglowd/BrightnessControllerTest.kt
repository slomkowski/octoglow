package eu.slomkowski.octoglow.octoglowd

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class BrightnessControllerTest {
  @Test
  fun testCalculateBrightnessFraction() {
  BrightnessController.calculateBrightnessFraction(LocalDateTime.of(2019, 1, 27, 12, 34), LocalTime.of(23, 16))
  }
 }