package dev.bee.kanjianki.widget

import android.content.Context
import dev.bee.kanjianki.core.FocusKanjiSelectionPolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.ReminderEligibilityPolicy
import dev.bee.kanjianki.core.TextUtil
import dev.bee.kanjianki.theme.KaniThemeChoice
import java.time.ZoneId

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
        val DEFAULT = FocusKanjiSelectionResolver { inventory, allowedKanji, nowMillis ->
            FocusKanjiSelectionPolicy.select(
                inventory,
                allowedKanji,
                nowMillis,
                ZoneId.systemDefault(),
            )?.let { selection ->
                FocusKanjiWidgetSelection(
                    selection.kanji,
                    selection.primaryMeaning,
                    selection.readings,
                )
            }
        }
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
 * Reads only committed local inventory and canonical Study eligibility. The default resolver
 * rotates deterministically at the local calendar-day boundary.
 */
internal object FocusKanjiWidgetSnapshotLoader {
    fun load(
        context: Context,
        nowMillis: Long = System.currentTimeMillis(),
        resolver: FocusKanjiSelectionResolver = FocusKanjiSelectionResolver.DEFAULT,
    ): FocusKanjiWidgetSnapshot = when (val read = WidgetLocalStoreReader.read(context) { store ->
        val dashboardRows = store.activeDashboardRows()
        val eligibleItems = ReminderEligibilityPolicy.eligibleReminderItems(
            store.studyItemsForKanji(dashboardRows.map { it.kanji }),
            dashboardRows,
            store.studyLadderSettings(),
        )
        val allowedKanji = eligibleItems.mapTo(linkedSetOf()) { it.kanji }
        // activeDashboardRows is capped; resolve only its canonical eligible glyphs instead of
        // scanning the full inventory on every widget refresh.
        val inventory = allowedKanji.mapNotNull(store::inventoryItemForKanji)
        val selected = resolver.resolve(inventory, allowedKanji, nowMillis)
            ?.takeIf { selection ->
                selection.kanji == TextUtil.normalizeSingleKanji(selection.kanji) &&
                    selection.kanji in allowedKanji
            }
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
