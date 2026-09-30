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
import kotlinx.coroutines.channels.Channel
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
) : PollingDemon(
    logger,
    // the firmware latches button edges and accumulates encoder deltas, so polling at 40 ms instead of
    // 20 ms halves the I2C traffic without losing input; 450 ms long-press detection is unaffected
    pollingInterval = 40.milliseconds,
    // this demon owns the dial, so a failed button read must not take the UI down for seconds
    initialErrorBackoff = 100.milliseconds,
    maxErrorBackoff = 2.seconds,
) {

    companion object {
        private val logger = KotlinLogging.logger {}

        private val DEFAULT_INSTANT_REDRAW_INTERVAL: Duration = 10.seconds

        /**
         * How often [poll] does the work that is not dial input: view cycling timeouts and polling
         * views for fresh instant data.
         */
        private val SLOW_TICK_INTERVAL: Duration = 250.milliseconds

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

        /**
         * Balances how fresh a view's data is against how long it has been since it was last shown.
         *
         * Both terms are clamped: until a view has been displayed once, [ViewInfo.lastViewed] is
         * [Instant.DISTANT_PAST], and the raw microsecond differences then saturate and overflow the
         * weighted sum, which made the ranking meaningless on a freshly started daemon.
         */
        fun getMostSuitableViewInfo(clock: Clock, views: Collection<ViewInfo>): ViewInfo {
            val now = clock.now()
            return checkNotNull(views.maxByOrNull {
                val dataFreshness = (it.currentStatus.timestamp - it.lastViewed).clampedSeconds()
                val timeSinceShown = (now - it.lastViewed).clampedSeconds()

                30 * dataFreshness + 50 * timeSinceShown
            })
        }

        private const val SUITABILITY_HORIZON_SECONDS = 24L * 60 * 60

        private fun Duration.clampedSeconds(): Long =
            inWholeSeconds.coerceIn(-SUITABILITY_HORIZON_SECONDS, SUITABILITY_HORIZON_SECONDS)

        private val NO_TIMESTAMPED_VALUE = TimestampedObject<Any?>(Instant.DISTANT_PAST, null)
    }

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
        var currentStatus: TimestampedObject<Any?> = NO_TIMESTAMPED_VALUE
            private set

        @Volatile
        var currentInstant: TimestampedObject<Any?> = NO_TIMESTAMPED_VALUE

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

        /**
         * The bookkeeping timestamps are updated here, synchronously, while the display writes are
         * handed to the redraw job. Keeping the timestamps in step with the request is what stops the
         * auto-cycle timeout from firing again before a queued redraw has run.
         */
        fun requestRedrawAll(byTimeout: Boolean) {
            lastViewed = clock.now()
            bumpLastStatusAndInstantRedraw()
            logger.debug { "Redrawing $this." }
            enqueueDraw(PendingDraw.View(this, redrawStatic = true, redrawStatus = true, byTimeout = byTimeout))
        }

        fun requestRedrawStatus() {
            lastViewed = clock.now()
            bumpLastStatusAndInstantRedraw()
            logger.debug { "Updating state of active $view." }
            enqueueDraw(PendingDraw.View(this, redrawStatic = false, redrawStatus = true, byTimeout = false))
        }

        fun requestRedrawInstant() {
            bumpLastInstantRedraw()
            logger.debug { "Updating instant of ${this@ViewInfo}." }
            enqueueDraw(PendingDraw.View(this, redrawStatic = false, redrawStatus = false, byTimeout = false))
        }

        suspend fun performRedraw(redrawStatic: Boolean, redrawStatus: Boolean, byTimeout: Boolean) = coroutineScope {
            if (redrawStatic) {
                hardware.frontDisplay.clear()
                launch { realTimeClockDemon.setFrontDisplayViewNumber(number, byTimeout) }
            }

            view.redrawDisplay(
                redrawStatic = redrawStatic,
                redrawStatus = redrawStatus,
                now = clock.now(),
                currentStatus.obj,
                currentInstant.obj,
            )
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

    private sealed class PendingDraw {
        class View(
            val info: ViewInfo,
            var redrawStatic: Boolean,
            var redrawStatus: Boolean,
            var byTimeout: Boolean,
        ) : PendingDraw()

        class MenuOverview(val menu: Menu, val current: MenuOption) : PendingDraw()

        class MenuSettingOption(val menu: Menu, val selected: MenuOption, var redrawAll: Boolean) : PendingDraw()

        data object ExitMenu : PendingDraw()
    }

    // Every write to the display goes through here. A full redraw is a dozen serialised I2C commands,
    // and it used to be awaited inside a state machine transition, which is itself awaited by poll() -
    // so every view switch and every dial turn froze the dial for the whole repaint. Now a request
    // only updates a single pending slot and wakes one dedicated job. Requests for the same target
    // merge (the strongest flags win), a request for a different target supersedes the old one, and
    // two draws can never interleave their writes on the bus.
    private val pendingDrawLock = Any()

    private var pendingDraw: PendingDraw? = null

    private val drawSignal = Channel<Unit>(Channel.CONFLATED)

    private fun enqueueDraw(draw: PendingDraw) {
        synchronized(pendingDrawLock) {
            val previous = pendingDraw
            pendingDraw = when {
                draw is PendingDraw.View && previous is PendingDraw.View && previous.info == draw.info ->
                    previous.apply {
                        redrawStatic = redrawStatic || draw.redrawStatic
                        redrawStatus = redrawStatus || draw.redrawStatus
                        byTimeout = draw.byTimeout
                    }

                // scrolling through options quickly only has to paint the last one, but a static
                // repaint that has not happened yet must not be lost along the way
                draw is PendingDraw.MenuSettingOption && previous is PendingDraw.MenuSettingOption && previous.menu == draw.menu ->
                    draw.apply { redrawAll = redrawAll || previous.redrawAll }

                else -> draw
            }
        }
        drawSignal.trySend(Unit)
    }

    private fun takePendingDraw(): PendingDraw? = synchronized(pendingDrawLock) {
        pendingDraw.also { pendingDraw = null }
    }

    internal fun createRedrawJob(scope: CoroutineScope): Job = scope.launch {
        for (signal in drawSignal) {
            val pending = takePendingDraw() ?: continue
            try {
                when (pending) {
                    is PendingDraw.View -> pending.info.performRedraw(pending.redrawStatic, pending.redrawStatus, pending.byTimeout)
                    is PendingDraw.MenuOverview -> drawMenuOverview(pending.menu, pending.current)
                    is PendingDraw.MenuSettingOption -> drawMenuSettingOption(pending.menu, pending.selected, pending.redrawAll)
                    PendingDraw.ExitMenu -> drawExitMenu()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Error while drawing $pending." }
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
        data object ButtonPressedLong : Event()

        data object ButtonPressedShort : Event()

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
        on<Event.ButtonPressedShort> {
            logger.info { "Toggling backlight." }
            commandBus.publish(BacklightToggleStateCommand)
            dontTransition()
        }

        on<Event.ButtonPressedLong> {
            val menu = allMenus.first()

            logger.info { "Going to menu overview: $menu." }
            val current = menu.loadCurrentOption()
            enqueueDraw(PendingDraw.MenuOverview(menu, current))

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
                            enqueueDraw(PendingDraw.ExitMenu)
                            transitionTo(State.Menu.Overview(exitMenu, exitMenu.options.first(), calledFrom))
                        }

                        else -> {
                            logger.info { "Switching to menu overview: $newMenu." }
                            val current = newMenu.loadCurrentOption()
                            enqueueDraw(PendingDraw.MenuOverview(newMenu, current))

                            transitionTo(State.Menu.Overview(newMenu, current, calledFrom))
                        }
                    }
                }

                on<Event.ButtonPressedShort> {
                    when (this.menu) {
                        exitMenu -> {
                            logger.info { "Leaving menu." }
                            transitionTo(State.ViewCycle.Manual(calledFrom), SideEffect.ViewInfoRedrawAll(calledFrom, false))
                        }

                        else -> {
                            logger.info { "Going to value setting of ${menu}." }
                            val current = menu.loadCurrentOption()
                            enqueueDraw(PendingDraw.MenuSettingOption(menu, current, redrawAll = true))

                            transitionTo(State.Menu.SettingOption(menu, current, calledFrom))
                        }
                    }
                }
            }

            state<State.Menu.SettingOption> {
                createCommonMenuActions()

                on<Event.ButtonPressedShort> {
                    logger.info { "Setting value of $menu to $current." }
                    // saving can be slow (it reaches the database or the command bus), so it stays off
                    // the state machine's path; only the drawing is serialised with everything else
                    workerScope.launch { menu.saveCurrentOption(current) }
                    enqueueDraw(PendingDraw.MenuOverview(menu, current))
                    transitionTo(State.Menu.Overview(menu, current, calledFrom))
                }

                on<Event.EncoderDelta> { event ->
                    val newOption = getNeighbourMenuOption(menu, current, event.delta)
                    logger.debug { "Changing visible menu option $current to $newOption." }

                    enqueueDraw(PendingDraw.MenuSettingOption(menu, newOption, redrawAll = false))

                    transitionTo(State.Menu.SettingOption(menu, newOption, calledFrom))
                }
            }

            onTransition {
                val validTransition = it as? StateMachine.Transition.Valid ?: return@onTransition
                when (val se = validTransition.sideEffect) {
                    is SideEffect.ViewInfoRedrawAll -> se.info.requestRedrawAll(se.byTimeout)
                    is SideEffect.ViewInfoRedrawStatus -> se.info.requestRedrawStatus()
                    is SideEffect.ViewInfoRedrawInstant -> se.info.requestRedrawInstant()
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
    private val longButtonPress: Duration = 450.milliseconds

    sealed class DialState {
        data object Idle : DialState()
        data class ButtonPressed(val timestamp: Instant) : DialState()
    }

    private val dialStateMachine = StateMachine.create<DialState, ButtonState, Unit> {
        initialState(DialState.Idle)

        state<DialState.Idle> {
            on<ButtonState> {
                when (it) {
                    ButtonState.JUST_PRESSED -> {
                        transitionTo(DialState.ButtonPressed(clock.now()))
                    }

                    ButtonState.JUST_RELEASED, ButtonState.NO_CHANGE -> {
                        dontTransition()
                    }
                }
            }
        }

        state<DialState.ButtonPressed> {
            on<ButtonState> {
                when (it) {
                    ButtonState.JUST_RELEASED -> {
                        val now = clock.now()
                        if ((now - this.timestamp) >= longButtonPress) {
                            commandBus.publish(DialPressedLong)
                        } else {
                            commandBus.publish(DialPressedShort)
                        }
                        transitionTo(DialState.Idle)
                    }

                    ButtonState.JUST_PRESSED, ButtonState.NO_CHANGE -> {
                        dontTransition()
                    }
                }
            }
        }

    }

    @Volatile
    private var lastSlowTick: Instant = Instant.DISTANT_PAST

    override suspend fun poll() {
        val buttonState = hardware.frontDisplay.getButtonReport()
        val now = clock.now()

        // NO_CHANGE is the overwhelmingly common case and every state of the dial machine answers it
        // with dontTransition(), so skipping it outright is behaviour-preserving
        if (buttonState.button != ButtonState.NO_CHANGE) {
            dialStateMachine.transition(buttonState.button) // todo dołożyć state machine dla encoder
        }

        if (buttonState.encoderDelta != 0) {
            lastDialActivity = now
            stateExecutor.transition(Event.EncoderDelta(buttonState.encoderDelta))
            return
        }

        // The dial must be read at the full polling rate, but view cycling and instant-data polling
        // only matter at roughly second granularity. Running them on every tick cost a state machine
        // transition plus a pass over all views every 20 ms, for nothing.
        if ((now - lastSlowTick) < SLOW_TICK_INTERVAL) {
            return
        }
        lastSlowTick = now

        // timeout
        if ((now - lastDialActivity) > config.viewAutomaticCycleTimeout) {
            stateExecutor.transition(Event.Timeout(now))
        }
        pollStatusAndInstant(now)
    }

    override fun createJobs(scope: CoroutineScope): List<Job> {
        return super.createJobs(scope).plus(createRedrawJob(scope)).plus(views.map {
            it.createDataSnapshotCollector(scope, dataSnapshotBus)
        }).plus(scope.launch {
            commandBus.commands.collect { command ->
                when (command) {
                    is DialPressedLong -> {
                        // todo bumping clocks should be in the transitions
                        lastDialActivity = clock.now()
                        stateExecutor.transition(Event.ButtonPressedLong)
                    }

                    is DialPressedShort -> {
                        // todo bumping clocks should be in the transitions
                        lastDialActivity = clock.now()
                        stateExecutor.transition(Event.ButtonPressedShort)
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