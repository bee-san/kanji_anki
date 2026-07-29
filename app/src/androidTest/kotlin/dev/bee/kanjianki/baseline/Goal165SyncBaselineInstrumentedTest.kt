package dev.bee.kanjianki.baseline

import android.content.Context
import android.database.DatabaseUtils
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.FakeAnkiDroidProvider
import dev.bee.kanjianki.core.AdaptiveStudyItemPolicy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.sync.SyncProgress
import dev.bee.kanjianki.sync.createManualSyncEngine
import dev.bee.kanjianki.testing.DeviceRisk
import dev.bee.kanjianki.platform.AppClock
import java.io.File
import java.nio.charset.StandardCharsets
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Sanitized Android sync/store baseline retained separately from the provider adapter. */
@RunWith(AndroidJUnit4::class)
@DeviceRisk
class Goal165SyncBaselineInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        resetProvider()
    }

    @After
    fun tearDown() {
        if (::context.isInitialized) {
            resetProvider()
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    @Test
    fun sanitizedSyncContractMatchesGoldenSnapshot() {
        val actual = actualSnapshot()
        if (
            InstrumentationRegistry.getArguments()
                .getString(RECORD_ARGUMENT)
                ?.toBooleanStrictOrNull() == true
        ) {
            val output = File(requireNotNull(context.getExternalFilesDir(null)), "goal165-sync.snapshot.txt")
            output.writeText("$actual\n", StandardCharsets.UTF_8)
            return
        }
        assertEquals(expectedSnapshot(), actual)
    }

    private fun actualSnapshot(): String = buildString {
        appendLine("goal165 sync baseline v1")
        appendLine("personal-data=none")
        appendManualSyncBaseline()
    }.trimEnd()

    private fun StringBuilder.appendManualSyncBaseline() {
        LocalStore(context).use { store ->
            val settings = RecordsSyncModels.Settings.kikuDefaults()
            val engine = createManualSyncEngine(
                context,
                store,
                gateway(),
                settings,
                SyncProgress.NONE,
                AppClock { FIXED_TIME_MILLIS },
            ).apply {
                reminderRescheduler = Runnable {}
                widgetRefresher = Runnable {}
            }
            val result = engine.run()
            val latest = requireNotNull(store.latestSync())
            val rows = store.dashboardRows().sortedBy { it.kanji }
            val imports = store.suspendedImports().sortedBy { it.kanji }
            val queue = store.studyItems().sortedBy { it.kanji }

            appendLine()
            appendLine("[manual-sync-fixed-clock]")
            appendLine(
                "result success=${result.success} skipped=${result.skipped} rows=${result.dashboardRows} " +
                    "imports=${result.importedSuspendedKanji} ready=${result.studyReadyCount} " +
                    "retryable=${result.retryable} cleanup=${result.message?.isNotBlank() == true}",
            )
            appendLine(
                "latest status=${latest.status} active-notes=${latest.activeNotes} " +
                    "active-cards=${latest.activeCards} suspended-cards=${latest.suspendedCards} " +
                    "imports=${latest.importedKanji} finished=${timeOffset(latest.finishedAt)} " +
                    "error=${latest.errorMessage.isNotBlank()} cleanup=${latest.removalMessage.isNotBlank()}",
            )
            appendLine(
                "history-count=" +
                    DatabaseUtils.queryNumEntries(store.readableDatabase, LocalStoreBase.TABLE_SYNC_RUNS),
            )
            rows.forEach { row ->
                appendLine(
                    "dashboard kanji=${row.kanji} rank=${nullable(row.jitenRank)} weakness=${row.weaknessScore} " +
                        "reason=${row.reasonCode} active=${row.activeExampleCount} " +
                        "suspended=${row.suspendedExampleCount} mature=${row.matureSupportCount} " +
                        "sources=" + row.examples.sortedWith(
                            compareBy({ it.cardId }, { it.noteId }, { it.sourceType }),
                        ).joinToString(",") { example ->
                            "${example.cardId}:${example.noteId}:${example.sourceType}"
                        },
                )
            }
            imports.forEach { imported ->
                appendLine(
                    "import kanji=${imported.kanji} rank=${nullable(imported.jitenRank)} " +
                        "known=${imported.rankKnown} cutoff=${imported.cutoffUsed} sources=" +
                        imported.sources.sortedWith(
                            compareBy({ it.cardId }, { it.noteId }, { it.sourceType }),
                        ).joinToString(",") { source ->
                            "${source.cardId}:${source.noteId}:${source.sourceType}:" +
                                source.ruleTypes.sorted().joinToString("+")
                        },
                )
            }
            queue.forEach { item ->
                val route = AdaptiveStudyItemPolicy.routeState(item)
                appendLine(
                    "queue kanji=${item.kanji} task=" +
                        "${AdaptiveStudyItemPolicy.taskTypeFor(item, store.studyLadderSettings())} " +
                        "rung=${item.rung.wireName()} phase=${item.phase.wireName()} state=${item.state} " +
                        "due=${timeOffset(item.dueAtMillis)} routing=${item.routingVersion} " +
                        "adaptive=${AdaptiveStudyItemPolicy.isAdaptive(item)} token=${item.activeToken != null} " +
                        "core=${route?.activeCore?.wireName() ?: "-"} repair=${route?.activeRepairTask() ?: "-"}",
                )
            }
        }
    }

    private fun expectedSnapshot(): String =
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("goal165/sync.snapshot.txt")
            .use { input -> String(input.readBytes(), StandardCharsets.UTF_8).trimEnd() }

    private fun gateway(): AnkiDroidGateway =
        AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

    private fun resetProvider() {
        context.contentResolver.call(providerUri(), "reset", null, null)
    }

    private fun providerUri(): Uri =
        Uri.parse("content://${FakeAnkiDroidProvider.AUTHORITY}")

    private fun nullable(value: Int?): String = value?.toString() ?: "-"

    private fun timeOffset(value: Long): String =
        if (value == FIXED_TIME_MILLIS) "T+0" else "T${value - FIXED_TIME_MILLIS}"

    private companion object {
        private const val DATABASE_NAME = "kanji_anki_simple.db"
        private const val FIXED_TIME_MILLIS = 1_700_000_000_000L
        private const val RECORD_ARGUMENT = "goal165RecordSyncBaseline"
    }
}
