package dev.bee.kanjianki.data.conformance

import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.FsrsPersonalization
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsInputRules
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.CommitFsrsFitCommand
import dev.bee.kanjianki.data.HomeRepository
import dev.bee.kanjianki.data.SaveMnemonicCommand
import dev.bee.kanjianki.data.SetLocalSuspensionCommand
import dev.bee.kanjianki.data.SettingsRepository
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.data.StoreResult
import dev.bee.kanjianki.data.StudyRepository
import dev.bee.kanjianki.data.SyncRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * One implementation under test, plus the raw settings seam the suite needs to
 * plant malformed values that no typed command can express.
 *
 * The host owns the database lifecycle: [reset] must leave a freshly created,
 * empty database and drop any cached projection or settings snapshot, so each
 * suite case starts from identical state on both implementations.
 */
interface RepositoryConformanceHost {
    val home: HomeRepository

    val settings: SettingsRepository

    val study: StudyRepository

    val sync: SyncRepository

    /** Discards all data and any in-memory projection/settings caching. */
    suspend fun reset()

    /** Writes a raw `settings` row, bypassing the typed command surface. */
    suspend fun putRawSetting(key: String, value: String)

    /** Reads a raw `settings` row, or null when absent. */
    suspend fun rawSetting(key: String): String?

    /** Reads `stats_cache_state.stats_source_version`. */
    suspend fun statsSourceVersion(): Long

    /** Seeds the dashboard/inventory/example fixture the read cases assert on. */
    suspend fun seedFixture(fixture: RepositoryConformanceFixture)
}

/**
 * The shared seed. Kept as data rather than SQL so each host writes it through
 * its own persistence layer; that is the point of the comparison.
 */
data class RepositoryConformanceFixture(
    val kanji: List<Entry>,
    val similarPairs: List<SimilarPair>,
    val syncFinishedAtMillis: Long,
) {
    data class Entry(
        val kanji: String,
        val primaryMeaning: String,
        val reading: String,
        val weaknessScore: Int,
        val matureSupportCount: Int,
        val activeExampleCount: Int,
        val suspendedExampleCount: Int,
        val examples: List<Example>,
    )

    data class Example(
        val expression: String,
        val reading: String,
        val meaning: String,
        val sentence: String,
        val sourceType: String,
        val mature: Boolean,
        val suspended: Boolean,
    )

    data class SimilarPair(
        val kanjiA: String,
        val kanjiB: String,
        val source: String,
    )

    companion object {
        // The suite carries its own copies of these source labels because the
        // real constants (KanjiAnalyzer.SOURCE_*) are private to `:core`.
        const val SOURCE_ACTIVE = "active"
        const val SOURCE_SUSPENDED = "suspended"

        /**
         * Three kanji with deliberately distinct weakness scores so dashboard
         * ordering is observable, and one shared similar pair so the
         * only-similar inventory filter has both a match and a non-match.
         */
        fun canonical(): RepositoryConformanceFixture =
            RepositoryConformanceFixture(
                kanji = listOf(
                    Entry(
                        kanji = "裂",
                        primaryMeaning = "split",
                        reading = "れつ",
                        weaknessScore = 90,
                        matureSupportCount = 0,
                        // Both examples share source_type so `source_type DESC,
                        // id ASC` orders them by insertion: 分裂 then 破裂.
                        activeExampleCount = 2,
                        suspendedExampleCount = 0,
                        examples = listOf(
                            Example("分裂", "ぶんれつ", "division", "細胞が分裂する。", SOURCE_ACTIVE, false, false),
                            Example("破裂", "はれつ", "rupture", "水道管が破裂した。", SOURCE_ACTIVE, true, false),
                        ),
                    ),
                    Entry(
                        kanji = "脱",
                        primaryMeaning = "escape",
                        reading = "だつ",
                        weaknessScore = 55,
                        matureSupportCount = 1,
                        activeExampleCount = 1,
                        suspendedExampleCount = 0,
                        examples = listOf(
                            Example("脱出", "だっしゅつ", "escape", "無事に脱出した。", SOURCE_ACTIVE, true, false),
                        ),
                    ),
                    Entry(
                        kanji = "痛",
                        primaryMeaning = "pain",
                        reading = "つう",
                        weaknessScore = 55,
                        matureSupportCount = 2,
                        activeExampleCount = 0,
                        suspendedExampleCount = 1,
                        examples = listOf(
                            Example("頭痛", "ずつう", "headache", "頭痛がひどい。", SOURCE_SUSPENDED, true, true),
                        ),
                    ),
                ),
                similarPairs = listOf(SimilarPair("裂", "烈", "visual")),
                syncFinishedAtMillis = FIXED_SYNC_FINISHED_AT,
            )

        const val FIXED_SYNC_FINISHED_AT: Long = 1_770_000_000_000L
    }
}

/**
 * The Goal 180 cross-implementation contract: the legacy Android `LocalStore`
 * repositories and the shared `:data-sql` repositories must be
 * indistinguishable through [HomeRepository] and [SettingsRepository].
 *
 * Modelled on `SqlDriverContractSuite`: one [runAll] entry point invoked by a
 * thin per-implementation test, asserting concrete expected values rather than
 * comparing the two implementations to each other. Comparing them directly
 * would let a shared misunderstanding pass; pinning literals means a drift in
 * either implementation fails on its own.
 */
class RepositoryConformanceSuite(
    private val host: RepositoryConformanceHost,
) {
    suspend fun runAll() {
        emptyStoreReturnsStableDefaultSnapshots()
        settingsCommandsRoundTripEveryTypedFamily()
        malformedStoredSettingsFallOpenToDefaults()
        outOfRangeWorkloadValuesAreClampedOnTheWayIn()
        seededDashboardHomeSnapshotIsOrderedAndComplete()
        inventorySearchHonoursTermsScopeAndSimilarFilter()
        kanjiDetailReportsMnemonicRowAndInventory()
        mnemonicWritesNormalizeTrimAndDeleteOnBlank()
        localSuspensionHidesRowsAndMarksStatsDirty()
        downgradeNoticeIsConsumedExactlyOnce()
        newCardSortPreviewVersionAdvancesOnlyAfterInvalidation()
        fsrsFitCommitsRespectPreserveAndDisableRules()
    }

    private suspend fun emptyStoreReturnsStableDefaultSnapshots() {
        host.reset()

        val snapshot = host.settings.load().expect("load settings on empty store")
        assertDefaultSettings(snapshot)

        val home = host.home.loadHome(NOW).expect("loadHome on empty store")
        assertTrue("empty store must have no dashboard rows", home.activeRows.isEmpty())
        assertTrue("empty store must have no study items", home.studyItems.isEmpty())
        assertTrue(home.locallySuspendedKanji.isEmpty())
        assertNull("no sync has run yet", home.latestSync)
        assertNull(home.latestSuccessfulSyncAtMillis)
        assertEquals(0, home.studyStreak.currentDays)
        assertTrue(home.dueLegacyWritingRepairs.isEmpty())
        assertTrue(home.repairedHandoffKanji.isEmpty())
        assertEquals(0, home.consecutiveFailedSyncs)

        val detail = host.home.loadKanjiDetail("裂", NOW).expect("detail on empty store")
        assertEquals("裂", detail.kanji)
        assertNull(detail.dashboardRow)
        assertNull(detail.inventoryItem)
        assertEquals("", detail.mnemonic)
        assertFalse(detail.locallySuspended)
        assertTrue(detail.similarPairs.isEmpty())
        assertTrue(detail.inventory.isEmpty())

        val games = host.home.loadGameData().expect("game data on empty store")
        assertTrue(games.activeRows.isEmpty())
        assertTrue(games.inventory.isEmpty())
        assertTrue(games.similarPairs.isEmpty())

        assertTrue(
            host.home.searchInventory("", onlySimilarKanji = false)
                .expect("inventory search on empty store").isEmpty(),
        )
        assertNull(
            "no downgrade has been recorded",
            host.home.consumeDowngradeNotice().expect("downgrade notice on empty store"),
        )
    }

    private suspend fun settingsCommandsRoundTripEveryTypedFamily() {
        host.reset()

        save(
            SettingsSaveCommand.AdaptiveWorkload(
                AdaptiveWorkloadSnapshot(workPercent = 65, maxItems = 17, mode = "manual"),
            ),
        )
        save(SettingsSaveCommand.StudyAhead(minutes = 45))
        save(SettingsSaveCommand.Theme(KaniThemeChoice.entries.last()))
        save(SettingsSaveCommand.NewCardSort(mode = "frequency"))
        save(SettingsSaveCommand.FrequencyRange(minRank = 300, maxRank = 4_000))
        save(SettingsSaveCommand.DeckLimits(newPerDay = 12, activeQueueCap = 64))
        save(
            SettingsSaveCommand.LadderThresholds(
                promotionIntervalDays = 28,
                demotionFailStreak = 4,
            ),
        )
        save(
            SettingsSaveCommand.NoteTypeFields(
                modelName = "Alternate",
                expressionField = "Word",
                readingField = "Kana",
                meaningField = "Gloss",
                sentenceField = "Context",
                frequencyField = "Freq",
                frequencySortField = "FreqRank",
            ),
        )
        save(
            SettingsSaveCommand.SchedulerParameters(
                RecordsSchedulerModels.SchedulerParameters(0.85),
            ),
        )
        save(SettingsSaveCommand.LearningSteps(learningSteps(listOf(2, 15), listOf(25))))
        save(SettingsSaveCommand.SchedulerFsrsWeights(CUSTOM_WEIGHTS))
        save(SettingsSaveCommand.FsrsPersonalizationEnabled(enabled = true))
        save(SettingsSaveCommand.FsrsFitSummary(summaryJson = FIT_SUMMARY_JSON))

        val stored = host.settings.load().expect("load settings after round trip")
        assertEquals(65, stored.adaptiveWorkload.workPercent)
        assertEquals(17, stored.adaptiveWorkload.maxItems)
        assertEquals("manual", stored.adaptiveWorkload.mode)
        assertEquals(45, stored.studyAheadMinutes)
        assertEquals(KaniThemeChoice.entries.last(), stored.themeChoice)
        assertEquals("frequency", stored.sync.newCardSortMode)
        assertEquals(300, stored.sync.suspendedRankMin)
        assertEquals(4_000, stored.sync.suspendedRankMax)
        assertEquals(12, stored.sync.newPerDay)
        assertEquals(64, stored.sync.activeQueueCap)
        assertEquals(28, stored.sync.ladderPromotionIntervalDays)
        assertEquals(4, stored.sync.ladderDemotionFailStreak)
        // NoteTypeFields writes the note-type (model) name; it has no template
        // field, so the template stays whatever the previous state held.
        assertEquals("Alternate", stored.sync.modelName)
        assertEquals("Word", stored.sync.expressionField)
        assertEquals("Kana", stored.sync.readingField)
        assertEquals("Gloss", stored.sync.meaningField)
        assertEquals("Context", stored.sync.sentenceField)
        assertEquals("Freq", stored.sync.frequencyField)
        assertEquals("FreqRank", stored.sync.frequencySortField)
        assertEquals(0.85, stored.schedulerParameters.targetRetention, 1.0e-9)
        assertEquals(listOf(2, 15), stored.learningSteps.newStepsMinutes)
        assertEquals(listOf(25), stored.learningSteps.reviewStepsMinutes)
        assertEquals(CUSTOM_WEIGHTS.size, stored.schedulerFsrsWeights?.size)
        assertWeightsEqual(CUSTOM_WEIGHTS, stored.schedulerFsrsWeights)
        assertTrue(stored.fsrsPersonalizationEnabled)
        assertEquals(FIT_SUMMARY_JSON, stored.fsrsFitSummaryJson)

        // Clearing the vector must remove personalization, not fall back to a
        // stale stored value.
        save(SettingsSaveCommand.SchedulerFsrsWeights(weights = null))
        assertNull(
            "cleared weights must read back as absent",
            host.settings.load().expect("load after clearing weights").schedulerFsrsWeights,
        )

        // ResetFsrsPersonalization clears the vector and summary but does not
        // touch the enabled flag, which we set to true above.
        save(SettingsSaveCommand.ResetFsrsPersonalization)
        val reset = host.settings.load().expect("load after personalization reset")
        assertTrue(reset.fsrsPersonalizationEnabled)
        assertNull(reset.schedulerFsrsWeights)

        // Ladder settings survive a full order + enablement round trip. Both
        // sides normalize the stored order, so we compare normalized to
        // normalized rather than to the literal input order.
        val ladder = RecordsBase.StudyLadderSettings.fromStored(
            "word_reading,kanji_meaning,write_kanji",
            "word_reading,kanji_meaning",
            null,
            null,
        )
        save(SettingsSaveCommand.StudyLadder(ladder))
        val storedLadder = host.settings.load().expect("load after ladder save").studyLadder
        assertEquals(ladder.orderedRungs, storedLadder.orderedRungs)
        assertEquals(ladder.enabledRungs, storedLadder.enabledRungs)

        save(
            SettingsSaveCommand.Sync(
                settings = syncWithMaturity(matureDays = 35, matureSupportThreshold = 4),
                tagRepairedCards = true,
            ),
        )
        val syncStored = host.settings.load().expect("load after sync save")
        assertEquals(35, syncStored.sync.matureDays)
        assertEquals(4, syncStored.sync.matureSupportThreshold)
        // The Sync command does carry the template field.
        assertEquals("Alternate", syncStored.sync.templateName)
        assertTrue(syncStored.tagRepairedCards)

        save(
            SettingsSaveCommand.ImportFilters(
                activeCards = false,
                suspendedCards = true,
                taggedCards = true,
                tags = "leech kani",
                weakCards = false,
                weakDifficulty = 8.25,
                weakLapses = 5,
                minMatchingCards = 3,
                browserQueryCards = true,
                browserQuery = "deck:Mining is:review",
                tagRepairedCards = false,
            ),
        )
        val filters = host.settings.load().expect("load after import filters").sync
        assertFalse(filters.importActiveCards)
        assertTrue(filters.importSuspendedCards)
        assertTrue(filters.importTaggedCards)
        assertFalse(filters.importWeakCards)
        assertEquals(8.25, filters.importWeakFsrsDifficultyThreshold, 1.0e-9)
        assertEquals(5, filters.importWeakLapsesThreshold)
        assertEquals(3, filters.importMinMatchingCardsPerKanji)
        assertTrue(filters.importBrowserQueryCards)
        assertEquals("deck:Mining is:review", filters.importBrowserQuery)
        assertFalse(
            "tagRepairedCards travels with import filters",
            host.settings.load().expect("load after import filters").tagRepairedCards,
        )
    }

    private suspend fun malformedStoredSettingsFallOpenToDefaults() {
        host.reset()
        val defaults = RecordsSyncModels.Settings.kikuDefaults()

        // Every one of these is unparseable for its declared type. A settings
        // read must never throw: a corrupt row degrades to the default.
        host.putRawSetting(MATURE_DAYS_KEY, "not-a-number")
        host.putRawSetting(MATURE_SUPPORT_THRESHOLD_KEY, "")
        host.putRawSetting(TARGET_RETENTION_KEY, "nine tenths")
        host.putRawSetting(STUDY_AHEAD_MINUTES_KEY, "soon")
        host.putRawSetting(ADAPTIVE_WORKLOAD_KEY, "lots")
        host.putRawSetting(ADAPTIVE_LOAD_MAX_ITEMS_KEY, "∞")
        host.putRawSetting(NEW_LEARNING_STEPS_KEY, "abc,def")
        host.putRawSetting(FSRS_WEIGHTS_KEY, "0.4,not-a-weight,0.9")
        host.putRawSetting(THEME_KEY, "no-such-theme")

        val snapshot = host.settings.load().expect("load settings with malformed rows")
        assertEquals(defaults.matureDays, snapshot.sync.matureDays)
        assertEquals(defaults.matureSupportThreshold, snapshot.sync.matureSupportThreshold)
        assertEquals(
            RecordsSchedulerModels.SchedulerParameters.defaults().targetRetention,
            snapshot.schedulerParameters.targetRetention,
            1.0e-9,
        )
        assertEquals(
            SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES,
            snapshot.studyAheadMinutes,
        )
        assertEquals(
            AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT,
            snapshot.adaptiveWorkload.workPercent,
        )
        assertEquals(AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS, snapshot.adaptiveWorkload.maxItems)
        assertEquals(
            RecordsSchedulerModels.LearningStepSettings.defaults().newStepsMinutes,
            snapshot.learningSteps.newStepsMinutes,
        )
        assertNull(
            "a malformed weight vector must not become a partial vector",
            snapshot.schedulerFsrsWeights,
        )
        assertEquals(
            "an unknown theme key falls back to the default choice",
            KaniThemeChoice.fromStorageKey(null),
            snapshot.themeChoice,
        )

        // A malformed read is non-destructive for values with no repair rule:
        // reading must not silently rewrite the user's row into a default.
        assertEquals("no-such-theme", host.rawSetting(THEME_KEY))
    }

    private suspend fun outOfRangeWorkloadValuesAreClampedOnTheWayIn() {
        host.reset()
        val defaults = RecordsSyncModels.Settings.kikuDefaults()

        // Non-positive maturity values are refused at the write boundary, so the
        // stored row keeps the default rather than an unusable zero/negative.
        save(
            SettingsSaveCommand.Sync(
                settings = syncWithMaturity(matureDays = 0, matureSupportThreshold = -1),
                tagRepairedCards = false,
            ),
        )
        val stored = host.settings.load().expect("load after non-positive maturity save")
        assertEquals(defaults.matureDays, stored.sync.matureDays)
        assertEquals(defaults.matureSupportThreshold, stored.sync.matureSupportThreshold)
        assertEquals(defaults.matureDays.toString(), host.rawSetting(MATURE_DAYS_KEY))
        assertEquals(
            defaults.matureSupportThreshold.toString(),
            host.rawSetting(MATURE_SUPPORT_THRESHOLD_KEY),
        )

        // Out-of-range workload/queue values are snapped on the write path, so
        // both the read and the stored raw row hold the clamped value.
        save(
            SettingsSaveCommand.AdaptiveWorkload(
                AdaptiveWorkloadSnapshot(workPercent = 5_000, maxItems = -4, mode = "manual"),
            ),
        )
        val repaired = host.settings.load().expect("load after out-of-range workload")
        assertEquals(
            AdaptiveLoadPlanner.snapWorkloadPercent(5_000),
            repaired.adaptiveWorkload.workPercent,
        )
        assertEquals(
            AdaptiveLoadPlanner.normalizeMaxItems(-4),
            repaired.adaptiveWorkload.maxItems,
        )
        assertEquals(
            "the clamped workload percent is persisted, not just returned",
            repaired.adaptiveWorkload.workPercent.toString(),
            host.rawSetting(ADAPTIVE_WORKLOAD_KEY),
        )
        assertEquals(
            repaired.adaptiveWorkload.maxItems.toString(),
            host.rawSetting(ADAPTIVE_LOAD_MAX_ITEMS_KEY),
        )

        // A second read is idempotent: reading again neither changes the value
        // nor rewrites it differently.
        val again = host.settings.load().expect("second load after clamp")
        assertEquals(repaired.adaptiveWorkload.workPercent, again.adaptiveWorkload.workPercent)
        assertEquals(repaired.adaptiveWorkload.maxItems, again.adaptiveWorkload.maxItems)
    }

    private suspend fun seededDashboardHomeSnapshotIsOrderedAndComplete() {
        host.reset()
        val fixture = RepositoryConformanceFixture.canonical()
        host.seedFixture(fixture)

        val home = host.home.loadHome(NOW).expect("loadHome on seeded store")

        // weakness_score DESC, then suspended_example_count DESC, then kanji ASC.
        // 脱 and 痛 tie on score; 痛's suspended example breaks the tie ahead of 脱.
        assertEquals(listOf("裂", "痛", "脱"), home.activeRows.map { it.kanji })

        val split = home.activeRows.first()
        assertEquals("split", split.primaryMeaning)
        assertEquals("れつ", split.reading)
        assertEquals(90, split.weaknessScore)
        assertEquals(0, split.matureSupportCount)
        assertEquals(
            "examples are attached to their row",
            listOf("分裂", "破裂"),
            split.examples.map { it.expression },
        )
        assertEquals(listOf("ぶんれつ", "はれつ"), split.examples.map { it.reading })
        assertEquals(listOf(false, true), split.examples.map { it.mature })

        assertEquals(
            RepositoryConformanceFixture.FIXED_SYNC_FINISHED_AT,
            home.latestSuccessfulSyncAtMillis,
        )
        assertNotNull("a completed sync must be reported", home.latestSync)
        assertEquals(0, home.consecutiveFailedSyncs)

        val games = host.home.loadGameData().expect("game data on seeded store")
        assertEquals(home.activeRows.map { it.kanji }, games.activeRows.map { it.kanji })
        assertEquals(listOf("裂", "痛", "脱").sorted(), games.inventory.map { it.kanji }.sorted())
        assertEquals(
            listOf("裂" to "烈"),
            games.similarPairs.map { it.kanjiA to it.kanjiB },
        )

        val preview = host.home.loadNewCardSortPreviewData().expect("sort preview on seeded store")
        assertEquals(home.activeRows.map { it.kanji }, preview.activeRows.map { it.kanji })
        assertEquals(games.similarPairs.size, preview.similarPairs.size)
    }

    private suspend fun inventorySearchHonoursTermsScopeAndSimilarFilter() {
        host.reset()
        host.seedFixture(RepositoryConformanceFixture.canonical())

        assertEquals(
            "a blank query returns the whole inventory in kanji order",
            listOf("痛", "脱", "裂").sorted(),
            host.home.searchInventory("", onlySimilarKanji = false)
                .expect("blank inventory search").map { it.kanji }.sorted(),
        )

        assertEquals(
            "a literal query matches its own kanji",
            listOf("裂"),
            host.home.searchInventory("裂", onlySimilarKanji = false)
                .expect("literal inventory search").map { it.kanji },
        )

        assertEquals(
            "a meaning term matches through search_text",
            listOf("痛"),
            host.home.searchInventory("pain", onlySimilarKanji = false)
                .expect("meaning inventory search").map { it.kanji },
        )

        assertTrue(
            "an unmatched term returns nothing rather than everything",
            host.home.searchInventory("zzzznomatch", onlySimilarKanji = false)
                .expect("unmatched inventory search").isEmpty(),
        )

        assertEquals(
            "only-similar keeps just the kanji with a confusion pair",
            listOf("裂"),
            host.home.searchInventory("", onlySimilarKanji = true)
                .expect("similar-only inventory search").map { it.kanji },
        )

        // LIKE wildcards in user input must be escaped, not interpreted.
        assertTrue(
            "a bare % must not match every row",
            host.home.searchInventory("%", onlySimilarKanji = false)
                .expect("wildcard inventory search").isEmpty(),
        )
        assertTrue(
            host.home.searchInventory("_", onlySimilarKanji = false)
                .expect("underscore inventory search").isEmpty(),
        )

        // The study-queue scopes are empty until items are admitted, and the
        // include-suspended scope additionally surfaces locally suspended kanji.
        assertTrue(
            host.home.searchStudyInventory("", onlySimilarKanji = false, includeLocallySuspended = false)
                .expect("study-queue inventory search").isEmpty(),
        )
        suspendLocally("痛")
        assertEquals(
            listOf("痛"),
            host.home.searchStudyInventory("", onlySimilarKanji = false, includeLocallySuspended = true)
                .expect("study-queue inventory search with suspended").map { it.kanji },
        )
        assertTrue(
            "the default scope still excludes locally suspended kanji",
            host.home.searchStudyInventory("", onlySimilarKanji = false, includeLocallySuspended = false)
                .expect("study-queue inventory search after suspension").isEmpty(),
        )
    }

    private suspend fun kanjiDetailReportsMnemonicRowAndInventory() {
        host.reset()
        host.seedFixture(RepositoryConformanceFixture.canonical())
        assertTrue(host.home.saveMnemonic(SaveMnemonicCommand("裂", "cloth tearing", NOW)).isOk())

        val detail = host.home.loadKanjiDetail("裂", NOW).expect("detail on seeded store")
        assertEquals("裂", detail.kanji)
        assertEquals("cloth tearing", detail.mnemonic)
        assertEquals("split", detail.dashboardRow?.primaryMeaning)
        assertEquals(90, detail.dashboardRow?.weaknessScore)
        assertEquals(listOf("分裂", "破裂"), detail.dashboardRow?.examples?.map { it.expression })
        assertEquals("裂", detail.inventoryItem?.kanji)
        assertFalse(detail.locallySuspended)
        assertEquals(
            "detail reports the pairs for its own kanji",
            listOf("裂" to "烈"),
            detail.similarPairs.map { it.kanjiA to it.kanjiB },
        )
        assertEquals(
            "detail carries the full inventory for choice building",
            3,
            detail.inventory.size,
        )
        assertTrue("no picks have been recorded", detail.wrongPickCounts.isEmpty())

        val absent = host.home.loadKanjiDetail("鬱", NOW).expect("detail for an unseeded kanji")
        assertNull(absent.dashboardRow)
        assertNull(absent.inventoryItem)
        assertEquals("", absent.mnemonic)
        assertEquals(
            "an unseeded kanji still sees the inventory",
            3,
            absent.inventory.size,
        )
    }

    private suspend fun mnemonicWritesNormalizeTrimAndDeleteOnBlank() {
        host.reset()

        assertTrue(host.home.saveMnemonic(SaveMnemonicCommand("裂", "  padded note  ", NOW)).isOk())
        assertEquals(
            "surrounding whitespace is trimmed before storage",
            "padded note",
            host.home.loadKanjiDetail("裂", NOW).expect("detail after padded save").mnemonic,
        )

        assertTrue(host.home.saveMnemonic(SaveMnemonicCommand("裂", "second note", NOW + 1)).isOk())
        assertEquals(
            "a second save replaces rather than duplicates",
            "second note",
            host.home.loadKanjiDetail("裂", NOW).expect("detail after rewrite").mnemonic,
        )

        assertTrue(
            "a blank note is accepted and clears the row",
            host.home.saveMnemonic(SaveMnemonicCommand("裂", "   ", NOW + 2)).isOk(),
        )
        assertEquals(
            "",
            host.home.loadKanjiDetail("裂", NOW).expect("detail after blank save").mnemonic,
        )

        assertTrue(
            "an empty kanji key is a no-op, not a failure",
            host.home.saveMnemonic(SaveMnemonicCommand("", "orphan", NOW)).isOk(),
        )
        assertEquals(
            "",
            host.home.loadKanjiDetail("", NOW).expect("detail for a blank kanji").mnemonic,
        )
    }

    private suspend fun localSuspensionHidesRowsAndMarksStatsDirty() {
        host.reset()
        host.seedFixture(RepositoryConformanceFixture.canonical())
        val beforeVersion = host.statsSourceVersion()

        suspendLocally("裂")

        val afterSuspend = host.home.loadHome(NOW).expect("loadHome after suspension")
        assertEquals(
            "a locally suspended kanji leaves the dashboard",
            listOf("痛", "脱"),
            afterSuspend.activeRows.map { it.kanji },
        )
        assertEquals(setOf("裂"), afterSuspend.locallySuspendedKanji)
        assertTrue(
            "suspension invalidates the stats cache in the same commit",
            host.statsSourceVersion() > beforeVersion,
        )
        assertTrue(
            host.home.loadKanjiDetail("裂", NOW).expect("detail while suspended").locallySuspended,
        )

        // Suspending an already-suspended kanji is idempotent for the row set.
        suspendLocally("裂")
        assertEquals(
            listOf("痛", "脱"),
            host.home.loadHome(NOW).expect("loadHome after repeat suspension")
                .activeRows.map { it.kanji },
        )

        // A batch command must apply to every distinct kanji it names.
        assertTrue(
            host.home.setLocalSuspension(
                SetLocalSuspensionCommand(listOf("脱", "痛", "脱"), suspended = true, NOW),
            ).isOk(),
        )
        val allSuspended = host.home.loadHome(NOW).expect("loadHome after batch suspension")
        assertTrue(allSuspended.activeRows.isEmpty())
        assertEquals(setOf("裂", "脱", "痛"), allSuspended.locallySuspendedKanji)

        assertTrue(
            host.home.setLocalSuspension(
                SetLocalSuspensionCommand(listOf("裂", "脱", "痛"), suspended = false, NOW + 1),
            ).isOk(),
        )
        val restored = host.home.loadHome(NOW).expect("loadHome after unsuspension")
        assertEquals(
            "unsuspending restores the original order",
            listOf("裂", "痛", "脱"),
            restored.activeRows.map { it.kanji },
        )
        assertTrue(restored.locallySuspendedKanji.isEmpty())

        val versionBeforeNoOp = host.statsSourceVersion()
        assertTrue(
            "an empty batch is a no-op",
            host.home.setLocalSuspension(
                SetLocalSuspensionCommand(emptyList(), suspended = true, NOW),
            ).isOk(),
        )
        assertEquals(
            "a no-op suspension must not dirty the stats cache",
            versionBeforeNoOp,
            host.statsSourceVersion(),
        )
    }

    private suspend fun downgradeNoticeIsConsumedExactlyOnce() {
        host.reset()
        assertNull(host.home.consumeDowngradeNotice().expect("notice before any downgrade"))

        host.putRawSetting(DOWNGRADED_FROM_VERSION_KEY, "36")
        assertEquals(
            36,
            host.home.consumeDowngradeNotice().expect("first notice read"),
        )
        assertNull(
            "the notice is consumed, so a second read reports nothing",
            host.home.consumeDowngradeNotice().expect("second notice read"),
        )
        assertNull(
            "consuming deletes the row rather than blanking it",
            host.rawSetting(DOWNGRADED_FROM_VERSION_KEY),
        )

        // A non-numeric notice is not a version: it is not reported, and it is
        // left in place (neither host deletes an unparseable row).
        host.putRawSetting(DOWNGRADED_FROM_VERSION_KEY, "vNext")
        assertNull(host.home.consumeDowngradeNotice().expect("malformed notice read"))
        assertEquals("vNext", host.rawSetting(DOWNGRADED_FROM_VERSION_KEY))
    }

    private suspend fun newCardSortPreviewVersionAdvancesOnlyAfterInvalidation() {
        host.reset()
        host.seedFixture(RepositoryConformanceFixture.canonical())

        // Reads never lower the version. The legacy host may lazily bump it from
        // a read path when a process-static cache epoch advanced, so the
        // portable guarantee across both hosts is monotonic non-decrease, and a
        // strict increase only after a real write.
        val initial = host.home.loadNewCardSortPreviewVersion().expect("initial preview version")
        val repeat = host.home.loadNewCardSortPreviewVersion().expect("repeat preview version")
        assertTrue("a read must not lower the version", repeat >= initial)
        assertTrue(
            "the snapshot reports at least the version it was built at",
            host.home.loadNewCardSortPreviewData().expect("preview data").sourceVersion >= repeat,
        )

        val beforeWrite = host.home.loadNewCardSortPreviewVersion().expect("version before write")
        suspendLocally("裂")
        val afterWrite = host.home.loadNewCardSortPreviewVersion().expect("preview version after write")
        assertTrue(
            "a suspension change invalidates the preview projection",
            afterWrite > beforeWrite,
        )
        assertTrue(
            host.home.loadNewCardSortPreviewData().expect("preview data after write").sourceVersion >= afterWrite,
        )
    }

    private suspend fun fsrsFitCommitsRespectPreserveAndDisableRules() {
        host.reset()

        assertTrue(
            "adopting a vector reports that it was adopted",
            host.settings.commitFsrsFit(
                CommitFsrsFitCommand(
                    weightsToAdopt = CUSTOM_WEIGHTS,
                    summaryJson = FIT_SUMMARY_JSON,
                    disabledSummaryJson = null,
                    preserveExistingWeights = false,
                ),
            ).expect("commit adopting a vector"),
        )
        val adopted = host.settings.load().expect("load after adopting a vector")
        assertWeightsEqual(CUSTOM_WEIGHTS, adopted.schedulerFsrsWeights)
        assertEquals(FIT_SUMMARY_JSON, adopted.fsrsFitSummaryJson)

        assertFalse(
            "preserving does not re-adopt, so it does not report an adoption",
            host.settings.commitFsrsFit(
                CommitFsrsFitCommand(
                    weightsToAdopt = null,
                    summaryJson = SECOND_FIT_SUMMARY_JSON,
                    disabledSummaryJson = null,
                    preserveExistingWeights = true,
                ),
            ).expect("commit preserving the existing vector"),
        )
        val preserved = host.settings.load().expect("load after preserving")
        assertWeightsEqual(
            CUSTOM_WEIGHTS,
            preserved.schedulerFsrsWeights,
        )
        assertEquals(
            "the summary still advances when the vector is preserved",
            SECOND_FIT_SUMMARY_JSON,
            preserved.fsrsFitSummaryJson,
        )

        // Not preserving and not adopting clears the live vector, and — because
        // no vector was adopted — the plain summary wins over the disabled one.
        assertFalse(
            "no vector was adopted",
            host.settings.commitFsrsFit(
                CommitFsrsFitCommand(
                    weightsToAdopt = null,
                    summaryJson = SECOND_FIT_SUMMARY_JSON,
                    disabledSummaryJson = DISABLED_FIT_SUMMARY_JSON,
                    preserveExistingWeights = false,
                ),
            ).expect("commit clearing the vector"),
        )
        val cleared = host.settings.load().expect("load after clearing")
        assertNull(
            "not preserving and not adopting clears the live vector",
            cleared.schedulerFsrsWeights,
        )
        assertEquals(SECOND_FIT_SUMMARY_JSON, cleared.fsrsFitSummaryJson)

        // The disabled summary is only chosen when a real vector was fitted but
        // personalization is off: encoded != null && !enabled. Turning
        // personalization off first also clears any stored vector.
        save(SettingsSaveCommand.FsrsPersonalizationEnabled(enabled = false))
        assertFalse(
            "a fit while personalization is off is recorded but not adopted",
            host.settings.commitFsrsFit(
                CommitFsrsFitCommand(
                    weightsToAdopt = CUSTOM_WEIGHTS,
                    summaryJson = SECOND_FIT_SUMMARY_JSON,
                    disabledSummaryJson = DISABLED_FIT_SUMMARY_JSON,
                    preserveExistingWeights = false,
                ),
            ).expect("commit while disabled"),
        )
        val disabled = host.settings.load().expect("load after disabled fit")
        assertNull(
            "a disabled fit does not install a live vector",
            disabled.schedulerFsrsWeights,
        )
        assertEquals(DISABLED_FIT_SUMMARY_JSON, disabled.fsrsFitSummaryJson)
    }

    private suspend fun assertDefaultSettings(snapshot: SettingsSnapshot) {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        val schedulerDefaults = RecordsSchedulerModels.SchedulerParameters.defaults()
        val stepDefaults = RecordsSchedulerModels.LearningStepSettings.defaults()

        assertEquals(defaults.templateName, snapshot.sync.templateName)
        assertEquals(defaults.matureDays, snapshot.sync.matureDays)
        assertEquals(defaults.matureSupportThreshold, snapshot.sync.matureSupportThreshold)
        assertEquals(defaults.newCardSortMode, snapshot.sync.newCardSortMode)
        assertEquals(defaults.activeQueueCap, snapshot.sync.activeQueueCap)
        assertEquals(
            defaults.ladderPromotionIntervalDays,
            snapshot.sync.ladderPromotionIntervalDays,
        )
        assertEquals(defaults.ladderDemotionFailStreak, snapshot.sync.ladderDemotionFailStreak)
        assertFalse("repaired-card tagging is off by default", snapshot.tagRepairedCards)
        assertEquals(
            AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT,
            snapshot.adaptiveWorkload.workPercent,
        )
        assertEquals(AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS, snapshot.adaptiveWorkload.maxItems)
        assertEquals(AdaptiveLoadPlanner.DEFAULT_WORKLOAD_MODE, snapshot.adaptiveWorkload.mode)
        assertEquals(
            SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES,
            snapshot.studyAheadMinutes,
        )
        assertEquals(
            schedulerDefaults.targetRetention,
            snapshot.schedulerParameters.targetRetention,
            1.0e-9,
        )
        assertEquals(stepDefaults.newStepsMinutes, snapshot.learningSteps.newStepsMinutes)
        assertEquals(stepDefaults.reviewStepsMinutes, snapshot.learningSteps.reviewStepsMinutes)
        assertEquals(KaniThemeChoice.fromStorageKey(null), snapshot.themeChoice)
        assertTrue(
            "FSRS personalization is on by default",
            snapshot.fsrsPersonalizationEnabled,
        )
        assertNull(
            "no custom vector exists until one is fitted",
            snapshot.schedulerFsrsWeights,
        )
        assertEquals("", snapshot.fsrsFitSummaryJson)
        assertEquals(
            "the default ladder order is the stored-nothing order",
            RecordsBase.StudyLadderSettings.fromStored("", "", null, null).orderedRungs,
            snapshot.studyLadder.orderedRungs,
        )
    }

    private suspend fun suspendLocally(kanji: String) {
        assertTrue(
            "suspend $kanji",
            host.home.setLocalSuspension(
                SetLocalSuspensionCommand(listOf(kanji), suspended = true, NOW),
            ).isOk(),
        )
    }

    private suspend fun save(command: SettingsSaveCommand) {
        assertTrue("save $command", host.settings.save(command).isOk())
    }

    private fun <T> StoreResult<T>.expect(label: String): T {
        assertTrue("$label must succeed, got $this", isOk())
        // Smart-cast on Ok returns the value directly; no unchecked cast, which
        // matters because library modules compile with allWarningsAsErrors.
        if (this is StoreResult.Ok) {
            return value
        }
        throw AssertionError("$label was not Ok: $this")
    }

    private fun assertWeightsEqual(expected: List<Double>, actual: List<Double>?) {
        assertNotNull("a vector was expected", actual)
        val observed = requireNotNull(actual)
        assertEquals("vector length", expected.size, observed.size)
        expected.forEachIndexed { index, value ->
            assertEquals("weight[$index]", value, observed[index], 1.0e-9)
        }
    }

    private fun learningSteps(
        newSteps: List<Int>,
        reviewSteps: List<Int>,
    ): RecordsSchedulerModels.LearningStepSettings =
        RecordsSchedulerModels.LearningStepSettings(newSteps, reviewSteps)

    private fun syncWithMaturity(
        matureDays: Int,
        matureSupportThreshold: Int,
    ): RecordsSyncModels.Settings {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        // 6 named + 25-element vararg tail. "Alternate" is the template (second
        // positional); model name stays the default so the Sync-command
        // template round trip is observable.
        return RecordsSyncModels.Settings(
            defaults.modelName,
            "Alternate",
            defaults.expressionField,
            defaults.readingField,
            defaults.meaningField,
            defaults.sentenceField,
            defaults.frequencyField,
            defaults.frequencySortField,
            matureDays,
            matureSupportThreshold,
            defaults.suspendedRankMin,
            defaults.suspendedRankMax,
            defaults.activeQueueCap,
            defaults.newPerDay,
            defaults.writingTriggerMissDays,
            defaults.recognitionPromotionPasses,
            defaults.realDueReviewsToMove,
            defaults.importActiveCards,
            defaults.importSuspendedCards,
            defaults.importTaggedCards,
            defaults.importTags,
            defaults.importWeakCards,
            defaults.importWeakFsrsDifficultyThreshold,
            defaults.importWeakLapsesThreshold,
            defaults.importMinMatchingCardsPerKanji,
            defaults.importBrowserQueryCards,
            defaults.importBrowserQuery,
            defaults.newCardSortMode,
            defaults.ladderPromotionIntervalDays,
            defaults.ladderDemotionFailStreak,
            defaults.ladderPromotionMinPasses,
        )
    }

    private companion object {
        const val NOW = 1_770_100_000_000L

        const val MATURE_DAYS_KEY = "mature_days"
        const val MATURE_SUPPORT_THRESHOLD_KEY = "mature_support_threshold"
        const val TARGET_RETENTION_KEY = "scheduler_target_retention"
        const val STUDY_AHEAD_MINUTES_KEY = "study_ahead_minutes"
        const val ADAPTIVE_LOAD_MAX_ITEMS_KEY = "adaptive_load_max_items"
        const val NEW_LEARNING_STEPS_KEY = "new_learning_steps_minutes"
        const val DOWNGRADED_FROM_VERSION_KEY = "downgraded_from_version"

        val ADAPTIVE_WORKLOAD_KEY = AdaptiveLoadPlanner.SETTING_KEY
        val FSRS_WEIGHTS_KEY = FsrsPersonalization.WEIGHTS_SETTING_KEY
        val THEME_KEY = KaniThemeChoice.SETTING_KEY

        const val FIT_SUMMARY_JSON = """{"samples":512,"improvement":0.021}"""
        const val SECOND_FIT_SUMMARY_JSON = """{"samples":740,"improvement":0.004}"""
        const val DISABLED_FIT_SUMMARY_JSON = """{"disabled":true}"""

        /**
         * A full FSRS-7 vector that is not the defaults, so a read that silently
         * fell back to defaults would fail rather than coincidentally match. It
         * is a perturbation of the default vector at indices with strict
         * headroom between their lower and upper clipper bounds, so it validates
         * on both the read and the write path (encodeWeights validates too).
         */
        val CUSTOM_WEIGHTS: List<Double> = FsrsPersonalization.defaultWeights().let { base ->
            val perturbed = base.copyOf()
            intArrayOf(
                7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19,
                21, 22, 23, 24, 31, 32, 33, 34,
            ).forEach { index -> perturbed[index] = perturbed[index] + 0.05 }
            perturbed.toList()
        }
    }
}
