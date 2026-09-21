package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget port's shape, and the one distinction its rendering turns on.
 *
 * A contract test rather than a behaviour test: the implementation lives in the composition
 * root, so what is checkable here is that the port carries what a widget needs and stays
 * read-only. That second part is the load-bearing one — a widget must never advance the
 * scheduler, commit a review, or start a sync, and an interface with no write method cannot.
 */
class WidgetDataPortTest {
    @Test
    fun anUnconfiguredScheduleIsNotTheSameAsADisabledOne() {
        val neverSetUp = AutoSyncSnapshot(
            configured = false,
            enabled = false,
            hour = 0,
            minute = 0,
            lastAttemptAtMillis = 0L,
            lastSuccessAtMillis = 0L,
            nextRunAtMillis = 0L,
        )
        val turnedOff = neverSetUp.copy(configured = true, hour = 9, minute = 30)

        // These render differently and must not be conflated: "never set up" is an invitation
        // to configure, while "configured but off" is a state the user chose. A widget that
        // showed both as "off" would tell someone their sync was disabled when it was simply
        // never armed.
        assertFalse(neverSetUp.configured)
        assertTrue(turnedOff.configured)
        assertFalse(turnedOff.enabled)
        assertEquals(9, turnedOff.hour)
        assertEquals(30, turnedOff.minute)
    }

    @Test
    fun theSnapshotIsAValueAndCopiesCompareEqual() {
        val snapshot = AutoSyncSnapshot(
            configured = true,
            enabled = true,
            hour = 7,
            minute = 15,
            lastAttemptAtMillis = 1_700_000_000_000L,
            lastSuccessAtMillis = 1_700_000_000_000L,
            nextRunAtMillis = 1_700_086_400_000L,
        )

        // Value semantics matter because a widget refresh compares the state it just read
        // against what it drew: a class without equals would repaint on every tick.
        assertEquals(snapshot, snapshot.copy())
        assertEquals(snapshot.hashCode(), snapshot.copy().hashCode())
    }

    @Test
    fun thePortExposesReadsOnlyAndEveryOneOfThem() {
        val port: WidgetDataPort = EmptyWidgetDataPort

        // Called through the interface, so a member removed from it stops compiling here.
        // The values are the empty ones a never-synced install would produce, which is the
        // state a freshly placed widget actually renders from.
        assertTrue(port.activeDashboardRows().isEmpty())
        assertTrue(port.studyItems().isEmpty())
        assertTrue(port.studyItemsForKanji(setOf("脱")).isEmpty())
        assertTrue(port.studyLadderSettings().enabledRungs.isNotEmpty())
        assertEquals(0, port.studyStreak(NOW).currentDays)
        assertNull(port.latestSuccessfulSyncFinishedAt())
        assertEquals(0, port.consecutiveFailedSyncCount())
        assertFalse(port.autoSyncSnapshot().configured)
        assertTrue(port.reviewTotalsByDay(NOW, 7).isEmpty())
        assertNull(port.inventoryItemForKanji("脱"))
        assertNull(port.themeStorageKey())
    }

    /**
     * A port over nothing, standing in for a never-synced install.
     *
     * Implemented here rather than mocked so the compiler enforces completeness: adding a
     * method to [WidgetDataPort] without deciding what an empty install returns is exactly
     * the omission that would render a widget with a blank tile instead of a set-up prompt.
     */
    private object EmptyWidgetDataPort : WidgetDataPort {
        override fun activeDashboardRows(): List<RecordsImportModels.DashboardRow> = emptyList()

        override fun studyItems(): List<RecordsStudyModels.StudyItem> = emptyList()

        override fun studyItemsForKanji(
            kanji: Collection<String>,
        ): List<RecordsStudyModels.StudyItem> = emptyList()

        // The reviewed defaults rather than an empty ladder: the class guarantees at least
        // one always-available enabled rung, so "no rungs" is not a state that can exist.
        override fun studyLadderSettings(): RecordsBase.StudyLadderSettings =
            RecordsBase.StudyLadderSettings.defaults()

        override fun studyStreak(nowMillis: Long): StudyStreakSnapshot = StudyStreakSnapshot(
            currentDays = 0,
            bestDays = 0,
            studiedToday = false,
            reviewsToday = 0,
            lastStudyAtMillis = 0L,
        )

        override fun latestSuccessfulSyncFinishedAt(): Long? = null

        override fun consecutiveFailedSyncCount(): Int = 0

        override fun autoSyncSnapshot(): AutoSyncSnapshot = AutoSyncSnapshot(
            configured = false,
            enabled = false,
            hour = 0,
            minute = 0,
            lastAttemptAtMillis = 0L,
            lastSuccessAtMillis = 0L,
            nextRunAtMillis = 0L,
        )

        override fun reviewTotalsByDay(nowMillis: Long, days: Int): List<Int> = emptyList()

        override fun inventoryItemForKanji(
            kanji: String,
        ): RecordsImportModels.KanjiInventoryItem? = null

        override fun themeStorageKey(): String? = null
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
