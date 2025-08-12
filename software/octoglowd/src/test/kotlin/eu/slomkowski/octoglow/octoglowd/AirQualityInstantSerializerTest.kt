@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.test.assertFails
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class AirQualityInstantSerializerTest {
    @Test
    fun deserialize1() {
        val json = Json.decodeFromString(AirQualityInstantSerializer, "\"2024-01-01 12:00:00\"")
        assertEquals(Instant.parse("2024-01-01T11:00:00Z"), json)
    }

    @Test
    fun deserialize2() {
        assertFails {
            Json.decodeFromString(AirQualityInstantSerializer, "\"invalid_str\"")
        }
    }
}