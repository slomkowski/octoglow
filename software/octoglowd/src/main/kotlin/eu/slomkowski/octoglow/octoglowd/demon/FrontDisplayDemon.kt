@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.demon


import eu.slomkowski.octoglow.octoglowd.*
import eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.FrontDisplayView
import eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.Menu
import eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.MenuOption
import eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.UpdateStatus
import eu.slomkowski.octoglow.octoglowd.hardware.ButtonState
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.*
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class FrontDisplayDemon(
    private val config: Config,
    private val workerScope: CoroutineScope,
    private val hardware: Hardware,
    frontDisplayViews: List<FrontDisplayView<*, *>>,
    additionalMenus: List<Menu>,
    private val dataSnapshotBus: DataSnapshotBus,
    private val commandBus: CommandBus,
    private val realTimeClockDemon: RealTimeClockDemon,
    private val clock: Clock = Clock.System,
) : PollingDemon(logger, 20.milliseconds) {

    companion object {
        private val logger = KotlinLogging.logger {}

        private val DEFAULT_INSTANT_REDRAW_INTERVAL: Duration = 10.seconds

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
                val v1 = (it.currentStatus.timestamp - it.lastViewed).inWholeMicroseconds
                val v2 = (clock.now() - it.lastViewed).inWholeMicroseconds

                30 * v1 + 50 * v2
            })
        }

        private val NO_TIMESTAMPED_VALUE = TimestampedObject(Instant.DISTANT_PAST, null)
    }

    data class TimestampedObject(
        val timestamp: Instant,
        val obj: Any?,
    )

    inner class ViewInfo(
        val number: Int,
        val view: FrontDisplayView<Any, Any>,
    ) {
        @Volatile
        var lastViewed: Instant = Instant.DISTANT_PAST
            private set

        @Volatile
        var lastStatusRedraw: Instant = Instant.DISTANT_PAST
            private set

        @Volatile
        var lastInstantRedraw: Instant = Instant.DISTANT_PAST
            private set

        @Volatile
        var lastInstantPoll: Instant = Instant.DISTANT_PAST
            private set

        @Volatile
        var currentStatus: TimestampedObject = NO_TIMESTAMPED_VALUE
            private set

        @Volatile
        var currentInstant: TimestampedObject = NO_TIMESTAMPED_VALUE

        override fun toString(): String = "$view ($number)"

        fun bumpLastStatusAndInstantRedraw() {
            clock.now().let {
                lastStatusRedraw = it
                lastInstantRedraw = it
            }
        }

        fun bumpLastInstantRedraw() {
            lastInstantRedraw = clock.now()
        }

        fun bumpLastInstantPool() {
            lastInstantPoll = clock.now()
        }

        suspend fun redrawAll(byTimeout: Boolean) {
            val now = clock.now()
            lastViewed = now
            logger.debug { "Redrawing $this." }
            coroutineScope {
                hardware.frontDisplay.clear()
                launch { realTimeClockDemon.setFrontDisplayViewNumber(number, byTimeout) }
                bumpLastStatusAndInstantRedraw()
                view.redrawDisplay(
                    redrawStatic = true,
                    redrawStatus = true,
                    now = now,
                    currentStatus.obj,
                    currentInstant.obj,
                )
            }
        }

        fun redrawStatus() {
            val now = clock.now()
            lastViewed = now
            workerScope.launch {
                logger.debug { "Updating state of active $view." }
                bumpLastStatusAndInstantRedraw()
                view.redrawDisplay(
                    redrawStatic = false,
                    redrawStatus = true,
                    now = now,
                    currentStatus.obj,
                    currentInstant.obj,
                )
            }
        }

        fun redrawInstant() {
            workerScope.launch {
                logger.debug { "Updating instant of ${this@ViewInfo}." }
                bumpLastInstantRedraw()
                view.redrawDisplay(
                    redrawStatic = false,
                    redrawStatus = false,
                    now = clock.now(),
                    currentStatus.obj,
                    currentInstant.obj,
                )
            }
        }

        fun createDataSnapshotCollector(
            scope: CoroutineScope,
            dataSnapshotBus: DataSnapshotBus
        ): Job = scope.launch {
            logger.info { "Created data snapshot collector for ${view}." }
            dataSnapshotBus.snapshots.collect { packet ->
                val newStatus = try {
                    view.onNewDataSnapshot(packet, currentStatus.obj)
                } catch (e: Exception) {
                    logger.error(e) { "Error while processing data snapshot: $packet" }
                    UpdateStatus.NewData(null)
                }
                if (newStatus is UpdateStatus.NewData) {
                    logger.debug { "Status updated of ${this@ViewInfo}." }
                    currentStatus = TimestampedObject(clock.now(), newStatus.newStatus)
                    stateExecutor.transition(Event.StatusUpdate(this@ViewInfo))
                }
            }
        }
    }

    private val views =
        frontDisplayViews.mapIndexed { idx, fdv -> ViewInfo(idx + 1, fdv as FrontDisplayView<Any, Any>) }

    private val allMenus = additionalMenus.plus(exitMenu)

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
            abstract val menu: eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.Menu
            abstract val current: MenuOption
            abstract val calledFrom: ViewInfo

            data class Overview(
                override val menu: eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.Menu,
                override val current: MenuOption,
                override val calledFrom: ViewInfo
            ) : Menu()

            data class SettingOption(
                override val menu: eu.slomkowski.octoglow.octoglowd.demon.frontdisplay.Menu,
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
        ) : Event()

        data class InstantUpdate(
            val info: ViewInfo,
        ) : Event()
    }

    sealed class SideEffect {
        data class ViewInfoRedrawAll(val info: ViewInfo, val byTimeout: Boolean) : SideEffect()
        data class ViewInfoRedrawStatus(val info: ViewInfo) : SideEffect()
        data class ViewInfoRedrawInstant(val info: ViewInfo) : SideEffect()
    }

    private inline fun <reified S : State.ViewCycle> StateMachine.GraphBuilder<State, Event, SideEffect>.StateDefinitionBuilder<S>.createCommonViewCycleActions() {
        on<Event.ButtonPressed> {
            val menu = allMenus.first()

            logger.info { "Going to menu overview: $menu." }
            val current = menu.loadCurrentOption()
            drawMenuOverview(menu, current)

            transitionTo(State.Menu.Overview(menu, current, info))
        }

        on<Event.StatusUpdate> { event ->
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
            transitionTo(State.ViewCycle.Manual(newView), SideEffect.ViewInfoRedrawAll(newView, false))
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
                    transitionTo(State.ViewCycle.Auto(this.info), SideEffect.ViewInfoRedrawAll(this.info, true))
                }
            }

            state<State.ViewCycle.Auto> {
                createCommonViewCycleActions()

                on<Event.Timeout> { event ->
                    if (event.now - info.lastViewed >= info.view.preferredDisplayTime(info.currentStatus.obj)) {
                        val newView = getMostSuitableViewInfo(clock, views)
                        logger.info { "Going to view $newView because of timeout." }
                        transitionTo(State.ViewCycle.Auto(newView), SideEffect.ViewInfoRedrawAll(this.info, true))
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
                            transitionTo(State.ViewCycle.Manual(calledFrom), SideEffect.ViewInfoRedrawAll(calledFrom, false))
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
                    is SideEffect.ViewInfoRedrawAll -> se.info.redrawAll(se.byTimeout)
                    is SideEffect.ViewInfoRedrawStatus -> se.info.redrawStatus()
                    is SideEffect.ViewInfoRedrawInstant -> se.info.redrawInstant()
                    null -> {
                        // no side effect
                    }
                }
            }
        }
    }

    private suspend fun pollStatusAndInstant(now: Instant) = coroutineScope {
        for (info in views) {
            if (info.view.pollInstantEvery == null && (info.lastInstantRedraw + DEFAULT_INSTANT_REDRAW_INTERVAL) < now) {
                // pollInstantData is not called, but redraw is called, probably just for the progress bar
                if ((stateExecutor.state as? State.ViewCycle)?.info == info) {
                    launch { stateExecutor.transition(Event.InstantUpdate(info)) }
                }
            } else if ((info.lastInstantPoll + (info.view.pollInstantEvery ?: Duration.INFINITE)) < now) {
                if ((stateExecutor.state as? State.ViewCycle)?.info == info) {
                    launch {
                        info.bumpLastInstantPool()
                        val newInstant = info.view.pollForNewInstantData(
                            clock.now(),
                            info.currentInstant.obj,
                        )
                        if (newInstant is UpdateStatus.NewData) {
                            info.currentInstant = TimestampedObject(clock.now(), newInstant.newStatus)
                            stateExecutor.transition(Event.InstantUpdate(info))
                        }
                    }
                }
            }
        }
    }

    @Volatile
    private var lastDialActivity: Instant = Instant.DISTANT_PAST

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
                pollStatusAndInstant(now)
            }
        }
    }

    override fun createJobs(scope: CoroutineScope): List<Job> {
        return super.createJobs(scope).plus(views.map {
            it.createDataSnapshotCollector(scope, dataSnapshotBus)
        }).plus(scope.launch {
            commandBus.commands.collect { command ->
                when (command) {
                    is DialPressed -> {
                        // todo bumping clocks should be in the transitions
                        lastDialActivity = clock.now()
                        stateExecutor.transition(Event.ButtonPressed)
                    }

                    is DialTurned -> {
                        if (command.delta != 0) {
                            lastDialActivity = clock.now()
                            stateExecutor.transition(Event.EncoderDelta(command.delta))
                        }
                    }
                }
            }
        })
    }
}