package eu.slomkowski.octoglow.octoglowd.statemachine

import eu.slomkowski.octoglow.octoglowd.StateMachine
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@Nested
open class ConstantStateMachine {

    @Nested
    inner class WithInitialState {

        private val onTransitionListener1 = mockk<(StateMachine.Transition<String, Int, String>) -> Unit>(relaxed = true)
        private val onTransitionListener2 = mockk<(StateMachine.Transition<String, Int, String>) -> Unit>(relaxed = true)
        private val onStateCEnterListener1 = mockk<String.(Int) -> Unit>(relaxed = true)
        private val onStateCEnterListener2 = mockk<String.(Int) -> Unit>(relaxed = true)
        private val onStateAExitListener1 = mockk<String.(Int) -> Unit>(relaxed = true)
        private val onStateAExitListener2 = mockk<String.(Int) -> Unit>(relaxed = true)
        private val stateMachine = StateMachine.create<String, Int, String> {
            initialState(STATE_A)
            state(STATE_A) {
                onExit(onStateAExitListener1)
                onExit(onStateAExitListener2)
                on(EVENT_1) {
                    transitionTo(STATE_B)
                }
                on(EVENT_2) {
                    transitionTo(STATE_C)
                }
                on(EVENT_4) {
                    transitionTo(STATE_D)
                }
            }
            state(STATE_B) {
                on(EVENT_3) {
                    transitionTo(STATE_C, SIDE_EFFECT_1)
                }
            }
            state(STATE_C) {
                onEnter(onStateCEnterListener1)
                onEnter(onStateCEnterListener2)
            }
            onTransition(onTransitionListener1)
            onTransition(onTransitionListener2)
        }

        @Test
        fun state_shouldReturnInitialState() {
            // When
            val state = stateMachine.state

            // Then
            Assertions.assertThat(state).isEqualTo(STATE_A)
        }

        @Test
        fun transition_givenValidEvent_shouldReturnTrue(): Unit = runBlocking {
            // When
            val transitionFromStateAToStateB = stateMachine.transition(EVENT_1)

            // Then
            Assertions.assertThat(transitionFromStateAToStateB).isEqualTo(
                StateMachine.Transition.Valid(STATE_A, EVENT_1, STATE_B, null)
            )

            // When
            val transitionFromStateBToStateC = stateMachine.transition(EVENT_3)

            // Then
            Assertions.assertThat(transitionFromStateBToStateC).isEqualTo(
                StateMachine.Transition.Valid(STATE_B, EVENT_3, STATE_C, SIDE_EFFECT_1)
            )
        }

        @Test
        fun transition_givenValidEvent_shouldCreateAndSetNewState(): Unit = runBlocking {
            // When
            stateMachine.transition(EVENT_1)

            // Then
            Assertions.assertThat(stateMachine.state).isEqualTo(STATE_B)

            // When
            stateMachine.transition(EVENT_3)

            // Then
            Assertions.assertThat(stateMachine.state).isEqualTo(STATE_C)
        }

        @Test
        fun transition_givenValidEvent_shouldTriggerOnStateChangeListener(): Unit = runBlocking {
            // When
            stateMachine.transition(EVENT_1)

            // Then
            verify { onTransitionListener1.invoke(StateMachine.Transition.Valid(STATE_A, EVENT_1, STATE_B, null)) }

            // When
            stateMachine.transition(EVENT_3)

            // Then
            verify { onTransitionListener2.invoke(StateMachine.Transition.Valid(STATE_B, EVENT_3, STATE_C, SIDE_EFFECT_1)) }
        }

        @Test
        fun transition_givenValidEvent_shouldTriggerOnEnterListeners(): Unit = runBlocking {
            // When
            stateMachine.transition(EVENT_2)

            // Then
            verify { onStateCEnterListener1.invoke(STATE_C, EVENT_2) }
            verify { onStateCEnterListener2.invoke(STATE_C, EVENT_2) }
        }

        @Test
        fun transition_givenValidEvent_shouldTriggerOnExitListeners(): Unit = runBlocking {
            // When
            stateMachine.transition(EVENT_2)

            // Then
            verify { onStateAExitListener1.invoke(STATE_A, EVENT_2) }
            verify { onStateAExitListener2.invoke(STATE_A, EVENT_2) }
        }

        @Test
        fun transition_givenInvalidEvent_shouldReturnInvalidTransition(): Unit = runBlocking {
            // When
            val fromState = stateMachine.state
            val transition = stateMachine.transition(EVENT_3)

            // Then
            Assertions.assertThat(transition).isEqualTo(
                StateMachine.Transition.Invalid<String, Int, String>(STATE_A, EVENT_3)
            )
            Assertions.assertThat(stateMachine.state).isEqualTo(fromState)
        }

        @Test
        fun transition_givenUndeclaredState_shouldThrowIllegalStateException() {
            // Then
            Assertions.assertThatIllegalStateException()
                .isThrownBy {
                    runBlocking { stateMachine.transition(EVENT_4) }
                }
        }
    }

    @Nested
    inner class WithoutInitialState {

        @Test
        fun create_givenNoInitialState_shouldThrowIllegalArgumentException() {
            // Then
            Assertions.assertThatIllegalArgumentException().isThrownBy {
                StateMachine.create<String, Int, String> {}
            }
        }
    }

    @Nested
    inner class WithMissingStateDefinition {

        private val stateMachine = StateMachine.create<String, Int, Nothing> {
            initialState(STATE_A)
            state(STATE_A) {
                on(EVENT_1) {
                    transitionTo(STATE_B)
                }
            }
            // Missing STATE_B definition.
        }

        @Test
        fun transition_givenMissingDestinationStateDefinition_shouldThrowIllegalStateExceptionWithStateName(): Unit = runBlocking {
            // Then
            Assertions.assertThatIllegalStateException()
                .isThrownBy { runBlocking { stateMachine.transition(EVENT_1) } }
                .withMessage("Missing definition for state ${STATE_B.javaClass.simpleName}!")
        }
    }

    private companion object {
        private const val STATE_A = "a"
        private const val STATE_B = "b"
        private const val STATE_C = "c"
        private const val STATE_D = "d"

        private const val EVENT_1 = 1
        private const val EVENT_2 = 2
        private const val EVENT_3 = 3
        private const val EVENT_4 = 4

        private const val SIDE_EFFECT_1 = "alpha"
    }
}