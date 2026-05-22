package dev.bee.kanjianki

import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.StudyStatsStore
import dev.bee.kanjianki.sync.ManualSyncEngine

internal abstract class MainActivityHome : MainActivityBase() {
    @JvmField
    var activeBrowseQuery: String = ""

    private val focusQueue = MainActivityHomeFocusQueue(this)
    private val browseDetail = MainActivityHomeBrowseDetail(this)
    private val syncFlow = MainActivityHomeSync(this)

    abstract fun renderStats()
    abstract fun renderGames()

    override fun renderHome() {
        renderHomeScreen(this)
    }

    fun homeActionRow(): View {
        return homeActionRowView(this)
    }

    fun homeSectionHeader(title: String, actionLabel: String?, action: Runnable?): View {
        return homeSectionHeaderView(this, title, actionLabel, action)
    }

    fun fullWidthHomeButton(): View {
        return fullWidthHomeButtonView(this)
    }

    fun renderFocusQueue() {
        focusQueue.renderFocusQueue()
    }

    fun renderRecentMistakes() {
        focusQueue.renderRecentMistakes()
    }

    fun streakAccent(streak: StudyStatsStore.StudyStreak?): Int {
        return focusQueue.streakAccent(streak)
    }

    fun confirmSync() {
        syncFlow.confirmSync()
    }

    fun runSync() {
        syncFlow.runSync()
    }

    fun renderSyncResult(result: ManualSyncEngine.SyncResult) {
        syncFlow.renderSyncResult(result)
    }

    fun renderSkippedSyncResult(result: ManualSyncEngine.SyncResult) {
        syncFlow.renderSkippedSyncResult(result)
    }

    fun renderSuccessfulSyncResult(result: ManualSyncEngine.SyncResult) {
        syncFlow.renderSuccessfulSyncResult(result)
    }

    fun renderFailedSyncResult(result: ManualSyncEngine.SyncResult) {
        syncFlow.renderFailedSyncResult(result)
    }

    fun studyAheadMillis(): Long {
        return focusQueue.studyAheadMillis()
    }

    fun nonEmptyOr(value: String?, fallback: String): String {
        return if (value.isNullOrEmpty()) fallback else value
    }

    fun studyQueue(
        rows: List<RecordsImportModels.DashboardRow>,
        now: Long,
        persist: Boolean,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    ): List<RecordsStudyModels.StudyItem> {
        return focusQueue.studyQueue(rows, now, persist, plan)
    }

    fun queuedEntries(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan?,
    ): List<QueueEntry> {
        return focusQueue.queuedEntries(rows, items, now, plan)
    }

    fun queuedEntries(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): List<QueueEntry> {
        return focusQueue.queuedEntries(rows, items, now, null)
    }

    fun rowColor(item: RecordsStudyModels.StudyItem, now: Long): Int {
        return focusQueue.rowColor(item, now)
    }

    fun kanjiTile(value: String, sizePx: Int, textSp: Int): TextView {
        return text(value, textSp, INK, true).apply {
            gravity = Gravity.CENTER
            setTypeface(fontResource(R.font.kaisei_tokumin_regular, Typeface.SERIF), Typeface.BOLD)
            background = panel(BLUSH, BLUSH, dp(10))
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
        }
    }

    fun renderDetail(kanji: String) {
        browseDetail.renderDetail(kanji)
    }

    fun renderBrowseKanji(query: String?) {
        browseDetail.renderBrowseKanji(query)
    }

    fun renderDetail(kanji: String, fromBrowse: Boolean) {
        browseDetail.renderDetail(kanji, fromBrowse)
    }

    fun renderDetail(kanji: String, fromBrowse: Boolean, browseQuery: String?) {
        browseDetail.renderDetail(kanji, fromBrowse, browseQuery)
    }
}
