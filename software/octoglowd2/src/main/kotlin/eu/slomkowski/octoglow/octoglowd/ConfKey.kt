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

object ConfKey : ConfigSpec() {
    val i2cBus by required<Int>()
    val databaseFile by optional<Path>(Paths.get("data.db"))
    val locale by optional(Locale("pl_PL"))
}
