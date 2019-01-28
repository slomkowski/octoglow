package eu.slomkowski.octoglow.octoglowd

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec

object TestConfKey : ConfigSpec() {
    val i2cBus by required<Int>()
}

val testConfig = Config { addSpec(TestConfKey) }.from.yaml.file("test-config.yml")
