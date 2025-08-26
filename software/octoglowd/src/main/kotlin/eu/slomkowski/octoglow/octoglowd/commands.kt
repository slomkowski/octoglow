package eu.slomkowski.octoglow.octoglowd

interface Command

data class DialTurned(val delta: Int) : Command
object DialPressed : Command

data class MagicEyeChangeStateCommand(val enabled: Boolean) : Command

data object MagicEyePublishStateCommand : Command