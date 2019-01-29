package eu.slomkowski.octoglow.octoglowd

import com.uchuhimo.konf.ConfigSpec
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalTime

object CryptocurrenciesKey : ConfigSpec() {
    val coin1 by required<String>()
    val coin2 by required<String>()
    val coin3 by required<String>()
}

object ConfKey : ConfigSpec() {
    val i2cBus by required<Int>()
    val databaseFile by optional<Path>(Paths.get("data.db"))
    val goToSleepTime by required<LocalTime>()
    val cryptocurrencies by required<CryptocurrenciesKey>()
}
