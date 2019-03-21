package eu.slomkowski.octoglow.octoglowd.daemon

import com.toxicbakery.kfinstatemachine.StateMachine
import com.toxicbakery.kfinstatemachine.StateMachine.Companion.transition
import eu.slomkowski.octoglow.octoglowd.daemon.view.FrontDisplayView
import eu.slomkowski.octoglow.octoglowd.daemon.view.Menu
import eu.slomkowski.octoglow.octoglowd.daemon.view.MenuOption
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KLogging
import java.time.Duration
import java.time.LocalDateTime

class FrontDisplayDaemon(
        private val hardware: Hardware,
        private val frontDisplayViews: List<FrontDisplayView>)
    : Daemon(Duration.ofMillis(100)) {

    companion object : KLogging() {
        fun updateViewIndex(current: Int, delta: Int, size: Int): Int {
            require(current in 0..(size - 1))
            return (10000 * size + current + delta) % size
        }
    }

    data class ViewInfo(
            var active: Boolean,
            val view: FrontDisplayView,
            var lastSelected: LocalDateTime?,
            var lastPooledStatusUpdate: LocalDateTime?,
            var lastPooledInstantUpdate: LocalDateTime?)

    private val views = frontDisplayViews.map { ViewInfo(false, it, null, null, null) }


    private var lastDialActivity: LocalDateTime? = null

    init {
        require(frontDisplayViews.isNotEmpty())
        runBlocking {
            hardware.frontDisplay.clear()
        }
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

    sealed class State {
        object Normal : State()
        object Menu : State()
    }

    sealed class Event {
        object ButtonPressed : Event()
       data class EncoderDelta(val delta: Int) : Event()
    }


    /**
     * Returns pair of: (views with status update, views with instant update)
     */


    private suspend fun poolMenu(firstTime: Boolean, encoderDelta: Int, buttonPress: Boolean): Boolean  = coroutineScope {




        val stateMachine = StateMachine(State.Normal,
                transition(State.Normal, Event.ButtonPressed::class, State.Menu).reaction { machine, transition ->
                    launch {
                        views[0].view.poolStatusData()
                   }
                }

        )



         true
    }

    override suspend fun pool() = coroutineScope {
       TODO()
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