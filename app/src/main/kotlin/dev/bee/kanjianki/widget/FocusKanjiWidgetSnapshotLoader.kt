package dev.bee.kanjianki.widget

import android.content.Context
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.ReminderEligibilityPolicy
import dev.bee.kanjianki.theme.KaniThemeChoice

internal enum class FocusKanjiWidgetState {
    NOT_SET_UP,
    ERROR,
    EMPTY,
    READY,
}

internal data class FocusKanjiWidgetSelection(
    val kanji: String,
    val primaryMeaning: String,
    val readings: String,
)

internal fun interface FocusKanjiSelectionResolver {
    fun resolve(
        inventory: List<RecordsImportModels.KanjiInventoryItem>,
        allowedKanji: Set<String>,
        nowMillis: Long,
    ): FocusKanjiWidgetSelection?

    companion object {
        val NONE = FocusKanjiSelectionResolver { _, _, _ -> null }
    }
}

internal data class FocusKanjiWidgetSnapshot(
    val state: FocusKanjiWidgetState,
    val kanji: String = "",
    val primaryMeaning: String = "",
    val readings: String = "",
    val isDueNow: Boolean = false,
    val themeChoice: KaniThemeChoice = KaniThemeChoice.GIRLYPOP,
)

/**
 * Reads only committed local inventory and canonical Study eligibility. The deterministic
 * local-day resolver is supplied by the Focus implementation child.
 */
internal object FocusKanjiWidgetSnapshotLoader {
    fun load(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        resolver: FocusKanjiSelectionResolver = FocusKanjiSelectionResolver.NONE,
    ): FocusKanjiWidgetSnapshot = when (val read = WidgetLocalStoreReader.read(context) { store ->
        val inventory = store.allInventoryItems(store.readableDatabase)
        val eligibleItems = ReminderEligibilityPolicy.eligibleReminderItems(
            store.studyItems(),
            store.activeDashboardRows(),
            store.studyLadderSettings(),
        )
        val allowedKanji = eligibleItems.mapTo(linkedSetOf()) { it.kanji }
        val resolved = resolver.resolve(inventory, allowedKanji, nowMillis)
        val selected = resolved?.kanji
            ?.takeIf(allowedKanji::contains)
            ?.let { kanji -> inventory.firstOrNull { it.kanji == kanji } }
        if (selected == null) {
            FocusKanjiWidgetSnapshot(
                state = FocusKanjiWidgetState.EMPTY,
                themeChoice = store.widgetThemeChoice(),
            )
        } else {
            FocusKanjiWidgetSnapshot(
                state = FocusKanjiWidgetState.READY,
                kanji = selected.kanji,
                primaryMeaning = selected.primaryMeaning,
                readings = selected.readings,
                isDueNow = eligibleItems.any {
                    it.kanji == selected.kanji && it.dueAtMillis <= nowMillis
                },
                themeChoice = store.widgetThemeChoice(),
            )
        }
    }) {
        is WidgetStoreRead.Ready -> read.value
        WidgetStoreRead.NotSetUp -> FocusKanjiWidgetSnapshot(FocusKanjiWidgetState.NOT_SET_UP)
        WidgetStoreRead.Corrupt -> FocusKanjiWidgetSnapshot(FocusKanjiWidgetState.ERROR)
    }
}
