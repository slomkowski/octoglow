@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.datacollectors

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class LocalSensorsDataCollector(
    private val config: Config,
    private val hardware: Hardware,
    eventBus: HistoricalValuesEvents,
) : DataCollector(logger, 2.minutes, eventBus) {
    companion object {
        private val logger = KotlinLogging.logger { }
    }

    @Volatile
    private var realPressure: Double? = null

    override suspend fun pollForNewData(now: Instant): Unit = coroutineScope {

        val bme280dataAsync = async {
            try {
                val bme280data = hardware.bme280.readReport()
                val elevation = config.geoPosition.elevation
                val mslPressure = bme280data.getMeanSeaLevelPressure(elevation)

                logger.info { "Got BMP280 report : ${bme280data}." }
                logger.info { "Mean sea-level pressure at $elevation m is $mslPressure hPa." }

                publishPacket(
                    StandardMeasurementReport(
                        now,
                        pollInterval,
                        listOf(
                            StandardMeasurement(Bme280Temperature, Result.success(bme280data.temperature)),
                            StandardMeasurement(Bme280Humidity, Result.success(bme280data.humidity)),
                            StandardMeasurement(RealPressure, Result.success(bme280data.realPressure)),
                            StandardMeasurement(MSLPressure, Result.success(mslPressure)),
                        )
                    )
                )

                bme280data
            } catch (e: Exception) {
                logger.error(e) { "Error while reading BME280." }
                publishPacket(
                    StandardMeasurementReport(
                        now,
                        pollInterval,
                        listOf(
                            StandardMeasurement(Bme280Temperature, Result.failure(e)),
                            StandardMeasurement(Bme280Humidity, Result.failure(e)),
                            StandardMeasurement(RealPressure, Result.failure(e)),
                            StandardMeasurement(MSLPressure, Result.failure(e)),
                        )
                    )
                )
                null
            }
        }

        launch {
            val result = try {
                Result.success(hardware.clockDisplay.retrieveLightSensorMeasurement().toDouble())
            } catch (e: Exception) {
                logger.error(e) { "Error while reading Light sensor." }
                Result.failure(e)
            }
            publishPacket(StandardMeasurementReport(now, pollInterval, listOf(StandardMeasurement(LightSensorValue, result))))
        }

        launch {
            try {
                val scd40data = hardware.scd40.readMeasurementWithWaiting()
                logger.info { "Got SCD40 report : ${scd40data}." }
                publishPacket(
                    StandardMeasurementReport(
                        now,
                        pollInterval,
                        listOf(
                            StandardMeasurement(IndoorCo2, Result.success(scd40data.co2)),
                            StandardMeasurement(Scd40Humidity, Result.success(scd40data.humidity)),
                            StandardMeasurement(Scd40Temperature, Result.success(scd40data.temperature))
                        )
                    )
                )
            } catch (e: Exception) {
                logger.error(e) { "Error while reading SCD40." }
                publishPacket(
                    StandardMeasurementReport(
                        now,
                        pollInterval,
                        listOf(
                            StandardMeasurement(IndoorCo2, Result.failure(e)),
                            StandardMeasurement(Scd40Humidity, Result.failure(e)),
                            StandardMeasurement(Scd40Temperature, Result.failure(e))
                        )
                    )
                )
            }
        }

        val bme280data = bme280dataAsync.await()
        if (bme280data?.realPressure != null) {
            if (realPressure == null || realPressure != bme280data.realPressure) {
                realPressure = bme280data.realPressure
                launch {
                    hardware.scd40.setAmbientPressure(bme280data.realPressure)
                }
            }
        } else {
            logger.warn { "Cannot update SCD40's ambient pressure since reading it from BME280 failed." }
        }
    }
}

