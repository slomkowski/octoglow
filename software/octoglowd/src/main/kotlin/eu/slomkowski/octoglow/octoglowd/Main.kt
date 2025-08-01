package eu.slomkowski.octoglow.octoglowd

import eu.slomkowski.octoglow.octoglowd.daemon.AnalogGaugeDemon
import eu.slomkowski.octoglow.octoglowd.daemon.BrightnessDemon
import eu.slomkowski.octoglow.octoglowd.daemon.FrontDisplayDemon2
import eu.slomkowski.octoglow.octoglowd.daemon.RealTimeClockDemon
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.*
import eu.slomkowski.octoglow.octoglowd.datacollectors.*
import eu.slomkowski.octoglow.octoglowd.hardware.HardwareReal
import eu.slomkowski.octoglow.octoglowd.mqtt.MqttEmiter
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import java.nio.file.Paths
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun main() {
    val logger = KotlinLogging.logger {}

    logger.info { "Starting Octoglow daemon..." }

    val config = Config.parse(Paths.get("config.json"))

    val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val eventBus = HistoricalValuesEvents()

    val mqttEmiter = MqttEmiter(config, workerScope)
    val hardware = HardwareReal(config)
    val database = DatabaseLayer(config.databaseFile, mqttEmiter, eventBus)
    val closeable = listOf(database, hardware)

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "Shutting down. Calling workers to stop." }
        workerScope.cancel()
        runBlocking {
            workerScope.coroutineContext[Job]?.join()
        }
        closeable.forEach { it.close() }
        logger.info { "Shut down." }
    })

    val frontDisplayViews = listOf(
        CalendarView(config, hardware),
        GeigerView(config, database, hardware),
        CryptocurrencyView(config, database, hardware),
        SimpleMonitorView(config, hardware),
        AirQualityView(config, database, hardware),
        NetworkView(config, hardware),
        LocalSensorView(config, database, hardware),
        TodoistView(config, hardware),
    )

    val frontDisplayViews2 = listOf(
        CalendarView(config, hardware),
        GeigerView(config, database, hardware),
        CryptocurrencyView(config, database, hardware),
        AirQualityView(config, database, hardware),
        LocalSensorView(config, database, hardware),
        JvmMemoryView(hardware),
        NbpView(config, hardware),
        WeatherSensorView(config, database, hardware),
    )

    val brightnessDaemon = BrightnessDemon(config, database, hardware)

    val menus = listOf(
        BrightnessMenu(brightnessDaemon)
    )

    val realTimeClockDemon = RealTimeClockDemon(hardware)

    val demons = listOf(
        database,
        realTimeClockDemon,
        FrontDisplayDemon2(config, workerScope, hardware, frontDisplayViews2, menus, eventBus, realTimeClockDemon),
        AnalogGaugeDemon(hardware),
        brightnessDaemon,
        RadmonOrgSender(config, eventBus),

        AirQualityDataCollector(config, eventBus),
        CryptocurrencyDataCollector(config, eventBus),
        GeigerDataCollector(hardware, eventBus),
        LocalSensorsDataCollector(config, hardware, eventBus),
        NbpDataCollector(config, eventBus),
        RadioWeatherSensorDataCollector(config, hardware, eventBus),
    )

    runBlocking {
        demons.forEach { it.createJobs(workerScope) }

        awaitCancellation()
    }
}
