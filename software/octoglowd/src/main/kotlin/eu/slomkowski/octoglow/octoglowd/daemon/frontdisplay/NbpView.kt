@file:OptIn(ExperimentalTime::class)

package eu.slomkowski.octoglow.octoglowd.daemon.frontdisplay


import eu.slomkowski.octoglow.octoglowd.Config
import eu.slomkowski.octoglow.octoglowd.datacollectors.MeasurementReport
import eu.slomkowski.octoglow.octoglowd.datacollectors.NbpDataCollector
import eu.slomkowski.octoglow.octoglowd.hardware.Hardware
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant


class NbpView(
    private val config: Config,
    hardware: Hardware
) : FrontDisplayView2<NbpView.CurrentReport, Unit>(
    hardware,
    "NBP exchange rates",
    null,
    logger,
) {
    override val preferredDisplayTime = 13.seconds

    data class CurrentReport(
        val timestamp: Instant,
        val cycleLength: Duration,
        val currencies: Map<String, NbpDataCollector.SingleNbpCurrencyMeasurement>
    )

    companion object {
        private val logger = KotlinLogging.logger {}

        fun formatZloty(amount: Double?): String {
            return when (amount) {
                null -> "----zł"
                in 10_000.0..100_000.0 -> String.format("%2.1fzł", amount / 1000.0).replace('.', 'k')
                in 1000.0..10_000.0 -> String.format("%4.0fzł", amount)
                in 100.0..1000.0 -> String.format("%3.0f zł", amount)
                in 10.0..100.0 -> String.format("%4.1fzł", amount)
                in 0.0..10.0 -> String.format("%3.2fzł", amount)
                else -> " MUCH "
            }
        }
    }

    override suspend fun onNewMeasurementReport(
        report: MeasurementReport,
        oldStatus: CurrentReport?,
    ): UpdateStatus {
        val currenciesToUpdate = report.values.filterIsInstance<NbpDataCollector.SingleNbpCurrencyMeasurement>()

        if (currenciesToUpdate.isEmpty()) {
            return UpdateStatus.NoNewData
        }

        return UpdateStatus.NewData(
            CurrentReport(
                report.timestamp,
                checkNotNull(report.cycleLength ?: oldStatus?.cycleLength),
                (oldStatus?.currencies.orEmpty()).plus(currenciesToUpdate.associateBy { it.code }),
            )
        )
    }

    private suspend fun drawCurrencyInfo(cr: NbpDataCollector.SingleNbpCurrencyMeasurement?, offset: Int, diffChartStep: Double) {
        require(diffChartStep > 0)
        hardware.frontDisplay.apply {
            setStaticText(
                offset, when (cr?.isLatestFromToday) {
                    true -> cr.code.uppercase()
                    false -> cr.code.lowercase()
                    null -> "---"
                }
            )
            setStaticText(offset + 20, formatZloty(cr?.value?.getOrNull()))

            if (cr != null) {
                val unit = cr.value.getOrElse { 1.0 } * diffChartStep
                setOneLineDiffChart(5 * (offset + 3), cr.value.getOrNull(), cr.historical, unit)
            }
        }
    }

    override suspend fun redrawDisplay(
        redrawStatic: Boolean,
        redrawStatus: Boolean,
        now: Instant,
        status: CurrentReport?,
        instant: Unit?
    ): Unit = coroutineScope {
        if (redrawStatus) {
            val diffChartStep = config.nbp.diffChartFraction
            logger.debug { "Refreshing NBP screen, diff chart step: $diffChartStep." }
            launch { drawCurrencyInfo(status?.currencies?.get(config.nbp.currency1), 0, diffChartStep) }
            launch { drawCurrencyInfo(status?.currencies?.get(config.nbp.currency2), 7, diffChartStep) }
            launch { drawCurrencyInfo(status?.currencies?.get(config.nbp.currency3), 14, diffChartStep) }
        }

        drawProgressBar(status?.timestamp, now, status?.cycleLength)
    }
}