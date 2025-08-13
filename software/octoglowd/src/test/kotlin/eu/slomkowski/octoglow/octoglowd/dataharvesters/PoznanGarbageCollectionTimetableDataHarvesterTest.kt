package eu.slomkowski.octoglow.octoglowd.dataharvesters

import eu.slomkowski.octoglow.octoglowd.readToString
import eu.slomkowski.octoglow.octoglowd.testConfig
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Month
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.test.assertFails

class PoznanGarbageCollectionTimetableDataHarvesterTest {

    @Test
    fun testDownloadTimetable(): Unit = runBlocking {
        val resp = PoznanGarbageCollectionTimetableDataHarvester.downloadTimetable(
            testConfig.garbageCollectionTimetable.streetName,
            testConfig.garbageCollectionTimetable.buildingNumber,
            null,
        )
        assertThat(resp).isNotBlank()
        val list = PoznanGarbageCollectionTimetableDataHarvester.extractTimetableFromHtml(resp)
        assertThat(list).isNotEmpty()
        assertThat(list.size).isGreaterThan(100)
    }

    @Test
    fun testDownloadTimetable2(): Unit = runBlocking {
        val resp = PoznanGarbageCollectionTimetableDataHarvester.downloadTimetable(
            "not existing street",
            "1",
            null,
        )
        assertFails {
            PoznanGarbageCollectionTimetableDataHarvester.extractTimetableFromHtml(resp)
        }
    }

    @Test
    fun testExtractTimetableFromHtml() {
        val html = checkNotNull(PoznanGarbageCollectionTimetableDataHarvesterTest::class.java.getResourceAsStream("/garbage-collection-kolegiacki-17.html")).use {
            it.readToString()
        }

        val list = PoznanGarbageCollectionTimetableDataHarvester.extractTimetableFromHtml(html)
        assertThat(list).isNotEmpty()
        assertThat(list).hasSize(391)
        assertThat(list).isSortedAccordingTo(compareBy({ it.first }, { it.second }))

        list.filter { it.first.month == Month.AUGUST }.also { forAugust ->
            assertThat(forAugust.filter { it.second == "Papier" }.map { it.first.day }).containsExactlyInAnyOrder(4, 6, 11, 13, 18, 20, 25, 27)
            assertThat(forAugust.filter { it.second == "Szkło" }.map { it.first.day }).containsExactlyInAnyOrder(5, 19)
            assertThat(forAugust.filter { it.second == "Bioodpady" }).hasSize(5)
            assertThat(forAugust).hasSize(32)
        }
    }
}