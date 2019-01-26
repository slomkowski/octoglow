package eu.slomkowski.octoglow.octoglowd.hardware

interface HasBrightness {
    suspend fun setBrightness(brightness: Int)
}