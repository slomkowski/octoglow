package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.TestConfKey
import eu.slomkowski.octoglow.octoglowd.testConfig
import io.dvlopt.linux.i2c.I2CBus
import io.dvlopt.linux.i2c.I2CFunctionality
import mu.KLogging
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

class I2CBusParameterResolver : ParameterResolver {

    companion object : KLogging()

    override fun supportsParameter(context: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return context.parameter.type == I2CBus::class.java
    }

    override fun resolveParameter(context: ParameterContext, extensionContext: ExtensionContext): Any {
        val bus = I2CBus(testConfig[TestConfKey.i2cBus])
        bus.functionalities.apply {
            logger.debug("I2C transactions: {}", can(I2CFunctionality.TRANSACTIONS))
        }
        return bus
    }
}
