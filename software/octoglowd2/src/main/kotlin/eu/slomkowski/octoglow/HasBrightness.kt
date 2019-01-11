package eu.slomkowski.octoglow

interface HasBrightness {
    suspend fun setBrightness(brightness: Int)
}