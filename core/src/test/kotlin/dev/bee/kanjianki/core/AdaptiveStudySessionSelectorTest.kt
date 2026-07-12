package dev.bee.kanjianki.core

import java.util.ArrayList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveStudySessionSelectorTest {
    private val selector = StudySessionSelector()
    private val settings = RecordsSyncModels.Settings.kikuDefaults()

    @Test
    fun recognitionAndContextVariantsAlternateDeterministically() {
        val recognitionFirst = session(item(CoreSkill.RECOGNITION, totalReviews = 0))
        val recognitionAlternate = session(item(CoreSkill.RECOGNITION, totalReviews = 1))
        val contextualAlternate = session(
            item(CoreSkill.CONTEXTUAL_READING, totalReviews = 1)
                .copyBuilder().hasSentenceReading(true).build(),
        )

        assertEquals(StudyTaskTypes.KANJI_MEANING, recognitionFirst.taskType)
        assertEquals(StudyTaskTypes.FONT_MEANING, recognitionAlternate.taskType)
        assertEquals(StudyTaskTypes.SENTENCE_READING, contextualAlternate.taskType)
    }

    @Test
    fun firstContextCheckIsPlainEvenWhenRecognitionMemoryWasCloned() {
        val cloned = item(CoreSkill.CONTEXTUAL_READING, totalReviews = 5)
            .copyBuilder()
            .hasSentenceReading(true)
            .adaptiveRouteStateJson(
                AdaptiveRouteStateCodec.encode(
                    AdaptiveRouteState(
                        activeCore = CoreSkill.CONTEXTUAL_READING,
                        contextualReadingReviewCount = 0,
                    )
                )
            )
            .build()

        assertEquals(StudyTaskTypes.WORD_READING, session(cloned).taskType)
    }

    @Test
    fun activeRepairRoutesTheSameItemWithoutCreatingAnotherQueue() {
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.CONTEXTUAL_READING,
            activeRepairTasks = listOf(StudyTaskTypes.TYPE_READING),
            repairStepMinutes = listOf(10),
            repairDueAtMillis = NOW,
        )
        val repairItem = item(CoreSkill.CONTEXTUAL_READING, totalReviews = 3)
            .copyBuilder()
            .state(StudyLadderRules.STATE_LEARNING)
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .dueAtMillis(NOW)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .build()

        val selected = session(repairItem)

        assertEquals(StudyTaskTypes.TYPE_READING, selected.taskType)
        assertFalse(selected.writingRequired)
        assertEquals(RecordsBase.LadderRung.WORD_READING, selected.item!!.rung)
    }

    @Test
    fun requiredContextCoreIgnoresLegacyDisabledBit() {
        val legacyDisabled = RecordsBase.StudyLadderSettings.defaults()
            .withRungEnabled(RecordsBase.LadderRung.WORD_READING, false)
            .withRungEnabled(RecordsBase.LadderRung.SENTENCE_READING, false)

        val selected = session(item(CoreSkill.CONTEXTUAL_READING, totalReviews = 0), legacyDisabled)

        assertEquals(StudyTaskTypes.WORD_READING, selected.taskType)
        assertTrue(selected.item!!.routingVersion >= AdaptiveStudyItemPolicy.ROUTING_VERSION)
    }

    private fun session(
        item: RecordsStudyModels.StudyItem,
        ladder: RecordsBase.StudyLadderSettings = RecordsBase.StudyLadderSettings.defaults(),
    ): RecordsSchedulerModels.StudySession = requireNotNull(
        selector.nextSession(
            listOf(item),
            listOf(row()),
            NOW,
            0L,
            null,
            settings,
            ladder,
        ),
    )

    private fun item(core: CoreSkill, totalReviews: Int): RecordsStudyModels.StudyItem {
        val anchor = AdaptiveCorePolicy.memoryOwnerRung(core)
        val owner = AdaptiveCorePolicy.memoryOwnerTaskType(core)
        val memory = RecordsStudyModels.TaskMemory(
            StudyLadderRules.STATE_REVIEW,
            NOW,
            4.0,
            5.0,
            totalReviews,
            0,
            0,
            "good",
            4,
        )
        val route = AdaptiveRouteState(
            activeCore = core,
            recognitionReviewCount = if (core == CoreSkill.RECOGNITION) totalReviews else 0,
            contextualReadingReviewCount = if (core == CoreSkill.CONTEXTUAL_READING) totalReviews else 0,
        )
        return RecordsStudyModels.StudyItem("脱", StudyLadderRules.STATE_REVIEW, NOW, 4.0, 5.0, totalReviews, 0, 0, 1, null, NOW)
            .copyBuilder()
            .answerSignature("脱|脱出|だっしゅつ|escape")
            .rung(anchor)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .build()
            .withTaskMemory(owner, memory)
    }

    private fun row() = RecordsImportModels.DashboardRow(
        "脱",
        900,
        "escape",
        "だっしゅつ",
        "search",
        30,
        "weak",
        "Needs practice",
        1,
        1,
        0,
        ArrayList<RecordsImportModels.Example>().apply {
            add(
                RecordsImportModels.Example(
                    "active", 1L, 2L, "脱出", "だっしゅつ", "escape", "脱出する。",
                    false, 0, 2, 1, 2.0, null, null,
                ),
            )
        },
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
