package dev.bee.kanjianki

import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.AutoSyncSettingsTogglePolicy
import dev.bee.kanjianki.core.LearningStepsSettingsPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.StudyLadderThresholdPolicy
import dev.bee.kanjianki.core.WorkloadSettingsPolicy
import dev.bee.kanjianki.sync.SyncSettings
import dev.bee.kanjianki.updatecore.AutoUpdateSettingsTogglePolicy
import java.util.LinkedHashMap
import java.util.Locale
import kotlin.io.path.Path
import kotlin.io.path.writeText
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class SettingsWriteActionsTest {
    @Test
    fun saveLadderThresholdsWritesPrimaryAndCompatibilityKeys() {
        val settings = LinkedHashMap<String, Int>()

        SettingsWriteActions.saveLadderThresholds(
            StudyLadderThresholdPolicy.saveRequest("21", "3"),
            { key: String, value: Int -> settings[key] = value }
        )

        assertEquals(3, settings.size)
        assertEquals(21, settings[SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY])
        assertEquals(3, settings[SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY])
        assertEquals(3, settings[SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY])
        // writing_trigger_miss_days is a days value, not a fail-streak count,
        // and must never be overwritten by the ladder threshold save.
        assertFalse(settings.containsKey(SyncSettings.WRITING_TRIGGER_MISS_DAYS_SETTING_KEY))
    }

    @Test
    fun saveLadderThresholdsIgnoresInvalidRequests() {
        val settings = LinkedHashMap<String, Int>()

        SettingsWriteActions.saveLadderThresholds(
            StudyLadderThresholdPolicy.saveRequest("0", "3"),
            { key: String, value: Int -> settings[key] = value }
        )

        assertTrue(settings.isEmpty())
    }

    @Test
    fun saveNoteTypeFieldsWritesAllFieldMappingKeys() {
        val settings = LinkedHashMap<String, String>()

        SettingsWriteActions.saveNoteTypeFields(
            SettingsWriteActions.NoteTypeFieldWriteRequest(
                "Kiku",
                "Expression",
                "Reading",
                "Meaning",
                "Sentence",
                "Frequency",
                "FrequencySort"
            ),
            { key: String, value: String? -> settings[key] = value.orEmpty() }
        )

        assertEquals(7, settings.size)
        assertEquals("Kiku", settings[SyncSettings.NOTE_TYPE_SETTING_KEY])
        assertEquals("Expression", settings[SyncSettings.EXPRESSION_FIELD_SETTING_KEY])
        assertEquals("Reading", settings[SyncSettings.READING_FIELD_SETTING_KEY])
        assertEquals("Meaning", settings[SyncSettings.MEANING_FIELD_SETTING_KEY])
        assertEquals("Sentence", settings[SyncSettings.SENTENCE_FIELD_SETTING_KEY])
        assertEquals("Frequency", settings[SyncSettings.FREQUENCY_FIELD_SETTING_KEY])
        assertEquals("FrequencySort", settings[SyncSettings.FREQUENCY_SORT_FIELD_SETTING_KEY])
    }

    @Test
    fun noteTypeFieldWriteRequestKeepsJavaRecordSemanticsAndNullComponents() {
        val request = SettingsWriteActions.NoteTypeFieldWriteRequest(
            "Kiku",
            "Expression",
            null,
            null,
            null,
            null,
            null
        )

        assertTrue(SettingsWriteActions.NoteTypeFieldWriteRequest::class.java.isRecord)
        assertEquals("Kiku", request.noteType)
        assertEquals("Expression", request.expressionField)
        assertNull(request.readingField)
        assertEquals(
            request,
            SettingsWriteActions.NoteTypeFieldWriteRequest("Kiku", "Expression", null, null, null, null, null)
        )
    }

    @Test
    fun saveLearningStepsWritesValidParsedSettingsOnly() {
        val writer = RecordingLearningStepWriter()

        SettingsWriteActions.saveLearningSteps(
            LearningStepsSettingsPolicy.saveRequest("1m 10m", "5m"),
            writer
        )
        SettingsWriteActions.saveLearningSteps(
            LearningStepsSettingsPolicy.saveRequest("bad", "5m"),
            writer
        )

        assertEquals("1m, 10m", writer.settings!!.newStepsText())
        assertEquals("5m", writer.settings!!.reviewStepsText())
    }

    @Test
    fun saveLearningStepsPreservesEmptyReviewSteps() {
        val writer = RecordingLearningStepWriter()

        SettingsWriteActions.saveLearningSteps(
            LearningStepsSettingsPolicy.saveRequest("1m 10m", ""),
            writer
        )

        assertEquals("1m, 10m", writer.settings!!.newStepsText())
        assertEquals("", writer.settings!!.reviewStepsText())
        assertTrue(writer.settings!!.reviewStepsMinutes.isEmpty())
    }

    @Test
    fun studyLadderActionsWriteMovedRestoredAndProvidedSettings() {
        val writer = RecordingStudyLadderWriter()
        val current = RecordsBase.StudyLadderSettings.defaults()

        writer.saveStudyLadderSettings(current.moveRung(RecordsBase.LadderRung.WORD_READING, -6))
        assertEquals(current.moveRung(RecordsBase.LadderRung.WORD_READING, -6).orderText(), writer.settings!!.orderText())

        writer.saveStudyLadderSettings(RecordsBase.StudyLadderSettings.defaults())
        assertEquals(RecordsBase.StudyLadderSettings.defaults().orderText(), writer.settings!!.orderText())

        val disabled = current.withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, false)
        writer.saveStudyLadderSettings(disabled)
        assertEquals(disabled.enabledText(), writer.settings!!.enabledText())
    }

    @Test
    fun toggleStudyLadderUsesTheProvidedSnapshot() {
        val current = RecordsBase.StudyLadderSettings.defaults()

        val next = requireNotNull(SettingsWriteActions.toggleStudyLadder(current, RecordsBase.LadderRung.WORD_READING))

        assertFalse(next.isEnabled(RecordsBase.LadderRung.WORD_READING))
        assertEquals(
            current.withRungEnabled(RecordsBase.LadderRung.WORD_READING, false).orderText(),
            next.orderText(),
        )
    }

    @Test
    fun toggleStudyLadderReturnsNullWhenTheSnapshotWouldKeepTheLastAlwaysAvailableRung() {
        val current = RecordsBase.StudyLadderSettings(
            listOf(RecordsBase.LadderRung.KANJI_MEANING),
            listOf(RecordsBase.LadderRung.KANJI_MEANING),
        )

        assertNull(SettingsWriteActions.toggleStudyLadder(current, RecordsBase.LadderRung.KANJI_MEANING))
    }

    @Test
    fun moveStudyLadderUsesTheProvidedSnapshot() {
        val current = RecordsBase.StudyLadderSettings.defaults()

        val next = SettingsWriteActions.moveStudyLadder(current, RecordsBase.LadderRung.WORD_READING, -6)

        assertEquals(RecordsBase.LadderRung.WORD_READING, next.orderedRungs[0])
        assertEquals(current.moveRung(RecordsBase.LadderRung.WORD_READING, -6).orderText(), next.orderText())
    }

    @Test
    fun benchmarksSnapshotBasedStudyLadderToggleAgainstLegacyProviderPath() {
        val current = RecordsBase.StudyLadderSettings.defaults()
        val rung = RecordsBase.LadderRung.WORD_READING
        val iterations = 2_000_000
        var supplierCalls = 0
        val tmpDir = requireNotNull(System.getProperty("java.io.tmpdir"))
        val benchmarkPath = Path(tmpDir, "study-ladder-toggle-benchmark.txt")

        val legacyProvider = {
            supplierCalls++
            current
        }

        var legacyChecksum = 0
        val legacyNanos = measureNanoTime {
            repeat(iterations) {
                val next = requireNotNull(SettingsWriteActions.toggleStudyLadder(legacyProvider(), rung))
                legacyChecksum += next.orderedRungs.size
            }
        }

        var snapshotChecksum = 0
        val snapshotNanos = measureNanoTime {
            repeat(iterations) {
                val next = requireNotNull(SettingsWriteActions.toggleStudyLadder(current, rung))
                snapshotChecksum += next.orderedRungs.size
            }
        }

        assertEquals(iterations, supplierCalls)
        assertEquals(legacyChecksum, snapshotChecksum)
        benchmarkPath.writeText(
            String.format(
                Locale.ROOT,
                "study-ladder-toggle legacy_ms=%.3f legacy_avg_ns=%.1f snapshot_ms=%.3f snapshot_avg_ns=%.1f",
                legacyNanos / 1_000_000.0,
                legacyNanos / iterations.toDouble(),
                snapshotNanos / 1_000_000.0,
                snapshotNanos / iterations.toDouble(),
            ),
        )
    }

    @Test
    fun saveWorkloadWritesAllAdaptiveLoadFields() {
        val writer = RecordingWorkloadWriter()

        SettingsWriteActions.saveWorkload(WorkloadSettingsPolicy.saveManualWorkload(84, 6), writer)

        assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, writer.mode)
        assertEquals(85, writer.workloadPercent)
        assertEquals(6, writer.maxItems)
    }

    @Test
    fun saveWorkloadPreservesPartialWriteRequests() {
        val maxOnly = RecordingWorkloadWriter()
        val manualOnly = RecordingWorkloadWriter()
        val autoOnly = RecordingWorkloadWriter()

        SettingsWriteActions.saveWorkload(WorkloadSettingsPolicy.saveMaximum(99), maxOnly)
        SettingsWriteActions.saveWorkload(WorkloadSettingsPolicy.enableManualMode(), manualOnly)
        SettingsWriteActions.saveWorkload(WorkloadSettingsPolicy.enableAutomaticMode(), autoOnly)

        assertEquals(0, maxOnly.modeWrites)
        assertEquals(0, maxOnly.workloadWrites)
        assertEquals(1, maxOnly.maxWrites)
        assertEquals(AdaptiveLoadPlanner.MAX_MAX_ITEMS, maxOnly.maxItems)
        assertEquals(1, manualOnly.modeWrites)
        assertEquals(0, manualOnly.workloadWrites)
        assertEquals(0, manualOnly.maxWrites)
        assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, manualOnly.mode)
        assertEquals(1, autoOnly.modeWrites)
        assertEquals(0, autoOnly.workloadWrites)
        assertEquals(0, autoOnly.maxWrites)
        assertEquals(AdaptiveLoadPlanner.MODE_AUTO, autoOnly.mode)
    }

    @Test
    fun setAutoSyncEnabledWritesToggleResultFlag() {
        val writer = RecordingAutoSyncWriter()

        writer.setAutoSyncEnabled(AutoSyncSettingsTogglePolicy.enable().enabled)
        assertTrue(writer.enabled)

        writer.setAutoSyncEnabled(AutoSyncSettingsTogglePolicy.disable().enabled)
        assertEquals(false, writer.enabled)
    }

    @Test
    fun setAutoUpdateEnabledWritesToggleResultFlag() {
        val writer = RecordingAutoUpdateWriter()

        writer.saveAutoUpdateEnabled(AutoUpdateSettingsTogglePolicy.toggle(false).enabled())
        assertTrue(writer.enabled)

        writer.saveAutoUpdateEnabled(AutoUpdateSettingsTogglePolicy.toggle(true).enabled())
        assertEquals(false, writer.enabled)
    }

    @Test
    fun saveImportFiltersWritesEveryImportSetting() {
        val writer = RecordingSettingsWriter()

        SettingsWriteActions.saveImportFilters(
            SettingsWriteActions.ImportFilterWriteRequest(
                true,
                false,
                true,
                "kani leech",
                true,
                8.5,
                4,
                2,
                true,
                "deck:Mining"
            ),
            writer
        )

        assertEquals(10, writer.settings.size)
        assertEquals(1, writer.settings[SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY])
        assertEquals(0, writer.settings[SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY])
        assertEquals(1, writer.settings[SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY])
        assertEquals("kani leech", writer.settings[SyncSettings.IMPORT_TAGS_SETTING_KEY])
        assertEquals(1, writer.settings[SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY])
        assertEquals(8.5, writer.settings[SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY] as Double, 0.0)
        assertEquals(4, writer.settings[SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY])
        assertEquals(2, writer.settings[SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY])
        assertEquals(1, writer.settings[SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY])
        assertEquals("deck:Mining", writer.settings[SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY])
    }

    @Test
    fun saveImportFiltersWritesSelectedValuesWithPresetEncoding() {
        val writer = RecordingSettingsWriter()

        SettingsWriteActions.saveImportFilters(
            SettingsWriteActions.ImportFilterWriteRequest(
                false,
                true,
                true,
                "kani leech",
                false,
                6.5,
                5,
                3,
                true,
                "rated:30:1"
            ),
            writer
        )

        assertEquals(10, writer.settings.size)
        assertEquals(0, writer.settings[SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY])
        assertEquals(1, writer.settings[SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY])
        assertEquals(1, writer.settings[SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY])
        assertEquals("kani leech", writer.settings[SyncSettings.IMPORT_TAGS_SETTING_KEY])
        assertEquals(0, writer.settings[SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY])
        assertEquals(6.5, writer.settings[SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY] as Double, 0.0)
        assertEquals(5, writer.settings[SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY])
        assertEquals(3, writer.settings[SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY])
        assertEquals(1, writer.settings[SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY])
        assertEquals("rated:30:1", writer.settings[SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY])
    }

    @Test
    fun importFilterWriteRequestKeepsJavaRecordSemanticsAndNullComponents() {
        val request = SettingsWriteActions.ImportFilterWriteRequest(
            true,
            true,
            false,
            null,
            false,
            6.5,
            1,
            2,
            false,
            null
        )

        assertTrue(SettingsWriteActions.ImportFilterWriteRequest::class.java.isRecord)
        assertNull(request.tags)
        assertNull(request.browserQuery)
        assertEquals(
            request,
            SettingsWriteActions.ImportFilterWriteRequest(true, true, false, null, false, 6.5, 1, 2, false, null)
        )
    }

    private class RecordingSettingsWriter : SettingsWriteActions.SettingWriter {
        val settings = LinkedHashMap<String, Any>()

        override fun putIntSetting(key: String, value: Int) {
            settings[key] = value
        }

        override fun putStringSetting(key: String, value: String?) {
            settings[key] = value.orEmpty()
        }

        override fun putDoubleSetting(key: String, value: Double) {
            settings[key] = value
        }
    }

    private class RecordingLearningStepWriter : SettingsWriteActions.LearningStepSettingsWriter {
        var settings: RecordsSchedulerModels.LearningStepSettings? = null

        override fun saveLearningStepSettings(settings: RecordsSchedulerModels.LearningStepSettings) {
            this.settings = settings
        }
    }

    private class RecordingStudyLadderWriter {
        var settings: RecordsBase.StudyLadderSettings? = null

        fun saveStudyLadderSettings(settings: RecordsBase.StudyLadderSettings) {
            this.settings = settings
        }
    }

    private class RecordingWorkloadWriter : SettingsWriteActions.WorkloadSettingsWriter {
        var mode: String? = null
        var workloadPercent = 0
        var maxItems = 0
        var modeWrites = 0
        var workloadWrites = 0
        var maxWrites = 0

        override fun saveAdaptiveLoadMode(mode: String) {
            this.mode = mode
            modeWrites++
        }

        override fun saveAdaptiveLoadWorkPercent(workloadPercent: Int) {
            this.workloadPercent = workloadPercent
            workloadWrites++
        }

        override fun saveAdaptiveLoadMaxItems(maxItems: Int) {
            this.maxItems = maxItems
            maxWrites++
        }
    }

    private class RecordingAutoSyncWriter {
        var enabled = false

        fun setAutoSyncEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }

    private class RecordingAutoUpdateWriter {
        var enabled = false

        fun saveAutoUpdateEnabled(enabled: Boolean) {
            this.enabled = enabled
        }
    }
}
