package eu.slomkowski.octoglow

import io.dvlopt.linux.i2c.I2CBus
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver

class I2CBusParameterResolver : ParameterResolver {

    override fun supportsParameter(context: ParameterContext, extensionContext: ExtensionContext): Boolean {
        return context.parameter.type == I2CBus::class.java
    }

    override fun resolveParameter(context: ParameterContext, extensionContext: ExtensionContext): Any {
        return I2CBus(4)
    }
}
