package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels

/**
 * The read-only slice of persisted state the home-screen widgets render from.
 *
 * Declared here so `:widget` can be its own Android module (Goal 199's last step) without
 * depending on `:app`. Before this, the widget snapshot loaders took the composition root's own database
 * helper directly, which made the module graph circular: `:widget` would need `:app` for
 * that helper while `:app` needs `:widget` for its receivers.
 *
 * Deliberately read-only, and that is a product rule rather than a convenience. A widget
 * renders; it never advances the scheduler, never commits a review, and never triggers a
 * sync — the manual sync path is built around an in-app confirmation, and the repaired-tag
 * write-back must stay manual-confirm-only. A port with no write method cannot break either
 * of those by accident.
 *
 * Every method is synchronous because a Glance `provideGlance` already runs off the main
 * thread and the widget has no scope of its own to suspend in. The implementation is
 * expected to be cheap: these are single-table reads the widget does per refresh.
 */
interface WidgetDataPort {
    /** Active dashboard rows, for the due/queue counts a widget shows. */
    fun activeDashboardRows(): List<RecordsImportModels.DashboardRow>

    /** Every persisted study item, for due-count and ladder projections. */
    fun studyItems(): List<RecordsStudyModels.StudyItem>

    /** The study items for [kanji], for the focus-kanji widget's single glyph. */
    fun studyItemsForKanji(kanji: Collection<String>): List<RecordsStudyModels.StudyItem>

    /** The ladder configuration, because due-ness depends on the configured rungs. */
    fun studyLadderSettings(): RecordsBase.StudyLadderSettings

    /** The streak as of [nowMillis], for the streak widget. */
    fun studyStreak(nowMillis: Long): StudyStreakSnapshot

    /** When the last successful sync finished, or null if none ever has. */
    fun latestSuccessfulSyncFinishedAt(): Long?

    /** How many syncs have failed in a row, for the "sync is unhealthy" state. */
    fun consecutiveFailedSyncCount(): Int

    /** The automatic-sync schedule, which the widget reports but never changes. */
    fun autoSyncSnapshot(): AutoSyncSnapshot

    /**
     * Review totals for the last [days] local days, oldest first, for the activity strip.
     *
     * Totals rather than the full per-day summary: the widgets draw one bar per day and read
     * only `total`, so the port carries what they use. A wider return type would put
     * `:app`'s stats-cache row shape on this boundary for no gain.
     */
    fun reviewTotalsByDay(nowMillis: Long, days: Int): List<Int>

    /**
     * The inventory entry for [kanji], or null when the glyph is not in the inventory.
     *
     * The focus-kanji widget needs the reading and meaning to render a single glyph; the
     * study item alone carries neither.
     */
    fun inventoryItemForKanji(kanji: String): RecordsImportModels.KanjiInventoryItem?

    /** The user's theme, so a widget paints in the palette the app is using. */
    fun themeStorageKey(): String?
}

/**
 * The automatic-sync schedule as a repository-facing projection.
 *
 * Mirrors the composition root's own auto-sync settings class rather than moving it: that
 * class carries a normalization method built on `:core`'s `TimeOfDaySettingsPolicy` and is
 * written back through the settings store — behaviour the widget has no business seeing. A
 * widget only reports the schedule, so it gets the fields and nothing else.
 *
 * [configured] is the one that matters for rendering: an unconfigured schedule is not "off",
 * it is "never set up", and a widget that showed those the same way would tell a user their
 * sync is disabled when it was simply never armed.
 */
data class AutoSyncSnapshot(
    val configured: Boolean,
    val enabled: Boolean,
    val hour: Int,
    val minute: Int,
    val lastAttemptAtMillis: Long,
    val lastSuccessAtMillis: Long,
    val nextRunAtMillis: Long,
)
