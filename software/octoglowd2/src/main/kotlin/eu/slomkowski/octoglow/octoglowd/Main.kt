package eu.slomkowski.octoglow.octoglowd

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.view.AboutView
import eu.slomkowski.octoglow.octoglowd.view.OutdoorWeatherView
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import java.nio.file.Paths

object ConfKey : ConfigSpec() {
    val i2cBusFile by required<Path>()
    val databaseFile by optional<Path>(Paths.get("data.db"))
}

fun main(args: Array<String>) {

    val config = Config { addSpec(ConfKey) }
            .from.yaml.file("config.yml")
            .from.env()
            .from.systemProperties()

    val hardware = Hardware(config[ConfKey.i2cBusFile])
    val database = DatabaseLayer(config[ConfKey.databaseFile])

    val frontDisplayViews = listOf(
            AboutView(hardware),
            OutdoorWeatherView(database, hardware))

    runBlocking {
        joinAll(createRealTimeClockController(hardware.clockDisplay),
                createFrontDisplayController(hardware.frontDisplay, frontDisplayViews))
    }
}

