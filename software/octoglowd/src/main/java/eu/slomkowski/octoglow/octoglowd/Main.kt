package eu.slomkowski.octoglow.octoglowd

import io.reactivex.Observable
import io.reactivex.rxkotlin.Observables
import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import jdk.dio.DeviceManager;
import jdk.dio.i2cbus.I2CDevice;
import jdk.dio.i2cbus.I2CDeviceConfig;
import java.nio.ByteBuffer

private val logger = KotlinLogging.logger {}


data class ExternalWeatherReport(
        val temperature: Double,
        val humidity : Int,
        val weakBattery : Boolean
) {
    init {
        check(temperature > -50.0 && temperature < 70.0)
        check(humidity >=0 || humidity <= 100)
    }
}

fun main(args : Array<String>) {

    val i2Config = I2CDeviceConfig.Builder()
            .setControllerNumber(1)
            .setAddress(0x10, I2CDeviceConfig.ADDR_SIZE_7)
            .build()

    DeviceManager.open(i2Config).use {
        val byteBuffer = ByteBuffer.allocate(4)
        it.write(4)
        it.read(byteBuffer)
        logger.info { byteBuffer }
    }

    val mainTimerStream = Observable.interval(500, TimeUnit.MILLISECONDS)

    mainTimerStream.subscribe {
        logger.info { "Recevied event $it" }
    }

    mainTimerStream.subscribe {
        logger.info { "Recevied event434 $it" }
    }


}
