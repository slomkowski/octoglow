package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.testConfig
import io.dvlopt.linux.i2c.I2CBus
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class HardwareParameterResolver : ParameterResolver {

    override fun supportsParameter(context: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return context.parameter.type == Hardware::class.java
    }

    override fun resolveParameter(context: ParameterContext, extensionContext: ExtensionContext): Any {
        val bus = I2CBus(testConfig.i2cBus)
        return Hardware(bus)
    }
}
