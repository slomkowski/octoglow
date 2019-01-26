package eu.slomkowski.octoglow.hardware

interface HasBrightness {
    suspend fun setBrightness(brightness: Int)
}