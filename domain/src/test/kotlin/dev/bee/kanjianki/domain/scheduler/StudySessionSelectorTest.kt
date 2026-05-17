package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudySessionSelectorTest {
    @Test
    fun returnsNullWhenNoActiveItemIsDueWithinHorizon() {
        val selector = selector()
        val now = 1_000L

        assertNull(
            selector.nextSession(
                input(
                    items = listOf(item("裂", dueAtMillis = now + 30_000L)),
                    rows = listOf(row("裂")),
                    nowMillis = now,
                    studyAheadMillis = 15_000L,
                ),
            ),
        )
    }

    @Test
    fun studyAheadWindowCanPullFutureItemIntoSession() {
        val selector = selector()
        val now = 1_000L

        val session = selector.nextSession(
            input(
                items = listOf(item("裂", dueAtMillis = now + 10_000L, token = "existing")),
                rows = listOf(row("裂")),
                nowMillis = now,
                studyAheadMillis = 15_000L,
            ),
        )

        assertEquals("裂", session?.item?.kanji)
        assertEquals("existing", session?.token)
    }

    @Test
    fun existingTokenIsReusedAndMissingTokenIsGenerated() {
        val existing = selector().nextSession(
            input(
                items = listOf(item("裂", token = "already-active")),
                rows = listOf(row("裂")),
            ),
        )
        val generated = selector { item -> "generated-${item.kanji}" }.nextSession(
            input(
                items = listOf(item("語", token = null)),
                rows = listOf(row("語")),
            ),
        )

        assertEquals("already-active", existing?.token)
        assertEquals("already-active", existing?.item?.activeToken)
        assertEquals("generated-語", generated?.token)
        assertEquals("generated-語", generated?.item?.activeToken)
    }

    @Test
    fun sessionUsesRungTaskTypeWritingFlagAndPrompt() {
        val session = selector().nextSession(
            input(
                items = listOf(item("書", rung = StudyRung.WRITE_KANJI)),
                rows = listOf(row("書", reasonText = "weak writing")),
            ),
        )

        assertEquals(StudyRung.WRITE_KANJI.wireName, session?.taskType)
        assertTrue(session?.writingRequired == true)
        assertEquals("weak writing", session?.prompt)
    }

    @Test
    fun duePriorityPutsWritingAndRelearningBeforeReviewAndUnseenNew() {
        val session = selector().nextSession(
            input(
                items = listOf(
                    item("新", phase = StudyPhase.NEW_LEARNING, totalReviews = 0, token = "new"),
                    item("復", phase = StudyPhase.RELEARNING, token = "relearning"),
                    item("見", rung = StudyRung.WRITE_KANJI, token = "writing"),
                    item("読", phase = StudyPhase.REVIEW, token = "review"),
                ),
                rows = listOf(
                    row("新"),
                    row("復", weakness = 10),
                    row("見", weakness = 80),
                    row("読"),
                ),
            ),
        )

        assertEquals("見", session?.item?.kanji)
        assertEquals("writing", session?.token)
    }

    @Test
    fun dueTimeBreaksPriorityTies() {
        val session = selector().nextSession(
            input(
                items = listOf(
                    item("遅", dueAtMillis = 2_000L, token = "late"),
                    item("早", dueAtMillis = 1_000L, token = "early"),
                ),
                rows = listOf(row("遅"), row("早")),
                nowMillis = 2_000L,
            ),
        )

        assertEquals("早", session?.item?.kanji)
    }

    @Test
    fun unseenNewCardsUseConfiguredNewCardSortMode() {
        val session = selector().nextSession(
            input(
                items = listOf(
                    item("低", phase = StudyPhase.NEW_LEARNING, totalReviews = 0),
                    item("難", phase = StudyPhase.NEW_LEARNING, totalReviews = 0),
                    item("弱", phase = StudyPhase.NEW_LEARNING, totalReviews = 0),
                ),
                rows = listOf(
                    row("低", rank = 300, weakness = 40),
                    row("難", rank = 100, weakness = 20),
                    row("弱", rank = 200, weakness = 80),
                ),
                newCardSortMode = NewCardSortMode.KANI_WEAKNESS,
            ),
        )

        assertEquals("弱", session?.item?.kanji)
    }

    @Test
    fun weaknessThenKanjiBreakReviewTies() {
        val byWeakness = selector().nextSession(
            input(
                items = listOf(
                    item("低", token = "low"),
                    item("高", token = "high"),
                ),
                rows = listOf(row("低", weakness = 10), row("高", weakness = 50)),
            ),
        )
        val byKanji = selector().nextSession(
            input(
                items = listOf(
                    item("語", token = "go"),
                    item("学", token = "gaku"),
                ),
                rows = listOf(row("語", weakness = 10), row("学", weakness = 10)),
            ),
        )

        assertEquals("高", byWeakness?.item?.kanji)
        assertEquals("学", byKanji?.item?.kanji)
    }

    @Test
    fun allowedKanjiAndActiveQueueFilteringStillApply() {
        val session = selector().nextSession(
            input(
                items = listOf(
                    item("裂", token = "allowed"),
                    item("語", token = "not-allowed"),
                    item("外", token = "missing-row"),
                ),
                rows = listOf(row("裂"), row("語")),
                allowedKanji = setOf("裂"),
            ),
        )

        assertEquals("allowed", session?.token)
        assertFalse(session?.item?.isRetired == true)
    }

    private fun selector(
        tokenFactory: (StudyQueueItem) -> String = { item -> "generated-${item.kanji}" },
    ): StudySessionSelector = StudySessionSelector(tokenFactory = tokenFactory)

    private fun input(
        items: List<StudyQueueItem>,
        rows: List<StudyDashboardRow>,
        nowMillis: Long = 1_000L,
        studyAheadMillis: Long = 0L,
        allowedKanji: Set<String>? = null,
        newCardSortMode: NewCardSortMode = NewCardSortMode.default,
    ): NextSessionInput = NextSessionInput(
        items = items,
        rows = rows,
        nowMillis = nowMillis,
        studyAheadMillis = studyAheadMillis,
        allowedKanji = allowedKanji,
        newCardSortMode = newCardSortMode,
    )

    private fun item(
        kanji: String,
        dueAtMillis: Long = 1_000L,
        rung: StudyRung = StudyRung.KANJI_MEANING,
        phase: StudyPhase = StudyPhase.REVIEW,
        totalReviews: Int = 1,
        token: String? = kanji,
    ): StudyQueueItem = StudyQueueItem(
        kanji = kanji,
        state = StudyItemState.REVIEW,
        dueAtMillis = dueAtMillis,
        stability = 1.0,
        difficulty = 5.0,
        totalReviews = totalReviews,
        lapses = 0,
        learningStep = 0,
        writingLevel = 0,
        rung = rung,
        phase = phase,
        activeToken = token,
    )

    private fun row(
        kanji: String,
        rank: Int = 100,
        weakness: Int = 10,
        reasonText: String = "reason text",
    ): StudyDashboardRow = StudyDashboardRow(
        kanji = kanji,
        jitenRank = rank,
        primaryMeaning = "meaning",
        reading = "reading",
        browserSearch = "search",
        weaknessScore = weakness,
        reasonCode = "reason",
        reasonText = reasonText,
        activeExampleCount = 1,
        suspendedExampleCount = 0,
        matureSupportCount = 0,
    )
}
