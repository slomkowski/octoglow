package eu.slomkowski.octoglow.octoglowd.daemon


import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.StateMachine
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.FrontDisplayView
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.Menu
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.MenuOption
import eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay.UpdateStatus
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonState
import eu.slomkowski.octoglow.octoglowd.hardware.FrontDisplay
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import eu.slomkowski.octoglow.octoglowd.now
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

class FrontDisplayDaemon(
    private val config: Config,
    private val coroutineContext: CoroutineContext,
    private val hardware: Hardware,
    frontDisplayViews: List<FrontDisplayView>,
    additionalMenus: List<Menu>
) : Daemon(config, hardware, logger, 20.milliseconds) {

    companion object {
        private val logger = KotlinLogging.logger {}

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
                    ?: Instant.DISTANT_PAST)).inWholeMilliseconds
                val v2 = (now() - (it.lastViewed ?: Instant.DISTANT_PAST)).inWholeMilliseconds

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
    private val stateExecutor: StateMachine<State, Event, SideEffect>

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
            class Auto(info: ViewInfo) : ViewCycle(info)
            class Manual(info: ViewInfo) : ViewCycle(info)
        }
    }

    sealed class Event {
        data object ButtonPressed : Event()

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

    sealed class SideEffect {
        data class ViewInfoRedrawAll(val info: ViewInfo) : SideEffect()
        data class ViewInfoRedrawStatus(val info: ViewInfo) : SideEffect()
        data class ViewInfoRedrawInstant(val info: ViewInfo) : SideEffect()
    }

    private inline fun <reified S : State.ViewCycle> StateMachine.GraphBuilder<State, Event, SideEffect>.StateDefinitionBuilder<S>.createCommonViewCycleActions(
        coroutineContext: CoroutineContext
    ) {
        on<Event.ButtonPressed> {
            val menu = info.menus.firstOrNull() ?: allMenus.first()

            transitionTo(State.Menu.Overview(menu, runBlocking(coroutineContext) {
                logger.info { "Going to menu overview: $menu." }
                val current = menu.loadCurrentOption()
                drawMenuOverview(menu, current)
                current
            }, info))
        }

        on<Event.StatusUpdate> { event ->
            info.bumpLastStatusAndInstant()
            if (event.info == info) {
                dontTransition(SideEffect.ViewInfoRedrawStatus(event.info))
            } else {
                dontTransition()
            }
        }

        on<Event.InstantUpdate> { event ->
            if (event.info == info) {
                dontTransition(SideEffect.ViewInfoRedrawInstant(event.info))
            } else {
                logger.warn { "Spurious instant update from inactive ${event.info}." }
                dontTransition()
            }
        }

        on<Event.EncoderDelta> { event ->
            check(event.delta != 0)
            val newView = getNeighbourView(info, event.delta)
            logger.info { "Switching view from $info to $newView by dial." }
            transitionTo(State.ViewCycle.Manual(newView), SideEffect.ViewInfoRedrawAll(newView))
        }
    }

    private inline fun <reified S : State.Menu> StateMachine.GraphBuilder<State, Event, SideEffect>.StateDefinitionBuilder<S>.createCommonMenuActions() {
        on<Event.StatusUpdate> { dontTransition() }
        on<Event.InstantUpdate> { dontTransition() }
        on<Event.Timeout> {
            logger.info { "Leaving menu and going to $calledFrom because of timeout." }
            transitionTo(State.ViewCycle.Auto(calledFrom), SideEffect.ViewInfoRedrawStatus(calledFrom))
        }
    }

    private fun createStateMachineExecutor(coroutineContext: CoroutineContext): StateMachine<State, Event, SideEffect> {

        fun launchInBackground(lambda: suspend () -> Unit) =
            CoroutineScope(coroutineContext).launch(exceptionHandler) { lambda() }

        return StateMachine.create<State, Event, SideEffect> {
            initialState(State.ViewCycle.Auto(views.first()))

            state<State.ViewCycle.Manual> {
                createCommonViewCycleActions(coroutineContext)

                on<Event.Timeout> {
                    logger.info { "Switching to auto mode because of the timeout." }
                    transitionTo(State.ViewCycle.Auto(this.info), SideEffect.ViewInfoRedrawAll(this.info))
                }
            }

            state<State.ViewCycle.Auto> {
                createCommonViewCycleActions(coroutineContext)

                on<Event.Timeout> { event ->
                    if (event.now - (info.lastViewed ?: event.now) >= info.view.preferredDisplayTime) {
                        val newView = getMostSuitableViewInfo(views)
                        logger.info { "Going to view $newView because of timeout." }
                        transitionTo(State.ViewCycle.Auto(newView), SideEffect.ViewInfoRedrawAll(this.info))
                    } else {
                        dontTransition()
                    }
                }
            }

            state<State.Menu.Overview> {
                createCommonMenuActions()

                on<Event.EncoderDelta> { event ->
                    when (val newMenu = getNeighbourMenu(this.menu, event.delta)) {
                        exitMenu -> {
                            logger.info { "Switching to exit menu." }
                            launchInBackground { drawExitMenu() }
                            transitionTo(State.Menu.Overview(exitMenu, exitMenu.options.first(), calledFrom))
                        }

                        else -> {
                            logger.info { "Switching to menu overview: $newMenu." }
                            transitionTo(State.Menu.Overview(newMenu, runBlocking(coroutineContext) {
                                val current = newMenu.loadCurrentOption()
                                drawMenuOverview(newMenu, current)
                                current
                            }, calledFrom))
                        }
                    }
                }

                on<Event.ButtonPressed> {
                    when (this.menu) {
                        exitMenu -> {
                            logger.info { "Leaving menu." }
                            transitionTo(State.ViewCycle.Manual(calledFrom), SideEffect.ViewInfoRedrawAll(calledFrom))
                        }

                        else -> {
                            logger.info { "Going to value setting of ${menu}." }
                            transitionTo(State.Menu.SettingOption(menu, runBlocking(coroutineContext) {
                                val current = menu.loadCurrentOption()
                                drawMenuSettingOption(menu, current, true)
                                current
                            }, calledFrom))
                        }
                    }
                }
            }

            state<State.Menu.SettingOption> {
                createCommonMenuActions()

                on<Event.ButtonPressed> {
                    logger.info { "Setting value of $menu to $current." }
                    launchInBackground {
                        menu.saveCurrentOption(current)
                        drawMenuOverview(menu, current)
                    }
                    transitionTo(State.Menu.Overview(menu, current, calledFrom))
                }

                on<Event.EncoderDelta> { event ->
                    val newOption = getNeighbourMenuOption(menu, current, event.delta)
                    logger.debug { "Changing visible menu option $current to $newOption." }

                    launchInBackground {
                        drawMenuSettingOption(menu, newOption, false)
                    }

                    transitionTo(State.Menu.SettingOption(menu, newOption, calledFrom))
                }
            }

            onTransition {
                val validTransition = it as? StateMachine.Transition.Valid ?: return@onTransition
                when (val se = validTransition.sideEffect) {
                    is SideEffect.ViewInfoRedrawAll -> se.info.redrawAll()
                    is SideEffect.ViewInfoRedrawStatus -> se.info.redrawStatus()
                    is SideEffect.ViewInfoRedrawInstant -> se.info.redrawInstant()
                    null -> {
                        // no side effect
                    }
                }
            }
        }
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
                            stateExecutor.transition(Event.StatusUpdate(info, status))
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
                                stateExecutor.transition(Event.InstantUpdate(info, status))
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
                stateExecutorMutex.withLock { stateExecutor.transition(Event.ButtonPressed) }
            }

            buttonState.encoderDelta != 0 -> {
                lastDialActivity = now
                stateExecutorMutex.withLock { stateExecutor.transition(Event.EncoderDelta(buttonState.encoderDelta)) }
            }

            else -> {
                // timeout
                if (now - (lastDialActivity
                        ?: Instant.DISTANT_PAST) > config.viewAutomaticCycleTimeout
                ) {
                    stateExecutorMutex.withLock {
                        stateExecutor.transition(Event.Timeout(now))
                    }
                }
                poolStatusAndInstant(now)
            }
        }
    }
}