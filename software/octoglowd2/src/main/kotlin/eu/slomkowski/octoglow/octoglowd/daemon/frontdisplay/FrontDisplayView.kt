package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import java.time.Duration

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
        val name: String,
        val poolStatusEvery: Duration,
        val poolInstantEvery: Duration) {

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
