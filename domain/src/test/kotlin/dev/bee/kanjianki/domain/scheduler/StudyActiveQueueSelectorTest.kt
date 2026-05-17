package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyExample
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.model.study.StudyTaskWireNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyActiveQueueSelectorTest {
    private val selector = StudyActiveQueueSelector()

    @Test
    fun filtersRetiredSuppressedDisallowedAndMissingRows() {
        val input = input(
            items = listOf(
                item("裂", token = "active"),
                item("休", state = StudyItemState.RETIRED, token = "retired"),
                item("黙", suppressedByTaskType = StudyTaskWireNames.FONT_MEANING, token = "suppressed"),
                item("外", token = "missing-row"),
                item("語", token = "disallowed"),
            ),
            rows = listOf(row("裂"), row("休"), row("黙"), row("語")),
            allowedKanji = setOf("裂", "休", "黙"),
        )

        assertEquals(listOf("active"), selector.activeQueueItems(input).map { it.activeToken })
    }

    @Test
    fun blankAnswerSignatureCanMatchCurrentRowByKanji() {
        val active = selector.activeQueueItems(
            input(
                items = listOf(item("裂", answerSignature = "", token = "legacy")),
                rows = listOf(row("裂", suspendedExpression = "裂ける")),
            ),
        )

        assertEquals("legacy", active.single().activeToken)
    }

    @Test
    fun matchingAnswerSignatureUsesRowFamily() {
        val signature = "裂|裂ける|さける|split"
        val active = selector.activeQueueItems(
            input(
                items = listOf(
                    item("裂", answerSignature = signature, token = "matching"),
                    item("裂", answerSignature = "裂|old|old|old", token = "stale"),
                ),
                rows = listOf(row("裂", suspendedExpression = "裂ける")),
            ),
        )

        assertEquals(listOf("matching"), active.map { it.activeToken })
    }

    @Test
    fun oneItemPerFamilyChoosesHighestActiveRung() {
        val signature = "裂|裂ける|さける|split"
        val active = selector.activeQueueItems(
            input(
                items = listOf(
                    item("裂", answerSignature = signature, rung = StudyRung.KANJI_MEANING, token = "kanji"),
                    item("裂", answerSignature = signature, rung = StudyRung.FONT_MEANING, token = "font"),
                    item("裂", answerSignature = signature, rung = StudyRung.TYPE_MEANING, token = "type"),
                ),
                rows = listOf(row("裂", suspendedExpression = "裂ける")),
                nowMillis = 2_000L,
            ),
        )

        assertEquals("font", active.single().activeToken)
    }

    @Test
    fun familySelectionPrefersDueWithinHorizonThenEarliestDue() {
        val signature = "裂|裂ける|さける|split"
        val due = item(
            "裂",
            answerSignature = signature,
            dueAtMillis = 2_000L,
            token = "due",
        )
        val future = item(
            "裂",
            answerSignature = signature,
            dueAtMillis = 30_000L,
            token = "future",
        )
        val earlyFuture = item(
            "裂",
            answerSignature = signature,
            dueAtMillis = 10_000L,
            token = "early",
        )

        assertEquals(
            "due",
            selector.activeQueueItems(
                input(
                    items = listOf(future, due),
                    rows = listOf(row("裂", suspendedExpression = "裂ける")),
                    nowMillis = 2_000L,
                ),
            ).single().activeToken,
        )
        assertEquals(
            "early",
            selector.activeQueueItems(
                input(
                    items = listOf(future, earlyFuture),
                    rows = listOf(row("裂", suspendedExpression = "裂ける")),
                    nowMillis = 2_000L,
                    studyAheadMillis = 20_000L,
                ),
            ).single().activeToken,
        )
    }

    @Test
    fun disabledRungsAreAlignedBeforeFamilyChoice() {
        val active = selector.activeQueueItems(
            input(
                items = listOf(
                    item("裂", rung = StudyRung.MEANING_KANJI, token = "meaning"),
                ),
                rows = listOf(row("裂")),
                ladderSettings = StudyLadderSettings(
                    enabledRungs = setOf(StudyRung.TYPE_MEANING, StudyRung.KANJI_MEANING),
                ),
            ),
        )

        assertEquals(StudyRung.TYPE_MEANING, active.single().rung)
    }

    @Test
    fun dueCountUsesStudyAheadHorizonAfterActiveQueueFiltering() {
        val now = 1_000L
        val activeCount = selector.dueCount(
            input(
                items = listOf(
                    item("裂", dueAtMillis = now, token = "due-now"),
                    item("語", dueAtMillis = now + 5_000L, token = "due-soon"),
                    item("外", dueAtMillis = now, token = "missing-row"),
                ),
                rows = listOf(row("裂"), row("語")),
                nowMillis = now,
                studyAheadMillis = 10_000L,
            ),
        )

        assertEquals(2, activeCount)
    }

    @Test
    fun emptyRowsProduceNoActiveQueueItems() {
        assertTrue(
            selector.activeQueueItems(
                input(items = listOf(item("裂")), rows = emptyList()),
            ).isEmpty(),
        )
    }

    private fun input(
        items: List<StudyQueueItem>,
        rows: List<StudyDashboardRow>,
        nowMillis: Long = 1_000L,
        studyAheadMillis: Long = 0L,
        allowedKanji: Set<String>? = null,
        ladderSettings: StudyLadderSettings = StudyLadderSettings.defaults,
    ): ActiveQueueInput = ActiveQueueInput(
        items = items,
        rows = rows,
        nowMillis = nowMillis,
        studyAheadMillis = studyAheadMillis,
        allowedKanji = allowedKanji,
        ladderSettings = ladderSettings,
    )

    private fun item(
        kanji: String,
        state: StudyItemState = StudyItemState.REVIEW,
        answerSignature: String = "",
        dueAtMillis: Long = 1_000L,
        rung: StudyRung = StudyRung.KANJI_MEANING,
        phase: StudyPhase = StudyPhase.REVIEW,
        suppressedByTaskType: String = "",
        token: String = kanji,
    ): StudyQueueItem = StudyQueueItem(
        kanji = kanji,
        state = state,
        dueAtMillis = dueAtMillis,
        stability = 1.0,
        difficulty = 5.0,
        totalReviews = 1,
        lapses = 0,
        learningStep = 0,
        writingLevel = 0,
        answerSignature = answerSignature,
        rung = rung,
        phase = phase,
        suppressedByTaskType = suppressedByTaskType,
        activeToken = token,
    )

    private fun row(
        kanji: String,
        suspendedExpression: String = "",
    ): StudyDashboardRow = StudyDashboardRow(
        kanji = kanji,
        jitenRank = 30,
        primaryMeaning = "split",
        reading = "さける",
        browserSearch = "search",
        weaknessScore = 5,
        reasonCode = "reason",
        reasonText = "reason text",
        activeExampleCount = 1,
        suspendedExampleCount = 0,
        matureSupportCount = 0,
        examples = if (suspendedExpression.isEmpty()) {
            emptyList()
        } else {
            listOf(StudyExample("suspended", suspendedExpression, "さける", "split"))
        },
    )
}
