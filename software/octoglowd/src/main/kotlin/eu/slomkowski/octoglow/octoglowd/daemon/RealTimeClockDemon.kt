package eu.slomkowski.octoglow.octoglowd.daemon


import eu.slomkowski.octoglow.octoglowd.StateMachine
import eu.slomkowski.octoglow.octoglowd.daemon.FrontDisplayDemon2.*
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.now
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


@OptIn(ExperimentalTime::class)
class RealTimeClockDemon(
    private val hardware: Hardware,
    private val clock: Clock = Clock.System,
) : PollingDemon(logger, 200.milliseconds) {

    companion object {
        private val logger = KotlinLogging.logger {}

        private val maxTimeTheFrontDisplayNumberIsVisible = 400.milliseconds
    }

    data class DisplayContent(
        val hour: Int,
        val minute: Int,
        val upperDot: Boolean,
        val lowerDot: Boolean,
    ) {
        companion object {
            fun ofTimestamp(dt: LocalDateTime): DisplayContent = when (dt.second % 2) {
                0 -> DisplayContent(dt.hour, dt.minute, dt.second >= 20, dt.second < 20 || dt.second > 40)
                else -> DisplayContent(dt.hour, dt.minute, upperDot = false, lowerDot = false)
            }
        }
    }

    sealed class State {
        data class DisplayTime(val displayContent: DisplayContent) : State()
        data class DisplayFrontDisplayNumber(
            val number: Int,
            val visibleSince: Instant,
        ) : State()
    }

    sealed class Event {
        data class FrontDisplayNumberSet(val number: Int) : Event()
        data class UpdateTime(val time: LocalDateTime) : Event()
    }

    sealed class SideEffect {
        data class DrawTimer(val displayContent: DisplayContent) : SideEffect()
        data class DrawNumberSet(val number: Int) : SideEffect()
    }

    private val stateExecutor: StateMachine<State, Event, SideEffect> = StateMachine.create {
        initialState(State.DisplayFrontDisplayNumber(0, Instant.DISTANT_PAST))

        // todo add timeout

        state<State.DisplayTime> {
            on<Event.UpdateTime> { event ->
                val newDisplayContent = DisplayContent.ofTimestamp(event.time)
                if (displayContent != newDisplayContent) {
                    transitionTo(State.DisplayTime(newDisplayContent), SideEffect.DrawTimer(newDisplayContent))
                } else {
                    dontTransition()
                }
            }

            on<Event.FrontDisplayNumberSet> { event ->
                transitionTo(State.DisplayFrontDisplayNumber(event.number, clock.now()), SideEffect.DrawNumberSet(event.number))
            }
        }

        state<State.DisplayFrontDisplayNumber> {
            on<Event.UpdateTime> { event ->
                if (clock.now() > visibleSince + maxTimeTheFrontDisplayNumberIsVisible) {
                    val newDisplayContent = DisplayContent.ofTimestamp(event.time)
                    transitionTo(State.DisplayTime(newDisplayContent), SideEffect.DrawTimer(newDisplayContent))
                } else {
                    dontTransition()
                }
            }

            on<Event.FrontDisplayNumberSet> { event ->
                transitionTo(State.DisplayFrontDisplayNumber(event.number, clock.now()), SideEffect.DrawNumberSet(event.number))
            }
        }

        onTransition {
            val validTransition = it as? StateMachine.Transition.Valid ?: return@onTransition
            when (val se = validTransition.sideEffect) {
                is SideEffect.DrawNumberSet -> hardware.clockDisplay.setFrontDisplayViewNumber(se.number)
                is SideEffect.DrawTimer -> se.displayContent.apply {
                    hardware.clockDisplay.setDisplay(hour, minute, upperDot, lowerDot)
                }

                null -> {
                    // no side effect
                }
            }
        }
    }

    override suspend fun poll() {
        stateExecutor.transition(Event.UpdateTime(now().toLocalDateTime(TimeZone.currentSystemDefault())))
    }

    suspend fun setFrontDisplayViewNumber(number: Int) {
        require(number in 0..99) { "Front display number must be between 0 and 99" }
        stateExecutor.transition(Event.FrontDisplayNumberSet(number))
    }
}
