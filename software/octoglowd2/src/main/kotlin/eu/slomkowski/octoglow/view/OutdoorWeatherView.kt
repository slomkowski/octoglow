package eu.slomkowski.octoglow.view

import eu.slomkowski.octoglow.DatabaseLayer
import eu.slomkowski.octoglow.hardware.ClockDisplay

class OutdoorWeatherView(
        private val databaseLayer: DatabaseLayer,
        private val clockDisplay: ClockDisplay) : FrontDisplayView{

    override fun redrawDisplay() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun poolStateUpdate(): Boolean {
        TODO()
    }
}