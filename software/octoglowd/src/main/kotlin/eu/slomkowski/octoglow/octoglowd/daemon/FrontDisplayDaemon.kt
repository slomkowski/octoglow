package eu.slomkowski.octoglow.octoglowd.daemon

import com.uchuhimo.konf.Config
import eu.slomkowski.octoglow.octoglowd.ConfKey
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.FrontDisplayView
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.Menu
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.MenuOption
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.UpdateStatus
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonState
import eu.slomkowski.octoglow.octoglowd.hardware.FrontDisplay
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.now
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import mu.KLogging
import org.softpark.stateful4k.StateMachine
import org.softpark.stateful4k.action.IExecutor
import org.softpark.stateful4k.extensions.createExecutor
import org.softpark.stateful4k.extensions.event
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass
import kotlin.time.ExperimentalTime
import kotlin.time.toKotlinDuration

//todo wywalić refleksję?

@ExperimentalTime
class FrontDisplayDaemon(
    private val config: Config,
    private val coroutineContext: CoroutineContext,
    private val hardware: Hardware,
    frontDisplayViews: List<FrontDisplayView>,
    additionalMenus: List<Menu>
) : Daemon(config, hardware, logger, java.time.Duration.ofMillis(100)) {

    companion object : KLogging() {

        fun updateViewIndex(current: Int, delta: Int, size: Int): Int {
            require(current in 0 until size)
            return (100000 * size + current + delta) % size
        }

        // special menu to exit
        private val exitMenu = object : Menu("EXIT MENU") {
            override val options: List<MenuOption>
                get() = listOf(MenuOption("dummy"))

            override suspend fun loadCurrentOption(): MenuOption = throw IllegalStateException()
            override suspend fun saveCurrentOption(current: MenuOption) = throw IllegalStateException()
        }

        fun getMostSuitableViewInfo(views: Collection<ViewInfo>): ViewInfo {
            return checkNotNull(views.maxByOrNull {
                val v1 = ((it.lastSuccessfulStatusUpdate ?: Instant.DISTANT_PAST) - (it.lastViewed
                    ?: Instant.DISTANT_PAST)).inMilliseconds
                val v2 = (now() - (it.lastViewed ?: Instant.DISTANT_PAST)).inMilliseconds

                30 * v1 + 50 * v2
            })

        }
    }

    data class ViewInfo(
        private val coroutineContext: CoroutineContext,
        private val exceptionHandler: CoroutineExceptionHandler,
        private val frontDisplay: FrontDisplay,
        val view: FrontDisplayView
    ) {

        var lastViewed: Instant? = null
            private set

        var lastSuccessfulStatusUpdate: Instant? = null
            private set

        var lastStatusPool: Instant? = null
            private set

        var lastInstantPool: Instant? = null
            private set

        val menus = view.getMenus()

        override fun toString(): String = view.toString()

        fun bumpLastStatusAndInstant() = now().let {
            lastStatusPool = it
            lastInstantPool = it
        }

        fun bumpLastInstant() {
            lastInstantPool = now()
        }

        fun bumpLastSuccessfulStatusUpdate() {
            lastSuccessfulStatusUpdate = now()
        }

        fun redrawAll() {
            lastViewed = now()
            logger.debug { "Redrawing $view." }
            CoroutineScope(coroutineContext).launch(exceptionHandler) {
                frontDisplay.clear()
                view.redrawDisplay(true, true, now())
            }
        }

        fun redrawStatus() {
            lastViewed = now()
            CoroutineScope(coroutineContext).launch(exceptionHandler) {
                logger.debug { "Updating state of active $view." }
                view.redrawDisplay(false, true, now())
            }
        }

        fun redrawInstant() {
            CoroutineScope(coroutineContext).launch(exceptionHandler) {
                logger.debug { "Updating instant of $view." }
                view.redrawDisplay(false, false, now())
            }
        }
    }

    private val views =
        frontDisplayViews.map { ViewInfo(coroutineContext, exceptionHandler, hardware.frontDisplay, it) }

    private val allMenus = additionalMenus.plus(views.flatMap { it.menus }).plus(exitMenu)

    private val stateExecutorMutex = Mutex()
    private val stateExecutor: IExecutor<FrontDisplayDaemon, State, Event>

    private var lastDialActivity: Instant? = null

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

    private suspend fun drawExitMenu() = coroutineScope {
        val fd = hardware.frontDisplay
        fd.clear()

        val line1 = "< " + exitMenu.text.padEnd(16) + " >"
        launch(exceptionHandler) { fd.setStaticText(0, line1) }
    }

    private suspend fun drawMenuOverview(menu: Menu, current: MenuOption) = coroutineScope {
        val fd = hardware.frontDisplay

        fd.clear()

        val line1 = "< " + menu.text.padEnd(16) + " >"
        val line2 = "  Current: " + current.text
        launch(exceptionHandler) {
            fd.setStaticText(0, line1)
            fd.setStaticText(20, line2)
        }
    }

    private suspend fun drawMenuSettingOption(menu: Menu, selected: MenuOption, redrawAll: Boolean) = coroutineScope {
        val fd = hardware.frontDisplay

        if (redrawAll) {
            fd.clear()

            val line1 = "  " + menu.text.padEnd(16)
            launch(exceptionHandler) { fd.setStaticText(0, line1) }
        }

        val line2 = "< " + selected.text.padEnd(16) + " >"
        launch(exceptionHandler) { fd.setStaticText(20, line2) }
    }

    abstract class State {

        abstract class Menu : State() {
            abstract val menu: eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.Menu
            abstract val current: MenuOption
            abstract val calledFrom: ViewInfo

            data class Overview(
                override val menu: eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.Menu,
                override val current: MenuOption,
                override val calledFrom: ViewInfo
            ) : Menu()

            data class SettingOption(
                override val menu: eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.Menu,
                override val current: MenuOption,
                override val calledFrom: ViewInfo
            ) : Menu()
        }

        abstract class ViewCycle(val info: ViewInfo) : State() {

            init {
                info.redrawAll()
            }

            class Auto(info: ViewInfo) : ViewCycle(info)
            class Manual(info: ViewInfo) : ViewCycle(info)
        }
    }

    sealed class Event {
        object ButtonPressed : Event()

        data class Timeout(val now: Instant) : Event()

        data class EncoderDelta(val delta: Int) : Event()

        data class StatusUpdate(
            val info: ViewInfo,
            val status: UpdateStatus
        ) : Event()

        data class InstantUpdate(
            val info: ViewInfo,
            val status: UpdateStatus
        ) : Event()
    }

    private fun createStateMachineExecutor(coroutineContext: CoroutineContext): IExecutor<FrontDisplayDaemon, State, Event> {

        fun launchInBackground(lambda: suspend () -> Unit) =
            CoroutineScope(coroutineContext).launch(exceptionHandler) { lambda() }

        val configurator = StateMachine.createConfigurator<FrontDisplayDaemon, State, Event>().apply {

            fun <T : State.ViewCycle> createCommonActions(klass: KClass<T>) {
                event(klass, Event.ButtonPressed::class).goto {
                    val menu = state.info.menus.firstOrNull() ?: allMenus.first()

                    State.Menu.Overview(menu, runBlocking(coroutineContext) {
                        logger.info { "Going to menu overview: $menu." }
                        val current = menu.loadCurrentOption()
                        drawMenuOverview(menu, current)
                        current
                    }, state.info)
                }

                event(klass, Event.StatusUpdate::class)
                    .loop {
                        event.info.bumpLastStatusAndInstant()
                        if (event.info == state.info) {
                            state.info.redrawStatus()
                        }
                    }

                event(klass, Event.InstantUpdate::class)
                    .loop {
                        if (event.info == state.info) {
                            state.info.redrawInstant()
                        } else {
                            logger.warn { "Spurious instant update from inactive ${event.info}." }
                        }
                    }

                event(klass, Event.EncoderDelta::class)
                    .goto {
                        check(event.delta != 0)
                        val newView = getNeighbourView(state.info, event.delta)
                        logger.info { "Switching view from ${state.info} to $newView by dial." }
                        State.ViewCycle.Manual(newView)
                    }
            }

            createCommonActions(State.ViewCycle.Manual::class)

            event(State.ViewCycle.Manual::class, Event.Timeout::class)
                .goto {
                    logger.info { "Switching to auto mode because of the timeout." }
                    State.ViewCycle.Auto(state.info)
                }

            createCommonActions(State.ViewCycle.Auto::class)

            event(State.ViewCycle.Auto::class, Event.StatusUpdate::class)
                .filter { event.info != state.info }
                .loop()

            event(State.ViewCycle.Auto::class, Event.Timeout::class)
                .filter { event.now - (state.info.lastViewed ?: event.now) >= state.info.view.preferredDisplayTime }
                .goto {
                    val newView = getMostSuitableViewInfo(views)
                    logger.info { "Going to view $newView because of timeout." }
                    State.ViewCycle.Auto(newView)
                }

            event(State.ViewCycle.Auto::class, Event.Timeout::class)
                .filter { event.now - (state.info.lastViewed ?: event.now) < state.info.view.preferredDisplayTime }
                .loop()

            event(State.Menu::class, Event.StatusUpdate::class).loop()
            event(State.Menu::class, Event.InstantUpdate::class).loop()

            event(State.Menu.Overview::class, Event.EncoderDelta::class).goto {
                when (val newMenu = getNeighbourMenu(state.menu, event.delta)) {
                    exitMenu -> {
                        logger.info { "Switching to exit menu." }
                        launchInBackground { drawExitMenu() }
                        State.Menu.Overview(exitMenu, exitMenu.options.first(), state.calledFrom)
                    }
                    else -> {
                        logger.info { "Switching to menu overview: $newMenu." }
                        State.Menu.Overview(newMenu, runBlocking(coroutineContext) {
                            val current = newMenu.loadCurrentOption()
                            drawMenuOverview(newMenu, current)
                            current
                        }, state.calledFrom)
                    }
                }
            }

            event(State.Menu::class, Event.Timeout::class)
                .goto {
                    logger.info { "Leaving menu and going to ${state.calledFrom} because of timeout." }
                    State.ViewCycle.Auto(state.calledFrom)
                }

            event(State.Menu.Overview::class, Event.ButtonPressed::class).goto {
                when (state.menu) {
                    exitMenu -> {
                        logger.info { "Leaving menu." }
                        State.ViewCycle.Manual(state.calledFrom)
                    }
                    else -> {
                        logger.info { "Going to value setting of ${state.menu}." }
                        State.Menu.SettingOption(state.menu, runBlocking(coroutineContext) {
                            val current = state.menu.loadCurrentOption()
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
                        menu.saveCurrentOption(current)
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

    private suspend fun poolStatusAndInstant(now: Instant) {
        for (info in views) {
            if ((info.lastStatusPool ?: Instant.DISTANT_PAST) + info.view.poolStatusEvery < now) {
                info.bumpLastStatusAndInstant()

                CoroutineScope(coroutineContext).launch(exceptionHandler) {
                    val status = info.view.poolStatusData(now())
                    if (status != UpdateStatus.NO_NEW_DATA) {
                        info.bumpLastSuccessfulStatusUpdate()
                        stateExecutorMutex.withLock {
                            stateExecutor.fire(Event.StatusUpdate(info, status))
                        }
                    }
                }
            } else if (((info.lastInstantPool ?: Instant.DISTANT_FUTURE) + info.view.poolInstantEvery) < now) {
                info.bumpLastInstant()

                if (stateExecutorMutex.withLock { (stateExecutor.state as? State.ViewCycle)?.info } == info) {
                    CoroutineScope(coroutineContext).launch(exceptionHandler) {
                        val status = info.view.poolInstantData(now())
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

    override suspend fun pool() {
        val buttonState = hardware.frontDisplay.getButtonReport()
        val now = now()

        when {
            buttonState.button == ButtonState.JUST_RELEASED -> {
                lastDialActivity = now
                stateExecutorMutex.withLock { stateExecutor.fire(Event.ButtonPressed) }
            }
            buttonState.encoderDelta != 0 -> {
                lastDialActivity = now
                stateExecutorMutex.withLock { stateExecutor.fire(Event.EncoderDelta(buttonState.encoderDelta)) }
            }
            else -> {
                // timeout
                if (now - (lastDialActivity
                        ?: Instant.DISTANT_PAST) > config[ConfKey.viewAutomaticCycleTimeout].toKotlinDuration()
                ) {
                    stateExecutorMutex.withLock {
                        stateExecutor.fire(Event.Timeout(now))
                    }
                }
                poolStatusAndInstant(now)
            }
        }
    }
}