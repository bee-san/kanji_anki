package dev.bee.kanjianki

import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SchedulerTraceFormatter
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.StudyQueueSeeder
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudyProgressMatrixHarnessTest {
    private val scheduler = BridgeScheduler.withWeights(null)
    private val settings = RecordsSyncModels.Settings.kikuDefaults()
    private val ladder = RecordsBase.StudyLadderSettings.defaults()
    private val nowMillis = 1_700_000_000_000L
    private val studyAheadMillis = 5 * 60_000L
    private val shortProjectionMillis = 15 * 60_000L
    private val longProjectionMillis = 2 * 60 * 60_000L
    private val logPath = File("/Users/autumnskerritt/.hermes/kanban/boards/kani/workspaces/t_0be60e34/build/reports/study-progress-matrix.log")

    @After
    fun tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
    }

    @Test
    fun matrixHarnessWritesDetailedLog() {
        val lines = mutableListOf<String>()
        lines += "build_sha=${git("rev-parse HEAD")}".trim()
        lines += "build_version=${BuildConfig.VERSION_NAME}"
        lines += "build_ancestry=${git("rev-list --parents -n 1 HEAD")}".trim()
        lines += "study-progress-harness build=${BuildConfig.VERSION_NAME} now=$nowMillis"
        lines += "lookahead=$studyAheadMillis shortProjection=$shortProjectionMillis longProjection=$longProjectionMillis"
        lines += runMatrixScenario("seven-visible", visibleCount = 7, futureCount = 0)
        lines += runMatrixScenario("five-of-seven", visibleCount = 5, futureCount = 2)
        lines += runAgainTrace()
        logPath.parentFile?.mkdirs()
        logPath.writeText(lines.joinToString("\n", postfix = "\n"))

        assertTrue(logPath.exists())
        assertTrue(logPath.readText().contains("[five-of-seven] terminal no-session completed=5/7"))
        assertTrue(logPath.readText().contains("[seven-visible] terminal no-session completed=7/7"))
    }

    @Test
    fun visibleFiveOfSevenMustNotRouteDone() {
        val activity = createActivity()
        try {
            val coordinator = MainActivityStudyQueueCoordinator(activity)
            val plan = matrixPlan(totalCount = 7)
            val rows = matrixRows(totalCount = 7)
            val items = matrixItems(rows, visibleCount = 5, futureCount = 2)
            activity.studySessionTracker.setTargetCount(7)
            repeat(5) { index ->
                val key = "session:kanji_meaning:${rows[index].kanji}:token-$index"
                activity.studySessionTracker.markTaskCompleted(key)
            }

            val decision = pendingRepairOrDoneRender(
                coordinator = coordinator,
                plan = plan,
                items = items,
            )

            assertFalse("visible 5/7 must not route Done", decision)
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    fun visibleSevenOfSevenRoutesDoneControl() {
        val activity = createActivity()
        try {
            val coordinator = MainActivityStudyQueueCoordinator(activity)
            val plan = matrixPlan(totalCount = 7)
            val rows = matrixRows(totalCount = 7)
            val items = matrixItems(rows, visibleCount = 7, futureCount = 0)
            activity.studySessionTracker.setTargetCount(7)
            repeat(7) { index ->
                val key = "session:kanji_meaning:${rows[index].kanji}:token-$index"
                activity.studySessionTracker.markTaskCompleted(key)
            }

            val decision = pendingRepairOrDoneRender(
                coordinator = coordinator,
                plan = plan,
                items = items,
            )

            assertTrue("visible 7/7 should route Done", decision)
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private fun runMatrixScenario(label: String, visibleCount: Int, futureCount: Int): List<String> {
        val activity = createActivity()
        try {
            val coordinator = MainActivityStudyQueueCoordinator(activity)
            val rows = matrixRows(visibleCount + futureCount)
            var items = matrixItems(rows, visibleCount, futureCount)
            val allowedKanji = rows.map { it.kanji }.toSet()
            val plan = matrixPlan(visibleCount + futureCount)
            activity.studySessionTracker.setTargetCount(plan.target)

            val initialRawTaskKeys = scheduler.randomizedSessionTaskKeys(
                items,
                rows,
                nowMillis,
                studyAheadMillis,
                allowedKanji,
                settings,
                ladder,
                0L,
            )
            val lines = mutableListOf<String>()
            lines += "[$label] initial plan target=${plan.target} remaining=${plan.remaining} focusComplete=${plan.focusComplete()} rawTaskKeys=${initialRawTaskKeys.size} dueNow=${scheduler.dueCount(items, rows, nowMillis, studyAheadMillis, ladder)} due@15m=${scheduler.dueCount(items, rows, nowMillis, shortProjectionMillis, ladder)} due@2h=${scheduler.dueCount(items, rows, nowMillis, longProjectionMillis, ladder)}"

            var completed = 0
            var currentRawTaskKeys = initialRawTaskKeys
            while (currentRawTaskKeys.isNotEmpty() && completed < visibleCount) {
                val session = scheduler.nextSessionForTaskKeys(
                    items,
                    rows,
                    nowMillis,
                    studyAheadMillis,
                    allowedKanji,
                    settings,
                    ladder,
                    currentRawTaskKeys,
                ) ?: break
                val item = requireNotNull(session.item)
                val beforeProgress = activity.studySessionTracker.topBarProgress(true, false)
                lines += "[$label] show#${completed + 1} task=${StudySessionTracker.sessionTaskKey(session)} kanji=${item.kanji} taskType=${session.taskType} progress=${beforeProgress.completed}/${beforeProgress.target} target=${activity.studySessionTracker.targetCount()} pending=${currentRawTaskKeys.size}/${initialRawTaskKeys.size} dueNow=${scheduler.dueCount(items, rows, nowMillis, studyAheadMillis, ladder)} due@15m=${scheduler.dueCount(items, rows, nowMillis, shortProjectionMillis, ladder)} due@2h=${scheduler.dueCount(items, rows, nowMillis, longProjectionMillis, ladder)} sessionGeneration=${item.schedulerRevision}/${item.routingVersion}"

                val reviewRequest = RecordsSchedulerModels.ReviewRequest(
                    item.kanji,
                    session.token,
                    BridgeScheduler.RATING_GOOD,
                    false,
                    false,
                    false,
                    false,
                    0,
                    session.taskType,
                    item.answerSignature,
                    session.prompt,
                )
                val traced = scheduler.debugTraceApplyReview(
                    BridgeScheduler.ReviewApplication.builder(item, reviewRequest, scheduler.tokenSet(emptyList()), nowMillis)
                        .settings(settings)
                        .ladder(ladder)
                        .build(),
                )
                items = items.map { if (it.kanji == item.kanji) traced.result.item else it }
                activity.studySessionTracker.markPlannedSessionTaskCompleted(session.taskType, item.kanji)
                activity.studySessionTracker.markTaskCompleted(StudySessionTracker.sessionTaskKey(session))
                completed++
                lines += "[$label] answer#$completed rating=good trace=${SchedulerTraceFormatter.developerExplanation(traced.trace)}"

                val nextRawTaskKeys = scheduler.randomizedSessionTaskKeys(
                    items,
                    rows,
                    nowMillis,
                    studyAheadMillis,
                    allowedKanji,
                    settings,
                    ladder,
                    0L,
                )
                val afterProgress = activity.studySessionTracker.topBarProgress(false, false)
                val decision = pendingRepairOrDoneRender(
                    coordinator = coordinator,
                    plan = plan,
                    items = items,
                )
                lines += "[$label] continue#$completed progress=${afterProgress.completed}/${afterProgress.target} topBar=${afterProgress.completed}/${afterProgress.target} nextPending=${nextRawTaskKeys.size} studiedToday=${activity.studySessionTracker.completedCount()} remaining=${maxOf(0, plan.remaining - completed)} dueNow=${scheduler.dueCount(items, rows, nowMillis, studyAheadMillis, ladder)} due@15m=${scheduler.dueCount(items, rows, nowMillis, shortProjectionMillis, ladder)} due@2h=${scheduler.dueCount(items, rows, nowMillis, longProjectionMillis, ladder)} decision=${if (decision) "done" else "keep"}"
                currentRawTaskKeys = nextRawTaskKeys
            }

            val terminalProgress = activity.studySessionTracker.topBarProgress(false, false)
            lines += "[$label] terminal no-session completed=${activity.studySessionTracker.completedCount()}/${activity.studySessionTracker.targetCount()} topBar=${terminalProgress.completed}/${terminalProgress.target} planRemaining=${maxOf(0, plan.remaining - completed)} focusComplete=${plan.focusComplete()} dueNow=${scheduler.dueCount(items, rows, nowMillis, studyAheadMillis, ladder)} due@15m=${scheduler.dueCount(items, rows, nowMillis, shortProjectionMillis, ladder)} due@2h=${scheduler.dueCount(items, rows, nowMillis, longProjectionMillis, ladder)}"
            return lines
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private fun runAgainTrace(): List<String> {
        val row = matrixRow("裂", 1)
        val item = matrixItem(row, nowMillis)
        val request = RecordsSchedulerModels.ReviewRequest(
            item.kanji,
            "again-trace-token",
            BridgeScheduler.RATING_AGAIN,
            false,
            false,
            false,
            false,
            0,
            BridgeScheduler.TASK_KANJI_MEANING,
            item.answerSignature,
            row.reasonText,
        )
        val traced = scheduler.debugTraceApplyReview(
            BridgeScheduler.ReviewApplication.builder(item, request, scheduler.tokenSet(emptyList()), nowMillis)
                .settings(settings)
                .ladder(ladder)
                .build(),
        )
        return listOf(
            "[again-trace] ${SchedulerTraceFormatter.developerExplanation(traced.trace)}",
            "[again-trace] result phase=${traced.result.item.phase.wireName()} dueAt=${traced.result.item.dueAtMillis} now=$nowMillis",
        )
    }

    private fun pendingRepairOrDoneRender(
        coordinator: MainActivityStudyQueueCoordinator,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan,
        items: List<RecordsStudyModels.StudyItem>,
    ): Boolean {
        val method = coordinator.javaClass.declaredMethods.single { it.name == "pendingRepairOrDoneRender" }
        method.isAccessible = true
        val render = method.invoke(
            coordinator,
            plan,
            nowMillis,
            ladder,
            items,
            emptyList<RecordsImportModels.SimilarKanjiWritingRepair>(),
            null,
        )
        return render != null
    }

    private fun createActivity(): MainActivity {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        val activity = Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get()
        activity.cancelPendingHomeRouteLoads()
        return activity
    }

    private fun matrixPlan(totalCount: Int): RecordsSchedulerModels.AdaptiveLoadPlan {
        val focusKanji = matrixRows(totalCount).map { it.kanji }
        return RecordsSchedulerModels.AdaptiveLoadPlan(
            20,
            totalCount,
            totalCount,
            focusKanji,
            totalCount,
            true,
            "study-progress-matrix",
        )
    }

    private fun matrixRows(totalCount: Int): List<RecordsImportModels.DashboardRow> {
        val kanji = listOf("乙", "戊", "丙", "丁", "甲", "庚", "己")
        return kanji.take(totalCount).mapIndexed { index, value -> matrixRow(value, index + 1) }
    }

    private fun matrixRow(kanji: String, rank: Int): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            rank,
            "meaning-$kanji",
            "reading-$kanji",
            "browser-$kanji",
            0,
            "focus",
            "reason-$kanji",
            0,
            0,
            0,
            emptyList<RecordsImportModels.Example>(),
        )
    }

    private fun matrixItems(
        rows: List<RecordsImportModels.DashboardRow>,
        visibleCount: Int,
        futureCount: Int,
    ): List<RecordsStudyModels.StudyItem> {
        val visibleItems = rows.take(visibleCount).map { matrixItem(it, nowMillis) }
        val futureItems = rows.drop(visibleCount).take(futureCount).map { matrixItem(it, nowMillis + 90 * 60_000L) }
        return visibleItems + futureItems
    }

    private fun matrixItem(row: RecordsImportModels.DashboardRow, dueAtMillis: Long): RecordsStudyModels.StudyItem {
        val signature = StudyQueueSeeder.answerSignature(row)
        return RecordsStudyModels.StudyItem(
            row.kanji,
            "review",
            dueAtMillis,
            1.0,
            5.0,
            1,
            0,
            0,
            0,
            signature,
            dueAtMillis,
        ).copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .answerSignature(signature)
            .schedulerRevision(1L)
            .routingVersion(1)
            .build()
    }

    private fun fakeAnkiDroidGateway(): AnkiDroidGateway {
        val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(android.content.Context::class.java, List::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(
            ApplicationProvider.getApplicationContext<android.content.Context>(),
            emptyList<Any>(),
        ) as AnkiDroidGateway
    }

    private fun git(args: String): String {
        val command = listOf("git") + args.split(" ").filter { it.isNotBlank() }
        val process = ProcessBuilder(command)
            .directory(File(requireNotNull(System.getProperty("user.dir"))))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        check(process.waitFor() == 0) { "git $args failed: $output" }
        return output
    }
}
