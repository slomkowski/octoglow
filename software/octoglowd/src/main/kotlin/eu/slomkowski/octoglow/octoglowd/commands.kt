package eu.slomkowski.octoglow.octoglowd

interface Command

data class DialTurned(val delta: Int) : Command
object DialPressed : Command

interface PublishStateCommand : Command

interface ChangeStateCommand : Command {
    val enabled: Boolean
}

data class MagicEyeChangeStateCommand(override val enabled: Boolean) : ChangeStateCommand
data class BacklightChangeStateCommand(override val enabled: Boolean) : ChangeStateCommand

data object MagicEyePublishStateCommand : PublishStateCommand
data object BacklightPublishStateCommand : PublishStateCommand