package eu.slomkowski.octoglow.octoglowd

import com.uchuhimo.konf.ConfigSpec
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalTime
import java.util.*

//todo add validators to values
object GeoPosKey : ConfigSpec("geo-position") {
    val latitude by required<Double>()
    val longitude by required<Double>()
    val elevation by required<Double>() // in meters, above sea level
}

object SleepKey : ConfigSpec("sleep") {
    val startAt by required<LocalTime>()
    val duration by optional<Duration>(Duration.ofHours(8))
}

object CryptocurrenciesKey : ConfigSpec("cryptocurrencies") {
    val coin1 by required<String>()
    val coin2 by required<String>()
    val coin3 by required<String>()
    val diffChartFraction by optional(0.005)
}

object NbpKey : ConfigSpec("nbp") {
    val currency1 by required<String>()
    val currency2 by required<String>()
    val currency3 by required<String>()
    val diffChartFraction by optional(0.005)
}

object NetworkViewKey : ConfigSpec("network-info") {
    val pingBinary by optional(Paths.get("/bin/ping"))
    val pingAddress by required<String>(description = "IP address or domain used to check internet access on network view")
}

object ConfKey : ConfigSpec("") {
    val i2cBus by required<Int>()
    val databaseFile by optional<Path>(Paths.get("data.db"))
    val locale by optional(Locale("pl", "PL"))
    val ringAtStartup by optional(false, description = "Should the bell ring at the application startup.")
    val ringAtError by optional(true, description = "Ring when demon error occurs.")
    val viewAutomaticCycleTimeout by optional<Duration>(Duration.ofSeconds(40),
            description = "When the dial is used, the device goes to manual mode. After this timeout, it switches back to automatic views cycling.")
}
