package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Checked-in, human-readable lifecycles for the canonical routing-v2 scheduler.
 *
 * The older [SchedulerTimelineSimulatorTest] resources intentionally retain
 * lazy-conversion and legacy-ladder coverage. These snapshots expose the
 * routing-v2 state that those timelines cannot render: both core memories,
 * inline repair, recurrence, revalidation, and whether FSRS was called.
 */
class AdaptiveSchedulerGoldenSnapshotTest {
    private val ladder = RecordsBase.StudyLadderSettings.defaults()
    private val settings = RecordsSyncModels.Settings.kikuDefaults()
    private val parameters = RecordsSchedulerModels.SchedulerParameters.defaults()
    private val steps = RecordsSchedulerModels.LearningStepSettings.defaults()

    @Test
    fun adaptiveLifecyclesMatchGoldenSnapshots() {
        val actual = linkedMapOf(
            "new-to-reading" to newToReadingTimeline(),
            "recognition-visual-lapse" to recognitionVisualLapseTimeline(),
            "reading-exact-repair" to readingExactRepairTimeline(),
            "reading-typed-fallback" to readingTypedFallbackTimeline(),
            "stuck-repair-escalation" to stuckRepairEscalationTimeline(),
        )

        actual.forEach { (name, timeline) ->
            assertEquals("Adaptive scheduler golden changed: $name", golden(name), timeline)
        }
    }

    private fun newToReadingTimeline(): String {
        val adapter = RecordingAdapter(intervalDays = 30, promotionDays = 30)
        val engine = AdaptiveReviewTransitionEngine(adapter)
        val timeline = Timeline(adapter)
        var item = adaptiveItem(
            AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION),
            hasSentenceReading = true,
        ).copyBuilder()
            .realPassStreak(settings.ladderPromotionMinPasses - 1)
            .build()

        timeline.state("recognition-due", item)
        var transition = engine.apply(
            item,
            request("good", StudyTaskTypes.KANJI_MEANING),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        )
        item = transition.item
        timeline.transition("recognition-pass-promotes", StudyTaskTypes.KANJI_MEANING, transition)

        item = item.copyBuilder().dueAtMillis(NOW).activeToken("reading-token").build()
        timeline.state("reading-due", item)
        transition = engine.apply(
            item,
            request("good", StudyTaskTypes.WORD_READING),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        )
        timeline.transition("reading-pass", StudyTaskTypes.WORD_READING, transition)
        return timeline.render()
    }

    private fun recognitionVisualLapseTimeline(): String {
        val adapter = RecordingAdapter(intervalDays = 5, promotionDays = 5)
        val engine = AdaptiveReviewTransitionEngine(adapter)
        val timeline = Timeline(adapter)
        var item = adaptiveItem(
            AdaptiveRouteState(
                activeCore = CoreSkill.RECOGNITION,
                recognitionReviewCount = 10,
            ),
            hasSimilarKanji = true,
        )

        timeline.state("recognition-due", item)
        var transition = engine.apply(
            item,
            request(
                rating = "again",
                taskType = StudyTaskTypes.KANJI_MEANING,
                failure = FailureKind.VISUAL_CONFUSION,
            ),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        )
        item = transition.item
        timeline.transition("visual-fail", StudyTaskTypes.KANJI_MEANING, transition)

        item = item.copyBuilder().dueAtMillis(NOW).activeToken("repair-token").build()
        transition = engine.apply(
            item,
            request("good", StudyTaskTypes.SIMILAR_KANJI),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        )
        item = transition.item
        timeline.transition("repair-pass", StudyTaskTypes.SIMILAR_KANJI, transition)

        item = item.copyBuilder().dueAtMillis(NOW).activeToken("revalidation-token").build()
        transition = engine.apply(
            item,
            request("good", StudyTaskTypes.KANJI_MEANING),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        )
        timeline.transition("revalidation-pass", StudyTaskTypes.KANJI_MEANING, transition)
        return timeline.render()
    }

    private fun readingExactRepairTimeline(): String {
        val adapter = RecordingAdapter(intervalDays = 5, promotionDays = 5)
        val engine = AdaptiveReviewTransitionEngine(adapter)
        val timeline = Timeline(adapter)
        var item = adaptiveItem(
            AdaptiveRouteState(
                activeCore = CoreSkill.CONTEXTUAL_READING,
                contextualReadingReviewCount = 4,
            ),
            hasKanjiReading = true,
            hasSentenceReading = true,
        )

        timeline.state("reading-due", item)
        var transition = engine.apply(
            item,
            request(
                rating = "again",
                taskType = StudyTaskTypes.WORD_READING,
                failure = FailureKind.WRONG_READING,
                correctAnswer = "だっしゅつ",
            ),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        )
        item = transition.item
        timeline.transition("reading-fail", StudyTaskTypes.WORD_READING, transition)

        val repairTask = AdaptiveStudyItemPolicy.routeState(item)!!.activeRepairTask()!!
        item = item.copyBuilder().dueAtMillis(NOW).activeToken("repair-token").build()
        transition = engine.apply(
            item,
            request("good", repairTask),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        )
        item = transition.item
        timeline.transition("exact-repair-pass", repairTask, transition)

        item = item.copyBuilder().dueAtMillis(NOW).activeToken("revalidation-token").build()
        transition = engine.apply(
            item,
            request("good", StudyTaskTypes.WORD_READING),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        )
        timeline.transition("reading-revalidation-pass", StudyTaskTypes.WORD_READING, transition)
        return timeline.render()
    }

    private fun readingTypedFallbackTimeline(): String {
        val adapter = RecordingAdapter(intervalDays = 5, promotionDays = 5)
        val engine = AdaptiveReviewTransitionEngine(adapter)
        val timeline = Timeline(adapter)
        var item = adaptiveItem(
            AdaptiveRouteState(
                activeCore = CoreSkill.CONTEXTUAL_READING,
                contextualReadingReviewCount = 2,
            ),
        )

        timeline.state("reading-due", item)
        var transition = engine.apply(
            item,
            request(
                rating = "again",
                taskType = StudyTaskTypes.WORD_READING,
                failure = FailureKind.WRONG_READING,
                correctAnswer = "だっしゅつ",
            ),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        )
        item = transition.item
        timeline.transition("reading-fail", StudyTaskTypes.WORD_READING, transition)

        val repairTask = AdaptiveStudyItemPolicy.routeState(item)!!.activeRepairTask()!!
        item = item.copyBuilder().dueAtMillis(NOW).activeToken("typed-token").build()
        transition = engine.apply(
            item,
            request("good", repairTask),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        )
        timeline.transition("typed-repair-pass", repairTask, transition)
        return timeline.render()
    }

    private fun stuckRepairEscalationTimeline(): String {
        val adapter = RecordingAdapter(intervalDays = 5, promotionDays = 5)
        val engine = AdaptiveReviewTransitionEngine(adapter)
        val timeline = Timeline(adapter)
        val item = adaptiveItem(
            AdaptiveRouteState(
                activeCore = CoreSkill.RECOGNITION,
                recurringFailure = FailureKind.VISUAL_CONFUSION,
                recurringFailureCount = settings.ladderDemotionFailStreak - 1,
                revalidationPending = true,
            ),
            hasSimilarKanji = true,
        )

        timeline.state("revalidation-due", item)
        val transition = engine.apply(
            item,
            request(
                rating = "again",
                taskType = StudyTaskTypes.KANJI_MEANING,
                failure = FailureKind.VISUAL_CONFUSION,
            ),
            NOW,
            parameters,
            settings,
            steps,
            ladder,
        )
        timeline.transition("repeat-fail-escalates", StudyTaskTypes.KANJI_MEANING, transition)
        return timeline.render()
    }

    private fun adaptiveItem(
        route: AdaptiveRouteState,
        hasSimilarKanji: Boolean = false,
        hasKanjiReading: Boolean = false,
        hasSentenceReading: Boolean = false,
    ): RecordsStudyModels.StudyItem {
        val owner = AdaptiveCorePolicy.memoryOwnerTaskType(route.activeCore)
        return baseItem(AdaptiveCorePolicy.memoryOwnerRung(route.activeCore))
            .withTaskMemory(owner, memory(totalReviews = 4, dueAt = NOW - 1L))
            .copyBuilder()
            .hasSimilarKanji(hasSimilarKanji)
            .hasKanjiReading(hasKanjiReading)
            .hasSentenceReading(hasSentenceReading)
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .activeToken("token")
            .build()
    }

    private fun baseItem(rung: RecordsBase.LadderRung): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(
            "脱",
            StudyLadderRules.STATE_REVIEW,
            NOW - 1L,
            4.0,
            5.0,
            4,
            0,
            0,
            1,
            null,
            NOW - 1000L,
        ).copyBuilder()
            .rung(rung)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .matureIntervalDays(4)
            .build()

    private fun memory(totalReviews: Int, dueAt: Long) = RecordsStudyModels.TaskMemory.fromFields(
        RecordsStudyModels.TaskMemory.Fields(
            state = StudyLadderRules.STATE_REVIEW,
            dueAtMillis = dueAt,
            stability = 4.0,
            difficulty = 5.0,
            totalReviews = totalReviews,
            lapses = 0,
            learningStep = 0,
            lastRating = StudyRatings.GOOD,
            matureIntervalDays = 4,
            lastReviewedAtMillis = NOW - StudyLadderRules.DAY,
        ),
    )

    private fun request(
        rating: String,
        taskType: String,
        failure: FailureKind? = null,
        correctAnswer: String = "",
    ): RecordsSchedulerModels.ReviewRequest {
        val evidence = AnswerEvidence(
            coreSkill = AdaptiveCorePolicy.coreForTaskType(taskType),
            failureKind = failure,
            evidenceSource = if (failure == null) null else EvidenceSource.SELF_REPORT,
            correctAnswer = correctAnswer,
            renderedExpression = "脱出",
            renderedReading = "だっしゅつ",
        )
        return RecordsSchedulerModels.ReviewRequest(
            "脱",
            "token",
            rating,
            false,
            false,
            false,
            false,
            0,
            taskType,
            "",
            correctAnswer,
        ).withEvidence(
            RecordsSchedulerModels.ReviewRequest.ReviewEvidence(
                evidence.coreSkill?.wireName(),
                evidence.failureKind?.wireName(),
                evidence.evidenceSource?.wireName(),
                evidence.selectedAnswer,
                evidence.correctAnswer,
                AnswerEvidenceCodec.encode(evidence),
            ),
        )
    }

    private fun golden(name: String): String {
        val path = "/dev/bee/kanjianki/core/scheduler-goldens/adaptive-v31/$name.timeline.txt"
        val resource = javaClass.getResource(path)
        assertNotNull("Missing adaptive scheduler golden resource $path", resource)
        return resource!!.readText().trimEnd()
    }

    private inner class Timeline(private val adapter: RecordingAdapter) {
        private val lines = mutableListOf<String>()

        fun state(label: String, item: RecordsStudyModels.StudyItem) {
            lines += render(label, item, requestedTask = "-", appliedRating = "-", fsrsCalled = false)
        }

        fun transition(
            label: String,
            requestedTask: String,
            transition: AdaptiveReviewTransitionEngine.Transition,
        ) {
            lines += render(
                label,
                transition.item,
                requestedTask,
                transition.appliedRating,
                transition.fsrsCalled,
            )
        }

        fun render(): String = lines.joinToString("\n")

        private fun render(
            label: String,
            item: RecordsStudyModels.StudyItem,
            requestedTask: String,
            appliedRating: String,
            fsrsCalled: Boolean,
        ): String {
            val route = AdaptiveStudyItemPolicy.routeState(item)!!
            val renderedTask = AdaptiveStudyItemPolicy.taskTypeFor(item, ladder)
            return buildString {
                append(label)
                append(" request=").append(requestedTask)
                append(" rating=").append(appliedRating)
                append(" rendered=").append(renderedTask)
                append(" variant=").append(variantFor(renderedTask))
                append(" core=").append(route.activeCore.wireName())
                append(" rung=").append(item.rung.name)
                append(" phase=").append(item.phase.name)
                append(" due=").append(offset(item.dueAtMillis))
                append(" coreDue=").append(offset(route.coreDueAtMillis))
                append(" repairDue=").append(offset(route.repairDueAtMillis))
                append(" repairs=").append(route.activeRepairTasks)
                append(" repairIndex=").append(route.repairTaskIndex)
                append(" repairAttempts=").append(route.repairAttemptCount)
                append(" revalidate=").append(route.revalidationPending)
                append(" recurrence=").append(route.recurringFailure?.wireName() ?: "-")
                    .append(":").append(route.recurringFailureCount)
                append(" recognitionCount=").append(route.recognitionReviewCount)
                append(" readingCount=").append(route.contextualReadingReviewCount)
                append(" recognition=").append(memoryText(item.kanjiMeaningMemory))
                append(" reading=").append(memoryText(item.wordReadingMemory))
                append(" fsrsCalled=").append(fsrsCalled)
                append(" fsrsCalls=").append(adapter.calls)
            }
        }
    }

    private class RecordingAdapter(
        private val intervalDays: Int,
        private val promotionDays: Int,
    ) : KaniFsrsAdapter {
        val calls = mutableListOf<String>()

        override fun initialReview(
            rating: String?,
            currentStability: Double,
            currentDifficulty: Double,
            targetRetention: Double,
            isNewLearning: Boolean,
        ): KaniFsrsReviewResult =
            KaniFsrsReviewResult(currentStability, currentDifficulty, StudyLadderRules.DAY)

        override fun review(
            stability: Double,
            difficulty: Double,
            rating: String?,
            elapsedDays: Double,
            targetRetention: Double,
        ): KaniFsrsReviewResult {
            // Formatted to four decimals rather than interpolated raw. FSRS-7 elapsed
            // times are fractional, and a bare Double would put values like
            // 0.006944444444444444 into a golden file — unreadable, and sensitive to
            // the last bit of a division. Four decimals still distinguish ten minutes
            // (0.0069) from an hour (0.0417), which is the resolution these timelines
            // are about.
            calls += "${StudyRatings.normalize(rating)}:${"%.4f".format(Locale.ROOT, elapsedDays)}"
            return KaniFsrsReviewResult(
                stability + 1.0,
                difficulty,
                intervalDays * StudyLadderRules.DAY,
                promotionDays * StudyLadderRules.DAY,
            )
        }
    }

    private fun memoryText(memory: RecordsStudyModels.TaskMemory): String =
        "{reviews=${memory.totalReviews},lapses=${memory.lapses},interval=${memory.matureIntervalDays}," +
            "stability=${oneDecimal(memory.stability)},difficulty=${oneDecimal(memory.difficulty)}," +
            "due=${offset(memory.dueAtMillis)}}"

    private fun oneDecimal(value: Double): String = String.format(Locale.ROOT, "%.1f", value)

    private fun variantFor(taskType: String): String = when (taskType) {
        StudyTaskTypes.KANJI_MEANING -> PresentationVariant.STANDARD_GLYPH.wireName()
        StudyTaskTypes.FONT_MEANING -> PresentationVariant.FONT_GLYPH.wireName()
        StudyTaskTypes.WORD_READING -> PresentationVariant.PLAIN_WORD.wireName()
        StudyTaskTypes.SENTENCE_READING -> PresentationVariant.SENTENCE_CONTEXT.wireName()
        else -> "repair"
    }

    private fun offset(timestamp: Long): String {
        if (timestamp <= 0L) return "-"
        val delta = timestamp - NOW
        return when {
            delta == 0L -> "T+0m"
            delta % StudyLadderRules.DAY == 0L ->
                "T${if (delta > 0) "+" else ""}${delta / StudyLadderRules.DAY}d"
            delta % 60_000L == 0L ->
                "T${if (delta > 0) "+" else ""}${delta / 60_000L}m"
            else -> "T${if (delta > 0) "+" else ""}${delta}ms"
        }
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
