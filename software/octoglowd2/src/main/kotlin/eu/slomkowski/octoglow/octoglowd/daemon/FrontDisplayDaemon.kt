package eu.slomkowski.octoglow.octoglowd.daemon

import eu.slomkowski.octoglow.octoglowd.daemon.view.FrontDisplayView
import eu.slomkowski.octoglow.octoglowd.daemon.view.Menu
import eu.slomkowski.octoglow.octoglowd.daemon.view.MenuOption
import eu.slomkowski.octoglow.octoglowd.daemon.view.UpdateStatus
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

        // special menu to exit
        private val exitMenu = Menu("EXIT MENU", listOf(MenuOption("dummy")), { throw IllegalStateException("get") }, { throw IllegalStateException("set") })
    }

    data class ViewInfo(
            val view: FrontDisplayView,
            val menus: List<Menu>,
            var lastSelected: LocalDateTime?,
            var lastStatusPool: LocalDateTime?,
            var lastInstantPool: LocalDateTime?) {
        override fun toString(): String = view.toString()

        fun bumpLastStatusAndInstant() = LocalDateTime.now().let {
            lastStatusPool = it
            lastInstantPool = it
        }

        fun bumpLastInstant() {
            lastInstantPool = LocalDateTime.now()
        }
    }

    private val views = frontDisplayViews.map { ViewInfo(it, it.getMenus(), null, null, null) }

    private val allMenus = listOf(brightnessMenu).plus(views.flatMap { it.menus }).plus(exitMenu)

    private val stateExecutorMutex = Mutex()
    private val stateExecutor: IExecutor<FrontDisplayDaemon, State, Event>

    private var lastDialActivity: LocalDateTime? = null

    init {
        require(frontDisplayViews.isNotEmpty())

        runBlocking(coroutineContext) {
            hardware.frontDisplay.clear()
            hardware.frontDisplay.getButtonReport() // resets any remaining button state
        }

        stateExecutor = createStateMachineExecutor(coroutineContext)
    }

    private fun getNeighbourView(info: ViewInfo, number: Int): ViewInfo = when (number) {
        0 -> info
        else -> views[updateViewIndex(views.indexOf(info), number, views.size)]
    }

    private fun getNeighbourMenu(menu: Menu, number: Int): Menu = when (number) {
        0 -> menu
        else -> allMenus[updateViewIndex(allMenus.indexOf(menu), number, allMenus.size)]
    }

    private fun getNeighbourMenuOption(menu: Menu, current: MenuOption, number: Int): MenuOption = when (number) {
        0 -> current
        else -> menu.options[updateViewIndex(menu.options.indexOf(current), number, menu.options.size)]
    }

    suspend fun drawExitMenu() = coroutineScope {
        val fd = hardware.frontDisplay
        fd.clear()

        val line1 = "< " + exitMenu.text.padEnd(16) + " >"
        launch { fd.setStaticText(0, line1) }
    }

    suspend fun drawMenuOverview(menu: Menu, current: MenuOption) = coroutineScope {
        val fd = hardware.frontDisplay

        fd.clear()

        val line1 = "< " + menu.text.padEnd(16) + " >"
        launch { fd.setStaticText(0, line1) }

        val line2 = "  Current: " + current.text
        launch { fd.setStaticText(20, line2) }
    }

    private suspend fun drawMenuSettingOption(menu: Menu, selected: MenuOption, redrawAll: Boolean) = coroutineScope {
        val fd = hardware.frontDisplay

        if (redrawAll) {
            fd.clear()

            val line1 = "  " + menu.text.padEnd(16)
            launch { fd.setStaticText(0, line1) }
        }

        val line2 = "< " + selected.text.padEnd(16) + " >"
        launch { fd.setStaticText(20, line2) }
    }

    abstract class State {

        abstract class Menu : State() {
            abstract val menu: eu.slomkowski.octoglow.octoglowd.daemon.view.Menu
            abstract val current: MenuOption
            abstract val calledFrom: ViewInfo

            data class Overview(override val menu: eu.slomkowski.octoglow.octoglowd.daemon.view.Menu,
                                override val current: MenuOption,
                                override val calledFrom: ViewInfo) : Menu()

            data class SettingOption(override val menu: eu.slomkowski.octoglow.octoglowd.daemon.view.Menu,
                                     override val current: MenuOption,
                                     override val calledFrom: ViewInfo) : Menu()
        }

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

        data class StatusUpdate(val info: ViewInfo,
                                val status: UpdateStatus) : Event()

        data class InstantUpdate(val info: ViewInfo,
                                 val status: UpdateStatus) : Event()
    }

    private fun createStateMachineExecutor(coroutineContext: CoroutineContext): IExecutor<FrontDisplayDaemon, State, Event> {

        val fd = hardware.frontDisplay

        fun launchInBackground(lambda: suspend () -> Unit) = CoroutineScope(coroutineContext).launch { lambda() }

        val configurator = StateMachine.createConfigurator<FrontDisplayDaemon, State, Event>().apply {

            fun <T : State.ViewCycle> createCommonActions(klass: KClass<T>) {
                event(klass, Event.ButtonPressed::class).goto {
                    val menu = state.info.menus.firstOrNull() ?: allMenus.first()
                    logger.info { "Going to menu overview: $menu." }
                    State.Menu.Overview(menu, runBlocking(coroutineContext) {
                        val current = menu.getCurrentOptionFun()
                        drawMenuOverview(menu, current)
                        current
                    }, state.info)
                }

                event(klass, Event.StatusUpdate::class)
                        .loop {
                            event.info.bumpLastStatusAndInstant()
                            if (event.info == state.info) {
                                logger.debug { "Updating state of active ${state.info}." }
                                launchInBackground {
                                    state.info.view.redrawDisplay(false, true)
                                }
                            }
                        } //todo możliwe, że wtedy przełączyć widok na nowy w auto

                event(klass, Event.InstantUpdate::class)
                        .loop {
                            if (event.info == state.info) {
                                state.info.apply {
                                    logger.debug { "Updating instant of $this." }
                                    launchInBackground {
                                        view.redrawDisplay(false, false)
                                    }
                                }
                            } else {
                                logger.warn { "Spurious instant update from inactive ${event.info}." }
                            }
                        }

                event(klass, Event.EncoderDelta::class)
                        .filter { event.delta != 0 }
                        .goto {
                            val newView = getNeighbourView(state.info, event.delta)
                            logger.info { "Switching view from ${state.info} to $newView." }
                            launchInBackground {
                                fd.clear()
                                newView.view.redrawDisplay(true, true)
                            }
                            State.ViewCycle.Manual(newView)
                        }
                //todo add check to instant update here
            }

            createCommonActions(State.ViewCycle.Manual::class)

            event(State.ViewCycle.Manual::class, Event.Timeout::class)
                    .goto { State.ViewCycle.Auto(state.info) }             //todo add cyclic timeout reaction

            createCommonActions(State.ViewCycle.Auto::class)

            event(State.ViewCycle.Auto::class, Event.StatusUpdate::class)
                    .filter { event.info != state.info }
                    .goto {
                        val newView = event.info
                        logger.info { "Got status update from $newView. Switching to it." }
                        launchInBackground {
                            fd.clear()
                            newView.view.redrawDisplay(true, true)
                        }
                        State.ViewCycle.Auto(event.info)
                    }

            event(State.Menu::class, Event.StatusUpdate::class).loop()
            event(State.Menu::class, Event.InstantUpdate::class).loop()

            event(State.Menu.Overview::class, Event.EncoderDelta::class).goto {
                val newMenu = getNeighbourMenu(state.menu, event.delta)

                when (newMenu) {
                    exitMenu -> {
                        logger.info { "Switching to exit menu." }
                        launchInBackground { drawExitMenu() }
                        State.Menu.Overview(exitMenu, exitMenu.options.first(), state.calledFrom)
                    }
                    else -> {
                        logger.info { "Switching to menu overview: $newMenu." }
                        State.Menu.Overview(newMenu, runBlocking(coroutineContext) {
                            val current = newMenu.getCurrentOptionFun()
                            drawMenuOverview(newMenu, current)
                            current
                        }, state.calledFrom)
                    }
                }
            }

            event(State.Menu.Overview::class, Event.ButtonPressed::class).goto {
                when (state.menu) {
                    exitMenu -> {
                        logger.info { "Leaving menu." }
                        launchInBackground {
                            fd.clear()
                            state.calledFrom.view.redrawDisplay(true, true)
                        }
                        State.ViewCycle.Manual(state.calledFrom)
                    }
                    else -> {
                        logger.info { "Going to value setting of ${state.menu}." }
                        State.Menu.SettingOption(state.menu, runBlocking(coroutineContext) {
                            //todo check if ok?
                            val current = state.menu.getCurrentOptionFun()
                            drawMenuSettingOption(state.menu, current, true)
                            current
                        }, state.calledFrom)
                    }
                }
            }

            event(State.Menu.SettingOption::class, Event.ButtonPressed::class).goto {
                state.run {
                    logger.info { "Setting value of $menu to $current." }
                    launchInBackground {
                        menu.setCurrentOptionFun(current)
                        drawMenuOverview(menu, current)
                    }
                    State.Menu.Overview(menu, current, calledFrom)
                }
            }

            event(State.Menu.SettingOption::class, Event.EncoderDelta::class).goto {
                val newOption = getNeighbourMenuOption(state.menu, state.current, event.delta)
                logger.debug { "Changing visible menu option ${state.current} to $newOption." }

                launchInBackground {
                    drawMenuSettingOption(state.menu, newOption, false)
                }

                State.Menu.SettingOption(state.menu, newOption, state.calledFrom)
            }
        }

        return configurator.createExecutor(this@FrontDisplayDaemon, State.ViewCycle.Auto(views.first()))
    }

    private suspend fun poolStatusAndInstant(now: LocalDateTime) = coroutineScope {
        for (info in views) {
            if (info.lastStatusPool?.plus(info.view.poolStatusEvery)?.isBefore(now) != false) {
                info.bumpLastStatusAndInstant()

                launch {
                    val status = info.view.poolStatusData()
                    if (status != UpdateStatus.NO_NEW_DATA) {
                        stateExecutorMutex.withLock {
                            stateExecutor.fire(Event.StatusUpdate(info, status))
                        }
                    }
                }
            } else if (info.lastInstantPool?.plus(info.view.poolInstantEvery)?.isBefore(now) != false) {
                info.bumpLastInstant()

                if (stateExecutorMutex.withLock { (stateExecutor.state as? State.ViewCycle)?.info } == info) {
                    launch {
                        val status = info.view.poolInstantData()
                        if (status != UpdateStatus.NO_NEW_DATA) {
                            stateExecutorMutex.withLock {
                                stateExecutor.fire(Event.InstantUpdate(info, status))
                            }
                        }
                    }
                }
            }
        }
    }

    override suspend fun pool() = coroutineScope {
        val buttonState = hardware.frontDisplay.getButtonReport()
        val now = LocalDateTime.now()

        when {
            buttonState.button == ButtonState.JUST_RELEASED -> stateExecutorMutex.withLock {
                lastDialActivity = now //todo lift out of mutex
                stateExecutor.fire(Event.ButtonPressed)
            }
            buttonState.encoderDelta != 0 -> stateExecutorMutex.withLock {
                lastDialActivity = now //todo lift out of mutex
                stateExecutor.fire(Event.EncoderDelta(buttonState.encoderDelta))
            }
            else -> poolStatusAndInstant(now)
        }
    }

    private var currentOptionBrightness: MenuOption? = null //todo zrobić zapis w bazie

    private val brightnessMenu: Menu
        get() {
            val optAuto = MenuOption("AUTO")
            val optHard = (1..5).map { MenuOption(it.toString()) }

            return Menu("Brightness", optHard.plus(optAuto), {
                currentOptionBrightness ?: optAuto //todo
            }, { currentOptionBrightness = it }) //todo
        }
}