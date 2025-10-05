@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class LocalSensorsDataHarvester(
    private val config: Config,
    private val hardware: Hardware,
    eventBus: DataSnapshotBus,
) : DataHarvester(logger, 2.minutes, eventBus) {
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

                publish(
                    StandardDataSnapshot(
                        now,
                        pollingInterval,
                        listOf(
                            StandardDataSample(Bme280Temperature, Result.success(bme280data.temperature)),
                            StandardDataSample(Bme280Humidity, Result.success(bme280data.humidity)),
                            StandardDataSample(RealPressure, Result.success(bme280data.realPressure)),
                            StandardDataSample(MSLPressure, Result.success(mslPressure)),
                        )
                    )
                )

                bme280data
            } catch (e: Exception) {
                logger.error(e) { "Error while reading BME280" }
                publish(
                    StandardDataSnapshot(
                        now,
                        pollingInterval,
                        listOf(
                            StandardDataSample(Bme280Temperature, Result.failure(e)),
                            StandardDataSample(Bme280Humidity, Result.failure(e)),
                            StandardDataSample(RealPressure, Result.failure(e)),
                            StandardDataSample(MSLPressure, Result.failure(e)),
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
                logger.error(e) { "Error while reading Light sensor" }
                Result.failure(e)
            }
            publish(StandardDataSnapshot(now, pollingInterval, listOf(StandardDataSample(LightSensorValue, result))))
        }

        launch {
            try {
                val scd40data = hardware.scd40.readMeasurementWithWaiting()
                logger.info { "Got SCD40 report : ${scd40data}." }
                publish(
                    StandardDataSnapshot(
                        now,
                        pollingInterval,
                        listOf(
                            StandardDataSample(IndoorCo2, Result.success(scd40data.co2)),
                            StandardDataSample(Scd40Humidity, Result.success(scd40data.humidity)),
                            StandardDataSample(Scd40Temperature, Result.success(scd40data.temperature))
                        )
                    )
                )
            } catch (e: Exception) {
                logger.error(e) { "Error while reading SCD40" }
                publish(
                    StandardDataSnapshot(
                        now,
                        pollingInterval,
                        listOf(
                            StandardDataSample(IndoorCo2, Result.failure(e)),
                            StandardDataSample(Scd40Humidity, Result.failure(e)),
                            StandardDataSample(Scd40Temperature, Result.failure(e))
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

