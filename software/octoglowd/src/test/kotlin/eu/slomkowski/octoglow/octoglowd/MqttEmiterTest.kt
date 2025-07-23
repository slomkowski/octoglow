package eu.slomkowski.octoglow.octoglowd

import eu.slomkowski.octoglow.octoglowd.mqtt.MqttEmiter
import eu.slomkowski.octoglow.octoglowd.mqtt.createDiscoveryMessageDto
import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Test

class MqttEmiterTest {

    private val logger = KotlinLogging.logger { }

    @Test
    fun testSendHomeassistantDiscoveryMessage() {
        logger.info { createDiscoveryMessageDto() }
    }
}