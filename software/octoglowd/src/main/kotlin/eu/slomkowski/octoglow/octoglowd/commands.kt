package eu.slomkowski.octoglow.octoglowd

interface Command

data class DialTurned(val delta: Int) : Command
object DialPressed : Command
