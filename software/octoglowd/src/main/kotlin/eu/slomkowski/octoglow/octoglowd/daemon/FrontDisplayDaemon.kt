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
import eu.slomkowski.octoglow.octoglowd.toKotlinxDatetimeInstant
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class FrontDisplayDaemon(
    private val config: Config,
    private val workerScope: CoroutineScope,
    private val hardware: Hardware,
    frontDisplayViews: List<FrontDisplayView>,
    additionalMenus: List<Menu>,
    private val clock: Clock = Clock.System,
) : Daemon(logger, 20.milliseconds) {

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

        fun getMostSuitableViewInfo(clock: Clock, views: Collection<ViewInfo>): ViewInfo {
            return checkNotNull(views.maxByOrNull {
                val v1 = (it.lastSuccessfulStatusUpdate - it.lastViewed).inWholeMicroseconds
                val v2 = (clock.now() - it.lastViewed).inWholeMicroseconds

                30 * v1 + 50 * v2
            })
        }
    }

    data class ViewInfo(
        private val clock: Clock,
        private val workerScope: CoroutineScope,
        private val frontDisplay: FrontDisplay,
        val view: FrontDisplayView,
    ) {

        @Volatile
        var lastViewed: kotlin.time.Instant = kotlin.time.Instant.DISTANT_PAST
            private set

        @Volatile
        var lastSuccessfulStatusUpdate: kotlin.time.Instant = kotlin.time.Instant.DISTANT_PAST
            private set

        @Volatile
        var lastStatusPool: kotlin.time.Instant = kotlin.time.Instant.DISTANT_PAST
            private set

        @Volatile
        var lastInstantPool: kotlin.time.Instant = kotlin.time.Instant.DISTANT_FUTURE
            private set

        val menus = view.getMenus()

        override fun toString(): String = view.toString()

        fun bumpLastStatusAndInstant() = clock.now().let {
            lastStatusPool = it
            lastInstantPool = it
        }

        fun bumpLastInstant() {
            lastInstantPool = clock.now()
        }

        fun bumpLastSuccessfulStatusUpdate() {
            lastSuccessfulStatusUpdate = clock.now()
        }

        suspend fun redrawAll() {
            val now = clock.now()
            lastViewed = now
            logger.debug { "Redrawing $view." }
            frontDisplay.clear()
            view.redrawDisplay(redrawStatic = true, redrawStatus = true, now = now.toKotlinxDatetimeInstant())
        }

        fun redrawStatus() {
            val now = clock.now()
            lastViewed = now
            workerScope.launch {
                logger.debug { "Updating state of active $view." }
                view.redrawDisplay(redrawStatic = false, redrawStatus = true, now = now.toKotlinxDatetimeInstant())
            }
        }

        fun redrawInstant() {
            workerScope.launch {
                logger.debug { "Updating instant of $view." }
                view.redrawDisplay(redrawStatic = false, redrawStatus = false, now = clock.now().toKotlinxDatetimeInstant())
            }
        }
    }

    private val views =
        frontDisplayViews.map { ViewInfo(clock, workerScope, hardware.frontDisplay, it) }

    private val allMenus = additionalMenus.plus(views.flatMap { it.menus }).plus(exitMenu)

    private val stateExecutor: StateMachine<State, Event, SideEffect>

    init {
        require(frontDisplayViews.isNotEmpty())

        runBlocking {
            hardware.frontDisplay.clear()
            hardware.frontDisplay.getButtonReport() // resets any remaining button state
        }

        stateExecutor = createStateMachineExecutor()
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
        launch { fd.setStaticText(0, line1) }
    }

    private suspend fun drawMenuOverview(menu: Menu, current: MenuOption) = coroutineScope {
        val fd = hardware.frontDisplay

        fd.clear()

        val line1 = "< " + menu.text.padEnd(16) + " >"
        val line2 = "  Current: " + current.text
        launch {
            fd.setStaticText(0, line1)
            fd.setStaticText(20, line2)
        }
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

        data class Timeout(val now: kotlin.time.Instant) : Event()

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

    private inline fun <reified S : State.ViewCycle> StateMachine.GraphBuilder<State, Event, SideEffect>.StateDefinitionBuilder<S>.createCommonViewCycleActions() {
        on<Event.ButtonPressed> {
            val menu = info.menus.firstOrNull() ?: allMenus.first()

            logger.info { "Going to menu overview: $menu." }
            val current = menu.loadCurrentOption()
            drawMenuOverview(menu, current)

            transitionTo(State.Menu.Overview(menu, current, info))
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

    private fun createStateMachineExecutor(): StateMachine<State, Event, SideEffect> {

        return StateMachine.create<State, Event, SideEffect> {
            initialState(State.ViewCycle.Auto(views.first()))

            state<State.ViewCycle.Manual> {
                createCommonViewCycleActions()

                on<Event.Timeout> {
                    logger.info { "Switching to auto mode because of the timeout." }
                    transitionTo(State.ViewCycle.Auto(this.info), SideEffect.ViewInfoRedrawAll(this.info))
                }
            }

            state<State.ViewCycle.Auto> {
                createCommonViewCycleActions()

                on<Event.Timeout> { event ->
                    if (event.now - info.lastViewed >= info.view.preferredDisplayTime) {
                        val newView = getMostSuitableViewInfo(clock, views)
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
                            workerScope.launch { drawExitMenu() }
                            transitionTo(State.Menu.Overview(exitMenu, exitMenu.options.first(), calledFrom))
                        }

                        else -> {
                            logger.info { "Switching to menu overview: $newMenu." }
                            val current = newMenu.loadCurrentOption()
                            drawMenuOverview(newMenu, current)

                            transitionTo(State.Menu.Overview(newMenu, current, calledFrom))
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
                            val current = menu.loadCurrentOption()
                            drawMenuSettingOption(menu, current, true)

                            transitionTo(State.Menu.SettingOption(menu, current, calledFrom))
                        }
                    }
                }
            }

            state<State.Menu.SettingOption> {
                createCommonMenuActions()

                on<Event.ButtonPressed> {
                    logger.info { "Setting value of $menu to $current." }
                    workerScope.launch {
                        menu.saveCurrentOption(current)
                        drawMenuOverview(menu, current)
                    }
                    transitionTo(State.Menu.Overview(menu, current, calledFrom))
                }

                on<Event.EncoderDelta> { event ->
                    val newOption = getNeighbourMenuOption(menu, current, event.delta)
                    logger.debug { "Changing visible menu option $current to $newOption." }

                    workerScope.launch {
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

    private suspend fun poolStatusAndInstant(now: kotlin.time.Instant) = coroutineScope {
        for (info in views) {
            if (info.lastStatusPool + info.view.poolStatusEvery < now) {
                info.bumpLastStatusAndInstant()

                launch {
                    val status = info.view.pollStatusData(clock.now().toKotlinxDatetimeInstant())
                    if (status != UpdateStatus.NO_NEW_DATA) {
                        info.bumpLastSuccessfulStatusUpdate()
                        stateExecutor.transition(Event.StatusUpdate(info, status))
                    }
                }
            } else if ((info.lastInstantPool + info.view.poolInstantEvery) < now) {
                info.bumpLastInstant()

                if ((stateExecutor.state as? State.ViewCycle)?.info == info) {
                    launch {
                        val status = info.view.pollInstantData(clock.now().toKotlinxDatetimeInstant())
                        if (status != UpdateStatus.NO_NEW_DATA) {
                            stateExecutor.transition(Event.InstantUpdate(info, status))
                        }
                    }
                }
            } else {
                yield()
            }
        }
    }

    @Volatile
    private var lastDialActivity: kotlin.time.Instant = kotlin.time.Instant.DISTANT_PAST

    override suspend fun poll() {
        val buttonState = hardware.frontDisplay.getButtonReport()
        val now = clock.now()

        when {
            buttonState.button == ButtonState.JUST_RELEASED -> {
                lastDialActivity = now
                stateExecutor.transition(Event.ButtonPressed)
            }

            buttonState.encoderDelta != 0 -> {
                lastDialActivity = now
                stateExecutor.transition(Event.EncoderDelta(buttonState.encoderDelta))
            }

            else -> {
                // timeout
                if ((now - lastDialActivity) > config.viewAutomaticCycleTimeout) {
                    stateExecutor.transition(Event.Timeout(now))
                }
                poolStatusAndInstant(now)
            }
        }
    }
}