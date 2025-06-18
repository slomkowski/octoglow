package eu.slomkowski.octoglow.octoglowd.statemachine

import eu.slomkowski.octoglow.octoglowd.StateMachine
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ObjectStateMachine {

    @Nested
    inner class WithInitialState {

        private val onTransitionListener1 = mockk<(StateMachine.Transition<State, Event, SideEffect>) -> Unit>(relaxed = true)
        private val onTransitionListener2 = mockk<(StateMachine.Transition<State, Event, SideEffect>) -> Unit>(relaxed = true)
        private val onStateAExitListener1 = mockk<State.(Event) -> Unit>(relaxed = true)
        private val onStateAExitListener2 = mockk<State.(Event) -> Unit>(relaxed = true)
        private val onStateCEnterListener1 = mockk<State.(Event) -> Unit>(relaxed = true)
        private val onStateCEnterListener2 = mockk<State.(Event) -> Unit>(relaxed = true)
        private val stateMachine = StateMachine.create<State, Event, SideEffect> {
            initialState(State.A)
            state<State.A> {
                onExit(onStateAExitListener1)
                onExit(onStateAExitListener2)
                on<Event.E1> {
                    transitionTo(State.B)
                }
                on<Event.E2> {
                    transitionTo(State.C)
                }
                on<Event.E4> {
                    transitionTo(State.D)
                }
            }
            state<State.B> {
                on<Event.E3> {
                    transitionTo(State.C, SideEffect.SE1)
                }
            }
            state<State.C> {
                on<Event.E4> {
                    dontTransition()
                }
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
            Assertions.assertThat(state).isEqualTo(State.A)
        }

        @Test
        fun transition_givenValidEvent_shouldReturnTransition(): Unit = runBlocking {
            // When
            val transitionFromStateAToStateB = stateMachine.transition(Event.E1)

            // Then
            Assertions.assertThat(transitionFromStateAToStateB).isEqualTo(
                StateMachine.Transition.Valid(State.A, Event.E1, State.B, null)
            )

            // When
            val transitionFromStateBToStateC = stateMachine.transition(Event.E3)

            // Then
            Assertions.assertThat(transitionFromStateBToStateC).isEqualTo(
                StateMachine.Transition.Valid(State.B, Event.E3, State.C, SideEffect.SE1)
            )
        }

        @Test
        fun transition_givenValidEvent_shouldCreateAndSetNewState(): Unit = runBlocking {
            // When
            stateMachine.transition(Event.E1)

            // Then
            Assertions.assertThat(stateMachine.state).isEqualTo(State.B)

            // When
            stateMachine.transition(Event.E3)

            // Then
            Assertions.assertThat(stateMachine.state).isEqualTo(State.C)
        }

        @Test
        fun transition_givenValidEvent_shouldTriggerOnStateChangeListener(): Unit = runBlocking {
            // When
            stateMachine.transition(Event.E1)

            // Then
            verify {
                onTransitionListener1.invoke(
                    StateMachine.Transition.Valid(State.A, Event.E1, State.B, null)
                )
            }

            // When
            stateMachine.transition(Event.E3)

            // Then
            verify {
                onTransitionListener2.invoke(StateMachine.Transition.Valid(State.B, Event.E3, State.C, SideEffect.SE1))
            }

            // When
            stateMachine.transition(Event.E4)

            // Then
            verify {
                onTransitionListener2.invoke(StateMachine.Transition.Valid(State.C, Event.E4, State.C, null))
            }
        }

        @Test
        fun transition_givenValidEvent_shouldTriggerOnEnterListeners() = runBlocking {
            // When
            stateMachine.transition(Event.E2)

            // Then
            verify { onStateCEnterListener1.invoke(State.C, Event.E2) }
            verify { onStateCEnterListener2.invoke(State.C, Event.E2) }
        }

        @Test
        fun transition_givenValidEvent_shouldTriggerOnExitListeners() = runBlocking {
            // When
            stateMachine.transition(Event.E2)

            // Then
            verify { onStateAExitListener1.invoke(State.A, Event.E2) }
            verify { onStateAExitListener2.invoke(State.A, Event.E2) }
        }

        @Test
        fun transition_givenInvalidEvent_shouldReturnInvalidTransition(): Unit = runBlocking {
            // When
            val fromState = stateMachine.state
            val transition = stateMachine.transition(Event.E3)

            // Then
            Assertions.assertThat(transition).isEqualTo(
                StateMachine.Transition.Invalid<State, Event, SideEffect>(State.A, Event.E3)
            )
            Assertions.assertThat(stateMachine.state).isEqualTo(fromState)
        }

        @Test
        fun transition_givenUndeclaredState_shouldThrowIllegalStateException() {
            // Then
            Assertions.assertThatIllegalStateException()
                .isThrownBy {
                    runBlocking { stateMachine.transition(Event.E4) }
                }
        }
    }


    @Nested
    inner class WithoutInitialState {

        @Test
        fun create_givenNoInitialState_shouldThrowIllegalArgumentException() {
            // Then
            Assertions.assertThatIllegalArgumentException().isThrownBy {
                StateMachine.create<State, Event, SideEffect> {}
            }
        }
    }

    private companion object {
        private sealed class State {
            data object A : State()
            data object B : State()
            data object C : State()
            data object D : State()
        }

        private sealed class Event {
            data object E1 : Event()
            data object E2 : Event()
            data object E3 : Event()
            data object E4 : Event()
        }

        private sealed class SideEffect {
            data object SE1 : SideEffect()
            data object SE2 : SideEffect()
            data object SE3 : SideEffect()
        }
    }
}