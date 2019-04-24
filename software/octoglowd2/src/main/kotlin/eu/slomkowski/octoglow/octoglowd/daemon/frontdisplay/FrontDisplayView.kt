package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.getSegmentNumber
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime

enum class UpdateStatus {
    NO_NEW_DATA,
    FULL_SUCCESS,
    PARTIAL_SUCCESS,
    FAILURE
}

/**
 * There are two kinds of data:
 * status - values provided by the view, updatable usually once a minute or so.
 * instant - exact state of the processing on the device, updatable once every several seconds.
 */
abstract class FrontDisplayView(
        val hardware: Hardware,
        val name: String,
        val poolStatusEvery: Duration,
        val poolInstantEvery: Duration,
        val preferredDisplayTime: Duration) {

    init {
        check(name.isNotBlank())
        check(poolStatusEvery > Duration.ZERO)
        check(poolInstantEvery > Duration.ZERO)
        check(poolStatusEvery > poolInstantEvery)
    }

    open fun getMenus(): List<Menu> = emptyList()

    abstract suspend fun poolStatusData(): UpdateStatus

    abstract suspend fun poolInstantData(): UpdateStatus

    abstract suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean)

    override fun toString(): String = "'$name'"

    protected suspend fun drawProgressBar(reportTimestamp: LocalDateTime?, period: Duration = poolStatusEvery) = coroutineScope {
        val fd = hardware.frontDisplay
        if (reportTimestamp != null) {
            val currentCycleDuration = Duration.between(reportTimestamp, LocalDateTime.now())
            check(!currentCycleDuration.isNegative)
            launch { fd.setUpperBar(listOf(getSegmentNumber(currentCycleDuration, period))) }
        } else {
            launch { fd.setUpperBar(emptyList()) }
        }
    }
}

data class MenuOption(val text: String) {
    init {
        require(text.isNotBlank())
        require(text.length < 14)
    }

    override fun toString(): String = text
}

abstract class Menu(val text: String) {
    init {
        require(text.isNotBlank())
        require(text.length <= 16)
    }

    abstract val options: List<MenuOption>

    abstract suspend fun loadCurrentOption(): MenuOption

    abstract suspend fun saveCurrentOption(current: MenuOption)

    override fun toString(): String = text
}
