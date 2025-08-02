package eu.slomkowski.octoglow.octoglowd.hardware

import eu.slomkowski.octoglow.octoglowd.testConfig
import io.helins.linux.i2c.I2CBus
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalTime::class)
class HardwareParameterResolver : ParameterResolver {

    class HardwareResource(val hardware: Hardware) : ExtensionContext.Store.CloseableResource {
        override fun close() {
            hardware.close()
        }
    }

    override fun supportsParameter(context: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return context.parameter.type == Hardware::class.java
    }

    override fun resolveParameter(context: ParameterContext, extensionContext: ExtensionContext): Any {
        val store = extensionContext.getStore(ExtensionContext.Namespace.create(Hardware::class))
        val hardwareResource = store.getOrComputeIfAbsent("hardware") {
            val bus = I2CBus(testConfig.i2cBus)
            val hardware = HardwareReal(bus)
            HardwareResource(hardware)
        } as HardwareResource

        return hardwareResource.hardware
    }
}
