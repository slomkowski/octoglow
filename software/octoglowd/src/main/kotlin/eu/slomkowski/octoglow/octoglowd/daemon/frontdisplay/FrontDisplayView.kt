package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay

import eu.slomkowski.octoglow.octoglowd.ChangeableSetting
import eu.slomkowski.octoglow.octoglowd.DatabaseLayer
import eu.slomkowski.octoglow.octoglowd.getSegmentNumber
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
import mu.KLogging
import java.time.ZonedDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration

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
@ExperimentalTime
abstract class FrontDisplayView(
    val hardware: Hardware,
    val name: String,
    val poolStatusEvery: kotlin.time.Duration,
    val poolInstantEvery: kotlin.time.Duration,
    val preferredDisplayTime: kotlin.time.Duration
) {

    init {
        check(name.isNotBlank())
        check(poolStatusEvery.isPositive())
        check(poolInstantEvery.isPositive())
        check(poolStatusEvery > poolInstantEvery)
    }

    open fun getMenus(): List<Menu> = emptyList()

    abstract suspend fun poolStatusData(now: Instant): UpdateStatus

    abstract suspend fun poolInstantData(now: Instant): UpdateStatus

    abstract suspend fun redrawDisplay(redrawStatic: Boolean, redrawStatus: Boolean, now: Instant)

    override fun toString(): String = "'$name'"

    @Deprecated("migrating to kotlinx date time")
    protected suspend fun drawProgressBar(
        reportTimestamp: ZonedDateTime?,
        now: ZonedDateTime,
        period: java.time.Duration = poolStatusEvery.toJavaDuration()
    ) = drawProgressBar(
        reportTimestamp?.toInstant()?.toKotlinInstant(),
        now.toInstant().toKotlinInstant(),
        period.toKotlinDuration()
    )

    @ExperimentalTime
    protected suspend fun drawProgressBar(
        reportTimestamp: Instant?,
        now: Instant,
        period: kotlin.time.Duration = poolStatusEvery
    ) = coroutineScope {
        val fd = hardware.frontDisplay
        if (reportTimestamp != null) {
            val currentCycleDuration = now - reportTimestamp
            check(!currentCycleDuration.isNegative())
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

class BooleanChangeableSettingMenu(
    private val database: DatabaseLayer,
    private val key: ChangeableSetting,
    text: String
) : Menu(text) {
    companion object : KLogging() {
        private val optOn = MenuOption("ON")
        private val optOff = MenuOption("OFF")
    }

    override val options: List<MenuOption>
        get() = listOf(optOn, optOff)

    override suspend fun loadCurrentOption(): MenuOption = when (database.getChangeableSettingAsync(key).await()) {
        false.toString() -> optOff
        else -> optOn
    }

    override suspend fun saveCurrentOption(current: MenuOption) {
        database.setChangeableSettingAsync(key, (current == optOn).toString())
        logger.info { "$key set to $current." }
    }
}
