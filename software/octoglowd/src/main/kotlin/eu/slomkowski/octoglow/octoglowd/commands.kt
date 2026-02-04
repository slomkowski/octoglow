package eu.slomkowski.octoglow.octoglowd

interface Command

data class DialTurned(val delta: Int) : Command
object DialPressedLong : Command
object DialPressedShort : Command

interface PublishStateCommand : Command

interface ChangeStateCommand : Command {
    val enabled: Boolean
}

interface ToggleStateCommand : Command

data class MagicEyeChangeStateCommand(override val enabled: Boolean) : ChangeStateCommand
data class BacklightChangeStateCommand(override val enabled: Boolean) : ChangeStateCommand

data object MagicEyeToggleStateCommand : ToggleStateCommand
data object BacklightToggleStateCommand : ToggleStateCommand

data object MagicEyePublishStateCommand : PublishStateCommand
data object BacklightPublishStateCommand : PublishStateCommand