package eu.slomkowski.octoglow.octoglowd.hardware

const val MAX_BRIGHTNESS = 5

interface HasBrightness {
    suspend fun setBrightness(brightness: Int)
}