package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels

/**
 * Binds `:data-api`'s [WidgetDataPort] to this module's [LocalStore].
 *
 * The adapter that lets `:widget` become its own module: the widget names the port, `:app`
 * names the store, and neither names the other. Every method here is a straight delegation
 * plus, where needed, a projection into the port's own value types — the widget must not see
 * `LocalStoreBase.AutoSyncSettings` or `StudyStatsStore.StudyStreak`, because both are
 * `:app` types and depending on either would restore the cycle this removes.
 *
 * Not a `LocalStore` subclass or extension: the port is deliberately narrower than the
 * store. Handing the widget the store and calling it a port would leave every write method
 * reachable, and a widget that could commit a review or start a sync is exactly what the
 * read-only contract exists to prevent.
 */
internal class LocalStoreWidgetDataPort(private val store: LocalStore) : WidgetDataPort {
    override fun activeDashboardRows(): List<RecordsImportModels.DashboardRow> =
        store.activeDashboardRows()

    override fun studyItems(): List<RecordsStudyModels.StudyItem> = store.studyItems()

    override fun studyItemsForKanji(kanji: Collection<String>): List<RecordsStudyModels.StudyItem> =
        store.studyItemsForKanji(kanji)

    override fun studyLadderSettings(): RecordsBase.StudyLadderSettings =
        store.studyLadderSettings()

    override fun studyStreak(nowMillis: Long): StudyStreakSnapshot =
        store.studyStreak(nowMillis).let {
            StudyStreakSnapshot(
                currentDays = it.currentDays,
                bestDays = it.bestDays,
                studiedToday = it.studiedToday,
                reviewsToday = it.reviewsToday,
                lastStudyAtMillis = it.lastStudyAtMillis,
            )
        }

    override fun latestSuccessfulSyncFinishedAt(): Long? = store.latestSuccessfulSyncFinishedAt()

    override fun consecutiveFailedSyncCount(): Int = store.consecutiveFailedSyncCount()

    /**
     * The schedule as the port's projection, taken from the *normalized* settings.
     *
     * `normalized()` is what clamps an out-of-range hour and zeroes an unconfigured
     * schedule's derived timestamps. Projecting the raw row instead would let a widget
     * render a next-run time the scheduler would never honour.
     */
    override fun autoSyncSnapshot(): AutoSyncSnapshot =
        store.autoSyncSettings().normalized().let {
            AutoSyncSnapshot(
                configured = it.configured,
                enabled = it.enabled,
                hour = it.hour,
                minute = it.minute,
                lastAttemptAtMillis = it.lastAttemptAt,
                lastSuccessAtMillis = it.lastSuccessAt,
                nextRunAtMillis = it.nextRunAt,
            )
        }

    override fun reviewTotalsByDay(nowMillis: Long, days: Int): List<Int> =
        StudyStatsQueries(store).reviewDaySummaries(nowMillis, days).map { it.total }

    override fun inventoryItemForKanji(kanji: String): RecordsImportModels.KanjiInventoryItem? =
        store.inventoryItemForKanji(kanji)

    /**
     * The stored theme key, unresolved.
     *
     * The raw key rather than a `KaniThemeChoice`, because that enum is `:core`'s and the
     * widget already resolves it through `KaniThemeChoice.fromStorageKey` for its Glance
     * palette. Passing the parsed enum would work but would put the parse on this side of a
     * boundary whose whole job is to carry stored state, not interpret it.
     */
    override fun themeStorageKey(): String? =
        store.getStringSetting(KaniThemeChoice.SETTING_KEY, null)
}
