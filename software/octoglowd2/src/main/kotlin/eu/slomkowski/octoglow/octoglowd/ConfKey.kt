package eu.slomkowski.octoglow.octoglowd

import com.uchuhimo.konf.ConfigSpec
import java.nio.file.Path
import java.nio.file.Paths
import java.time.LocalTime

object ConfKey : ConfigSpec() {
    val i2cBus by required<Int>()
    val databaseFile by optional<Path>(Paths.get("data.db"))
    val goToSleepTime by required<LocalTime>()
}
