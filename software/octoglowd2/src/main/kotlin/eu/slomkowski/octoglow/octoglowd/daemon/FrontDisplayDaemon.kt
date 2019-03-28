package eu.slomkowski.octoglow.octoglowd.daemon

import eu.slomkowski.octoglow.octoglowd.daemon.view.FrontDisplayView
import eu.slomkowski.octoglow.octoglowd.daemon.view.Menu
import eu.slomkowski.octoglow.octoglowd.daemon.view.MenuOption
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.*
import mu.KLogging
import org.softpark.stateful4k.StateMachine
import org.softpark.stateful4k.action.IExecutor
import org.softpark.stateful4k.extensions.createExecutor
import org.softpark.stateful4k.extensions.event
import java.time.Duration
import java.time.LocalDateTime
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

class FrontDisplayDaemon(
        coroutineContext: CoroutineContext,
        private val hardware: Hardware,
        frontDisplayViews: List<FrontDisplayView>)
    : Daemon(Duration.ofMillis(100)) {

    companion object : KLogging() {
        fun updateViewIndex(current: Int, delta: Int, size: Int): Int {
            require(current in 0..(size - 1))
            return (10000 * size + current + delta) % size
        }
    }

    data class ViewInfo(
            val view: FrontDisplayView,
            var lastSelected: LocalDateTime?,
            var lastPooledStatusUpdate: LocalDateTime?,
            var lastPooledInstantUpdate: LocalDateTime?) {
        override fun toString(): String = view.toString()
    }

    private val views = frontDisplayViews.map { ViewInfo(it, null, null, null) }

    private val stateExecutor: IExecutor<FrontDisplayDaemon, State, Event>

    private var lastDialActivity: LocalDateTime? = null

    init {
        require(frontDisplayViews.isNotEmpty())

        runBlocking(coroutineContext) {
            hardware.frontDisplay.clear()
        }

        stateExecutor = createStateMachineExecutor(coroutineContext)
    }

    fun getNeighbourView(info: ViewInfo, number: Int): ViewInfo = when (number) {
        0 -> info
        else -> views[updateViewIndex(views.indexOf(info), number, views.size)]
    }

    suspend fun drawMenu(menu : Menu) : MenuOption = coroutineScope{
        val fd = hardware.frontDisplay
        val currentOptionJob = async {  menu.getCurrentOptionFun() }

        fd.clear()

        val line1 = "< " + menu.text.padEnd(16) + " >"
        launch { fd.setStaticText(0, line1) }

        val currentOption = currentOptionJob.await()

        val line2 = "Current: " + currentOption.text
        launch { fd.setStaticText(20, line2) }

        currentOption
    }

    abstract class State {
        //object Normal : State()
        // object Menu : State()

        abstract class ViewCycle : State() {
            abstract val info: ViewInfo

            data class Auto(override val info: ViewInfo) : ViewCycle()
            data class Manual(override val info: ViewInfo) : ViewCycle()
        }
    }

    sealed class Event {
        object ButtonPressed : Event()
        object Timeout : Event()
       data class EncoderDelta(val delta: Int) : Event()
        data class StatusUpdate(val info: ViewInfo) : Event()
        data class InstantUpdate(val info: ViewInfo) : Event()
    }

    private fun createStateMachineExecutor(coroutineContext: CoroutineContext): IExecutor<FrontDisplayDaemon, State, Event> {

        fun launchInBackground(lambda: suspend () -> Unit) = CoroutineScope(coroutineContext).launch { lambda() }

        val configurator = StateMachine.createConfigurator<FrontDisplayDaemon, State, Event>().apply {

            fun <T : State.ViewCycle> createCommonActions(klass: KClass<T>) {
                event(klass, Event.ButtonPressed::class).loop() //todo enter menu

                event(klass, Event.StatusUpdate::class)
                        .filter { event.info == state.info }
                        .loop { launchInBackground { state.info.view.redrawDisplay(false, true) } }

                event(klass, Event.InstantUpdate::class)
                        .filter { event.info == state.info }
                        .loop { launchInBackground { state.info.view.redrawDisplay(false, false) } }

                event(klass, Event.EncoderDelta::class)
                        .filter { event.delta != 0 }
                        .goto {
                            val newView = getNeighbourView(state.info, event.delta)
                            logger.info { "Switching view from ${state.info} to $newView." }
                            launchInBackground {
                                hardware.frontDisplay.clear()
                                state.info.view.redrawDisplay(true, true)
                            }
                            State.ViewCycle.Manual(newView)
                        }
                //todo add check to instant update here
            }

            createCommonActions(State.ViewCycle.Manual::class)

            event(State.ViewCycle.Manual::class, Event.Timeout::class)
                    .goto { State.ViewCycle.Auto(state.info) }

            createCommonActions(State.ViewCycle.Auto::class)

            //todo add cyclic timeout reaction
            event(State.ViewCycle.Auto::class, Event.StatusUpdate::class)
                    .filter { event.info != state.info }
                    .goto {
                        logger.info { "Got status update from ${event.info}. Switching to it." }
                        launchInBackground {
                            hardware.frontDisplay.clear()
                            state.info.view.redrawDisplay(true, true)
                        }
                        State.ViewCycle.Auto(event.info)
                    }

        }

        return configurator.createExecutor(this@FrontDisplayDaemon, State.ViewCycle.Auto(views.first()))
    }

    override suspend fun pool() = coroutineScope {
        val buttonState = hardware.frontDisplay.getButtonReport()

        // todo examine all status updates

        if (buttonState.button == ButtonState.JUST_RELEASED) {
            stateExecutor.fire(Event.ButtonPressed)
        } else if (buttonState.encoderDelta != 0) {
            stateExecutor.fire(Event.EncoderDelta(buttonState.encoderDelta))
        }
    }

    private var currentOptionBrightness: MenuOption? = null //todo zrobić zapis w bazie

    private val brightnessMenu: Menu
        get() {
            val optAuto = MenuOption("AUTO")
            val optHard = (1..5).map { MenuOption(it.toString()) }

            return Menu("Display brightness", optHard.plus(optAuto), {
                currentOptionBrightness ?: optAuto //todo
            }, { currentOptionBrightness = it }) //todo
        }
}