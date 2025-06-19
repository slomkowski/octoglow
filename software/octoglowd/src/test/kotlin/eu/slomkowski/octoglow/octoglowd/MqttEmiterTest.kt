package eu.slomkowski.octoglow.octoglowd

import io.github.oshai.kotlinlogging.KotlinLogging
import org.junit.jupiter.api.Test

class MqttEmiterTest {

    private val logger = KotlinLogging.logger { }

    @Test
    fun testWriteDiscoveryMessage() {
        logger.info { MqttEmiter.createDiscoveryMessageDto() }
    }
}