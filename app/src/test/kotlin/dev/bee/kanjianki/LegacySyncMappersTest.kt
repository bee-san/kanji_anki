package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.domain.model.importing.ImportSource
import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlanner
import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LegacySyncMappersTest {
    @Test
    fun mapsLegacySyncSettingsToDomainImportAndQueueSettings() {
        val settings = customSettings()

        val importSettings = LegacySyncMappers.toImportSettings(settings)
        val queueSettings = LegacySyncMappers.toQueueSeedSettings(settings)

        assertEquals("Custom Japanese", importSettings.noteMapping.noteTypeName)
        assertEquals("Front", importSettings.noteMapping.expressionField)
        assertEquals("", importSettings.noteMapping.readingField)
        assertEquals("", importSettings.noteMapping.meaningField)
        assertEquals(200, importSettings.suspendedRankMin)
        assertEquals(2200, importSettings.suspendedRankMax)
        assertEquals(
            setOf(ImportSource.ACTIVE, ImportSource.TAGGED, ImportSource.WEAK, ImportSource.BROWSER_QUERY),
            importSettings.enabledSources,
        )
        assertEquals(NewCardSortMode.RETRIEVABILITY_RISK, importSettings.newCardSortMode)
        assertEquals(5, queueSettings.activeQueueCap)
        assertEquals(7, queueSettings.newPerDay)
        assertEquals(2, queueSettings.matureSupportThreshold)
        assertEquals(NewCardSortMode.RETRIEVABILITY_RISK, queueSettings.newCardSortMode)
    }

    @Test
    fun mapsQueueSeedContextWithAdaptiveAndLadderSettings() {
        val settings = customSettings()

        val context = LegacySyncMappers.toQueueSeedContext(
            settings = settings,
            ladderSettings = RecordsBase.StudyLadderSettings.defaults(),
            locallySuspendedKanji = setOf("本"),
            startOfDayMillis = 1234L,
            recentStats = RecordsSchedulerModels.ReviewStats(8, 1, 2, 3, 2, 4, 1),
            currentStreakDays = 3,
            studiedToday = setOf("日"),
            workloadPercent = 20,
            workloadMode = AdaptiveStudyPlanner.MODE_MANUAL,
            maxItems = 12,
        )

        assertEquals(1234L, context.startOfDayMillis)
        assertEquals(setOf("本"), context.locallySuspendedKanji)
        assertEquals(30, context.ladderSettings.promotionIntervalDays)
        assertEquals(6, context.ladderSettings.demotionFailStreak)
        assertTrue(context.ladderSettings.enabledRungs.contains(StudyRung.KANJI_MEANING))
        assertEquals(8, context.adaptiveContext!!.recentStats.total)
        assertEquals(setOf("日"), context.adaptiveContext!!.studiedToday)
        assertEquals(20, context.adaptiveContext!!.workloadPolicy.workloadPercent)
        assertEquals(12, context.adaptiveContext!!.workloadPolicy.maxItems)
    }

    @Test
    fun similarKanjiAdapterKeepsCanonicalDomainPairs() {
        val coreIndex = SimilarKanjiIndex.parseTsv(
            StringReader("提\t拉\tfixture\n提\t謎\tfixture\n"),
        )
        val adapter = CoreSimilarKanjiIndexAdapter(coreIndex)

        val pairs = adapter.pairsWithin(listOf("拉", "提")).map { it.key() }

        assertEquals(listOf("拉\u0000提\u0000fixture"), pairs)
    }

    private fun customSettings(): RecordsSyncModels.Settings =
        RecordsSyncModels.Settings(
            "Custom Japanese",
            "Mining",
            "Front",
            "",
            "",
            "",
            "",
            "",
            21,
            2,
            200,
            2200,
            5,
            7,
            9,
            4,
            5,
            true,
            false,
            true,
            listOf("focus", "marked"),
            true,
            8.5,
            4,
            2,
            true,
            "tag:kani",
            RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK,
            30,
            6,
        )
}
