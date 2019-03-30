package eu.slomkowski.octoglow.octoglowd.hardware

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.ConfKey
import io.dvlopt.linux.i2c.I2CBus
import kotlinx.coroutines.newSingleThreadContext
import kotlinx.coroutines.runBlocking
import java.time.Duration

interface Hardware : HasBrightness, AutoCloseable {
    val clockDisplay: ClockDisplay
    val frontDisplay: FrontDisplay
    val geiger: Geiger
    val dac: Dac
    val bme280: Bme280

    override suspend fun setBrightness(brightness: Int)

    override fun close()
}

class PhysicalHardware(private val config: Config) : Hardware {

    private val threadContext = newSingleThreadContext("hardware")

    private val bus = I2CBus(config[ConfKey.i2cBus])

    override val clockDisplay = ClockDisplay(threadContext, bus)

    override val frontDisplay = FrontDisplay(threadContext, bus)

    override val geiger = Geiger(threadContext, bus)

    override val dac = Dac(threadContext, bus)

    override val bme280: Bme280
        get() = TODO("not implemented") //To change initializer of created properties use File | Settings | File Templates.

    // override val bme280 = Bme280(threadContext, bus)

    init {
        if (config[ConfKey.ringAtStartup]) {
            runBlocking { clockDisplay.ringBell(Duration.ofMillis(70)) }
        }

        runBlocking {
            frontDisplay.apply {
                clear()
                setStaticText(0, "Initializing...")
            }
        }
    }

    override suspend fun setBrightness(brightness: Int) {
        listOf<HasBrightness>(clockDisplay, frontDisplay, geiger).forEach { it.setBrightness(brightness) }
    }

    override fun close() {
        listOf<AutoCloseable>(clockDisplay, frontDisplay, geiger, dac /*todo bme280 */).forEach { it.close() }
    }
}