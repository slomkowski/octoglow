package eu.slomkowski.octoglow.octoglowd.statemachine

import eu.slomkowski.octoglow.octoglowd.StateMachine
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test

class MatterStateMachine {

    private val logger = mockk<Logger>().apply {
        every { log(any()) } just Runs
    }
    private val stateMachine = StateMachine.create<State, Event, SideEffect> {
        initialState(State.Solid)
        state<State.Solid> {
            on<Event.OnMelted> {
                transitionTo(State.Liquid, SideEffect.LogMelted)
            }
        }
        state<State.Liquid> {
            on<Event.OnFrozen> {
                transitionTo(State.Solid, SideEffect.LogFrozen)
            }
            on<Event.OnVaporized> {
                transitionTo(State.Gas, SideEffect.LogVaporized)
            }
        }
        state<State.Gas> {
            on<Event.OnCondensed> {
                transitionTo(State.Liquid, SideEffect.LogCondensed)
            }
        }
        onTransition {
            val validTransition = it as? StateMachine.Transition.Valid ?: return@onTransition
            when (validTransition.sideEffect!!) {
                SideEffect.LogMelted -> logger.log(ON_MELTED_MESSAGE)
                SideEffect.LogFrozen -> logger.log(ON_FROZEN_MESSAGE)
                SideEffect.LogVaporized -> logger.log(ON_VAPORIZED_MESSAGE)
                SideEffect.LogCondensed -> logger.log(ON_CONDENSED_MESSAGE)
            }
        }
    }

    @Test
    fun initialState_shouldBeSolid() {
        // Then
        Assertions.assertThat(stateMachine.state).isEqualTo(State.Solid)
    }

    @Test
    fun givenStateIsSolid_onMelted_shouldTransitionToLiquidStateAndLog(): Unit = runBlocking {
        // Given
        val stateMachine = givenStateIs(State.Solid)

        // When
        val transition = stateMachine.transition(Event.OnMelted)

        // Then
        Assertions.assertThat(stateMachine.state).isEqualTo(State.Liquid)
        Assertions.assertThat(transition).isEqualTo(
            StateMachine.Transition.Valid(State.Solid, Event.OnMelted, State.Liquid, SideEffect.LogMelted)
        )
        verify { logger.log(ON_MELTED_MESSAGE) }
    }

    @Test
    fun givenStateIsLiquid_onFroze_shouldTransitionToSolidStateAndLog() = runBlocking {
        // Given
        val stateMachine = givenStateIs(State.Liquid)

        // When
        val transition = stateMachine.transition(Event.OnFrozen)

        // Then
        Assertions.assertThat(stateMachine.state).isEqualTo(State.Solid)
        Assertions.assertThat(transition).isEqualTo(
            StateMachine.Transition.Valid(State.Liquid, Event.OnFrozen, State.Solid, SideEffect.LogFrozen)
        )
        verify { logger.log(ON_FROZEN_MESSAGE) }
    }

    @Test
    fun givenStateIsLiquid_onVaporized_shouldTransitionToGasStateAndLog() = runBlocking {
        // Given
        val stateMachine = givenStateIs(State.Liquid)

        // When
        val transition = stateMachine.transition(Event.OnVaporized)

        // Then
        Assertions.assertThat(stateMachine.state).isEqualTo(State.Gas)
        Assertions.assertThat(transition).isEqualTo(
            StateMachine.Transition.Valid(State.Liquid, Event.OnVaporized, State.Gas, SideEffect.LogVaporized)
        )
        verify { logger.log(ON_VAPORIZED_MESSAGE) }
    }

    @Test
    fun givenStateIsGas_onCondensed_shouldTransitionToLiquidStateAndLog() = runBlocking {
        // Given
        val stateMachine = givenStateIs(State.Gas)

        // When
        val transition = stateMachine.transition(Event.OnCondensed)

        // Then
        Assertions.assertThat(stateMachine.state).isEqualTo(State.Liquid)
        Assertions.assertThat(transition).isEqualTo(
            StateMachine.Transition.Valid(State.Gas, Event.OnCondensed, State.Liquid, SideEffect.LogCondensed)
        )
        verify { logger.log(ON_CONDENSED_MESSAGE) }
    }

    private fun givenStateIs(state: State): StateMachine<State, Event, SideEffect> {
        return stateMachine.with { initialState(state) }
    }

    companion object {
        const val ON_MELTED_MESSAGE = "I melted"
        const val ON_FROZEN_MESSAGE = "I froze"
        const val ON_VAPORIZED_MESSAGE = "I vaporized"
        const val ON_CONDENSED_MESSAGE = "I condensed"

        sealed class State {
            data object Solid : State()
            data object Liquid : State()
            data object Gas : State()
        }

        sealed class Event {
            data object OnMelted : Event()
            data object OnFrozen : Event()
            data object OnVaporized : Event()
            data object OnCondensed : Event()
        }

        sealed class SideEffect {
            data object LogMelted : SideEffect()
            data object LogFrozen : SideEffect()
            data object LogVaporized : SideEffect()
            data object LogCondensed : SideEffect()
        }

        interface Logger {
            fun log(message: String)
        }
    }
}