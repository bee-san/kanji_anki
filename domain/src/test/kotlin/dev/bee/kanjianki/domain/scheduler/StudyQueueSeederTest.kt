package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.common.AppClock
import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow
import dev.bee.kanjianki.domain.model.study.StudyExample
import dev.bee.kanjianki.domain.model.study.StudyItemState
import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyQueueItem
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.repository.StudyQueueRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StudyQueueSeederTest {
    private val seeder = StudyQueueSeeder()

    @Test
    fun newAdmissionRespectsDailyAndActiveCaps() {
        val seeded = seeder.seed(
            request(
                rows = listOf(row("日", rank = 2), row("本", rank = 1), row("語", rank = 3)),
                existing = listOf(item("火", createdAtMillis = 0)),
                settings = settings(activeQueueCap = 3, newPerDay = 2),
                nowMillis = 200,
                startOfDayMillis = 100,
            ),
        )

        assertEquals(listOf("日", "本", "火"), seeded.map { it.kanji })
        assertEquals(listOf(StudyItemState.NEW, StudyItemState.NEW), seeded.filter { it.kanji != "火" }.map { it.state })
        assertEquals(listOf(200L, 200L), seeded.filter { it.kanji != "火" }.map { it.createdAtMillis })
    }

    @Test
    fun adaptiveFocusLimitsAdmissionRows() {
        val seeded = seeder.seed(
            request(
                rows = listOf(row("日"), row("本"), row("語")),
                settings = settings(activeQueueCap = 10, newPerDay = 10),
                adaptivePlan = plan(focusKanji = listOf("語", "日"), newAdmissionLimit = 1),
            ),
        )

        assertEquals(listOf("語"), seeded.map { it.kanji })
    }

    @Test
    fun allKanjiModeBypassesCaps() {
        val seeded = seeder.seed(
            request(
                rows = listOf(row("日"), row("本"), row("語")),
                settings = settings(activeQueueCap = 0, newPerDay = 0),
                adaptivePlan = plan(
                    focusKanji = listOf("日"),
                    newAdmissionLimit = 0,
                    allKanjiMode = true,
                ),
            ),
        )

        assertEquals(listOf("日", "本", "語"), seeded.map { it.kanji })
    }

    @Test
    fun retiresMissingRowsAndReviewedRowsWithEnoughMatureSupport() {
        val matureReviewed = item("日", totalReviews = 1, answerSignature = row("日", matureSupportCount = 2).answerSignature())
        val matureUnreviewed = item("本", totalReviews = 0, answerSignature = row("本", matureSupportCount = 2).answerSignature())
        val missing = item("語", totalReviews = 1)

        val seeded = seeder.seed(
            request(
                rows = listOf(row("日", matureSupportCount = 2), row("本", matureSupportCount = 2)),
                existing = listOf(matureReviewed, matureUnreviewed, missing),
                settings = settings(activeQueueCap = 10, newPerDay = 10, matureSupportThreshold = 2),
            ),
        )

        assertEquals(StudyItemState.RETIRED, seeded.single { it.kanji == "日" }.state)
        assertEquals(StudyItemState.REVIEW, seeded.single { it.kanji == "本" }.state)
        assertEquals(StudyItemState.RETIRED, seeded.single { it.kanji == "語" }.state)
    }

    @Test
    fun reopensRetiredItemWhenSupportDropsBelowThreshold() {
        val retired = item(
            kanji = "日",
            state = StudyItemState.RETIRED,
            totalReviews = 5,
            answerSignature = row("日").answerSignature(),
            dueAtMillis = 50,
        )

        val seeded = seeder.seed(
            request(
                rows = listOf(row("日", matureSupportCount = 0)),
                existing = listOf(retired),
                settings = settings(activeQueueCap = 1, newPerDay = 1, matureSupportThreshold = 2),
                nowMillis = 300,
            ),
        )

        val reopened = seeded.single()
        assertEquals(StudyItemState.NEW, reopened.state)
        assertEquals(0, reopened.totalReviews)
        assertEquals(300L, reopened.createdAtMillis)
    }

    @Test
    fun emptyAnswerSignaturePreservesProgressAndAdoptsCurrentSignature() {
        val existing = item(
            kanji = "日",
            answerSignature = "",
            totalReviews = 7,
            dueAtMillis = 50,
            rung = StudyRung.FONT_MEANING,
        )

        val seeded = seeder.seed(
            request(
                rows = listOf(row("日")),
                existing = listOf(existing),
            ),
        )

        val aligned = seeded.single()
        assertEquals(7, aligned.totalReviews)
        assertEquals(50L, aligned.dueAtMillis)
        assertEquals(row("日").answerSignature(), aligned.answerSignature)
        assertEquals(StudyRung.FONT_MEANING, aligned.rung)
    }

    @Test
    fun changedAnswerSignatureResetsNonRetiredProgressAndDemotesRung() {
        val existing = item(
            kanji = "日",
            answerSignature = row("日", expression = "日本").answerSignature(),
            totalReviews = 7,
            lapses = 3,
            dueAtMillis = 50,
            rung = StudyRung.FONT_MEANING,
            suppressedByTaskType = "font_meaning",
            suppressedAtMillis = 40,
            activeToken = "token",
        )

        val seeded = seeder.seed(
            request(
                rows = listOf(row("日", expression = "日記")),
                existing = listOf(existing),
                nowMillis = 300,
            ),
        )

        val reset = seeded.single()
        assertEquals(StudyItemState.LEARNING, reset.state)
        assertEquals(300L, reset.dueAtMillis)
        assertEquals(0, reset.totalReviews)
        assertEquals(0, reset.lapses)
        assertEquals(StudyPhase.NEW_LEARNING, reset.phase)
        assertEquals(StudyRung.KANJI_MEANING, reset.rung)
        assertEquals("", reset.suppressedByTaskType)
        assertEquals(0L, reset.suppressedAtMillis)
        assertNull(reset.activeToken)
    }

    @Test
    fun retiredSignatureChangeDoesNotResetProgress() {
        val existing = item(
            kanji = "日",
            state = StudyItemState.RETIRED,
            answerSignature = row("日", expression = "日本").answerSignature(),
            totalReviews = 7,
            dueAtMillis = 50,
            rung = StudyRung.FONT_MEANING,
        )

        val seeded = seeder.seed(
            request(
                rows = listOf(row("日", expression = "日記", matureSupportCount = 2)),
                existing = listOf(existing),
                nowMillis = 300,
            ),
        )

        val retained = seeded.single()
        assertEquals(StudyItemState.RETIRED, retained.state)
        assertEquals(7, retained.totalReviews)
        assertEquals(50L, retained.dueAtMillis)
        assertEquals(row("日", expression = "日記", matureSupportCount = 2).answerSignature(), retained.answerSignature)
    }

    @Test
    fun seedUseCaseReadsExistingAndReplacesSeededQueue() = runBlocking {
        val repository = FakeStudyQueueRepository(existing = listOf(item("火")))
        val useCase = SeedStudyQueueUseCase(
            studyQueueRepository = repository,
            clock = object : AppClock {
                override fun nowMillis(): Long = 500
            },
        )

        val seeded = useCase(
            SeedStudyQueueUseCaseRequest(
                rows = listOf(row("日")),
                settings = settings(activeQueueCap = 2, newPerDay = 1),
                startOfDayMillis = 0,
            ),
        )

        assertEquals(listOf("日", "火"), seeded.map { it.kanji })
        assertEquals(seeded, repository.replaced.single())
    }

    private class FakeStudyQueueRepository(
        private val existing: List<StudyQueueItem>,
    ) : StudyQueueRepository {
        val replaced = mutableListOf<List<StudyQueueItem>>()

        override suspend fun listActive(): List<StudyQueueItem> = existing.filter { it.state != StudyItemState.RETIRED }

        override suspend fun listByState(state: StudyItemState): List<StudyQueueItem> =
            existing.filter { it.state == state }

        override suspend fun listAllForSeeding(): List<StudyQueueItem> = existing

        override suspend fun replaceAllSeeded(items: List<StudyQueueItem>) {
            replaced += items
        }

        override suspend fun updateReviewedItem(item: StudyQueueItem): Boolean = false

        override suspend fun dueCount(
            state: StudyItemState,
            nowMillis: Long,
        ): Int = 0
    }

    private fun request(
        rows: List<StudyDashboardRow>,
        existing: List<StudyQueueItem> = emptyList(),
        settings: StudyQueueSeedSettings = settings(),
        nowMillis: Long = 200,
        startOfDayMillis: Long = 100,
        adaptivePlan: AdaptiveStudyPlan? = null,
    ): StudyQueueSeedRequest = StudyQueueSeedRequest(
        rows = rows,
        existing = existing,
        settings = settings,
        nowMillis = nowMillis,
        startOfDayMillis = startOfDayMillis,
        adaptivePlan = adaptivePlan,
    )

    private fun settings(
        activeQueueCap: Int = 20,
        newPerDay: Int = 20,
        matureSupportThreshold: Int = 2,
        newCardSortMode: NewCardSortMode = NewCardSortMode.FREQUENCY,
    ): StudyQueueSeedSettings = StudyQueueSeedSettings(
        activeQueueCap = activeQueueCap,
        newPerDay = newPerDay,
        matureSupportThreshold = matureSupportThreshold,
        newCardSortMode = newCardSortMode,
    )

    private fun plan(
        focusKanji: List<String>,
        newAdmissionLimit: Int,
        allKanjiMode: Boolean = false,
    ): AdaptiveStudyPlan = AdaptiveStudyPlan(
        autoMode = !allKanjiMode,
        workloadPercent = if (allKanjiMode) 100 else 25,
        targetCount = focusKanji.size,
        remainingCount = focusKanji.size,
        focusKanji = focusKanji,
        newAdmissionLimit = newAdmissionLimit,
        allKanjiMode = allKanjiMode,
        status = "test",
    )

    private fun row(
        kanji: String,
        rank: Int = 1,
        expression: String = kanji,
        matureSupportCount: Int = 0,
    ): StudyDashboardRow = StudyDashboardRow(
        kanji = kanji,
        jitenRank = rank,
        primaryMeaning = "meaning $kanji",
        reading = "reading $kanji",
        browserSearch = kanji,
        weaknessScore = 10,
        reasonCode = "reason",
        reasonText = "reason text",
        activeExampleCount = 1,
        suspendedExampleCount = 0,
        matureSupportCount = matureSupportCount,
        examples = listOf(
            StudyExample(
                sourceType = "active",
                expression = expression,
                reading = "reading $kanji",
                meaning = "meaning $kanji",
            ),
        ),
    )

    private fun item(
        kanji: String,
        state: StudyItemState = StudyItemState.REVIEW,
        dueAtMillis: Long = 0,
        totalReviews: Int = 1,
        lapses: Int = 0,
        answerSignature: String = "$kanji|old",
        rung: StudyRung = StudyRung.KANJI_MEANING,
        createdAtMillis: Long = 0,
        suppressedByTaskType: String = "",
        suppressedAtMillis: Long = 0,
        activeToken: String? = null,
    ): StudyQueueItem = StudyQueueItem(
        kanji = kanji,
        state = state,
        dueAtMillis = dueAtMillis,
        stability = 3.0,
        difficulty = 6.0,
        totalReviews = totalReviews,
        lapses = lapses,
        learningStep = 0,
        writingLevel = 0,
        answerSignature = answerSignature,
        rung = rung,
        phase = StudyPhase.REVIEW,
        suppressedByTaskType = suppressedByTaskType,
        activeToken = activeToken,
        createdAtMillis = createdAtMillis,
        suppressedAtMillis = suppressedAtMillis,
    )
}
