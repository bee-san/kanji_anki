package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiFieldTextNormalizer
import dev.bee.kanjianki.core.AnkiKanjiInventory
import dev.bee.kanjianki.core.AnkiKanjiInventoryCollector
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyQueueSeeder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MissingKanjiStoreTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun freshInstallAndMigrationFromThirtyTwoCreateAllTablesIdempotently() {
        val expected = expectedTables()
        for (table in expected) {
            assertTrue("Fresh schema is missing $table", tableExists(store.writableDatabase, table))
        }

        val db = SQLiteDatabase.create(null)
        try {
            db.execSQL("CREATE TABLE settings (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
            db.execSQL("INSERT INTO settings (key, value) VALUES ('sentinel', 'kept')")

            store.onUpgrade(db, 32, 33)
            store.onUpgrade(db, 32, 33)

            for (table in expected) {
                assertTrue("Migration is missing $table", tableExists(db, table))
            }
            db.rawQuery("SELECT value FROM settings WHERE key='sentinel'", null).use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("kept", cursor.getString(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun successfulPublicationIsAtomicAndFailedAttemptLeavesItAvailableAsStale() {
        val repository = store.missingKanjiStore()
        val first = repository.publishInventory(
            inventory(setOf("水", "火"), notes = 10, fields = 30),
            startedAt = 100,
            completedAt = 200,
            providerFingerprint = "authority=test;spec=2",
        )

        val published = repository.inventoryState()

        assertEquals(MissingKanjiScanStatus.SUCCESS, first.status)
        assertEquals(setOf("水", "火"), published.published?.literals)
        assertEquals(10, published.published?.scan?.notesScanned)
        assertFalse(published.isStale)

        val failed = repository.recordUnsuccessfulScan(
            status = MissingKanjiScanStatus.FAILED,
            startedAt = 300,
            completedAt = 400,
            notesScanned = 7,
            fieldsScanned = 20,
            uniqueKanjiCount = 3,
            skippedNotes = 1,
            modelCount = 2,
            providerFingerprint = "authority=test;spec=2",
            failureCode = "provider_unavailable",
        )
        val stale = repository.inventoryState()

        assertEquals(MissingKanjiScanStatus.FAILED, failed.status)
        assertEquals("provider_unavailable", failed.failureCode)
        assertEquals(setOf("水", "火"), stale.published?.literals)
        assertEquals(failed.id, stale.latestAttempt?.id)
        assertTrue(stale.isStale)
    }

    @Test
    fun emptySuccessfulPublicationReplacesPreviousMembership() {
        val repository = store.missingKanjiStore()
        repository.publishInventory(inventory(setOf("水")), 1, 2, "test")

        val empty = repository.publishInventory(inventory(emptySet()), 3, 4, "test")
        val state = repository.inventoryState()

        assertEquals(0, empty.uniqueKanjiCount)
        assertEquals(empty.id, state.published?.scan?.id)
        assertEquals(emptySet<String>(), state.published?.literals)
        assertFalse(state.isStale)
    }

    @Test
    fun scanHistoryPruningRetainsEmptyPublishedGeneration() {
        val repository = store.missingKanjiStore()
        val published = repository.publishInventory(inventory(emptySet()), 1, 2, "test")
        repeat(55) { index ->
            repository.recordUnsuccessfulScan(
                MissingKanjiScanStatus.FAILED,
                10L + index,
                11L + index,
                0,
                0,
                0,
                0,
                0,
                "test",
                "provider_unavailable",
            )
        }

        val state = repository.inventoryState()
        assertEquals(published.id, state.published?.scan?.id)
        assertEquals(emptySet<String>(), state.published?.literals)
        assertTrue(state.isStale)
        assertEquals(51, rowCount(LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY_SCANS))
    }

    @Test
    fun publicationFailureRollsBackScanRowAndPriorInventory() {
        val repository = store.missingKanjiStore()
        val first = repository.publishInventory(inventory(setOf("水")), 1, 2, "test")
        store.writableDatabase.execSQL(
            """
            CREATE TRIGGER abort_missing_kanji_publication
            BEFORE INSERT ON ${LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY}
            WHEN NEW.literal = '火'
            BEGIN
                SELECT RAISE(ABORT, 'forced publication failure');
            END
            """.trimIndent(),
        )

        assertThrows(RuntimeException::class.java) {
            repository.publishInventory(inventory(setOf("火")), 3, 4, "test")
        }

        val state = repository.inventoryState()
        assertEquals(first.id, state.published?.scan?.id)
        assertEquals(first.id, state.latestAttempt?.id)
        assertEquals(setOf("水"), state.published?.literals)
    }

    @Test
    fun concurrentReaderSeesPreviousCompleteGenerationUntilCommit() {
        val repository = store.missingKanjiStore()
        repository.publishInventory(inventory(setOf("水")), 1, 2, "test")
        val observer = LocalStore(context)
        observer.readableDatabase
        val publicationPaused = CountDownLatch(1)
        val releasePublication = CountDownLatch(1)
        val hooked = MissingKanjiStore(
            store,
            MissingKanjiStore.PublicationHook {
                publicationPaused.countDown()
                check(releasePublication.await(5, TimeUnit.SECONDS))
            },
        )
        val writer = Executors.newSingleThreadExecutor()
        val reader = Executors.newSingleThreadExecutor()
        try {
            val write = writer.submit {
                hooked.publishInventory(inventory(setOf("火", "風")), 3, 4, "test")
            }
            assertTrue(publicationPaused.await(5, TimeUnit.SECONDS))

            val during = reader.submit<MissingKanjiInventoryState> {
                observer.missingKanjiStore().inventoryState()
            }.get(5, TimeUnit.SECONDS)

            assertEquals(setOf("水"), during.published?.literals)
            releasePublication.countDown()
            write.get(5, TimeUnit.SECONDS)
            assertEquals(
                setOf("火", "風"),
                observer.missingKanjiStore().inventoryState().published?.literals,
            )
        } finally {
            releasePublication.countDown()
            writer.shutdownNow()
            reader.shutdownNow()
            observer.close()
        }
    }

    @Test
    fun preferencesPersistAtomicallyAndInvalidateOtherStoreSnapshots() {
        val repository = store.missingKanjiStore()
        val observer = LocalStore(context)
        try {
            assertEquals(MissingKanjiPreferences(), repository.loadPreferences())
            assertEquals(MissingKanjiPreferences(), observer.missingKanjiStore().loadPreferences())

            val custom = MissingKanjiPreferences(
                preset = MissingKanjiPreferences.PRESET_CUSTOM,
                range = MissingKanjiFrequencyRange(750, 2_500, includeUnranked = true),
                searchQuery = "  water  ",
            )
            repository.savePreferences(custom)

            assertEquals(custom.copy(searchQuery = "water"), repository.loadPreferences())
            assertEquals(custom.copy(searchQuery = "water"), observer.missingKanjiStore().loadPreferences())
            assertThrows(IllegalArgumentException::class.java) {
                repository.savePreferences(
                    custom.copy(range = MissingKanjiFrequencyRange(5, 1)),
                )
            }
        } finally {
            observer.close()
        }
    }

    @Test
    fun manualSourcesAreIdempotentReactivatableAndDictionaryBacked() {
        val repository = store.missingKanjiStore()
        val water = candidate(
            "水",
            rank = 12,
            meanings = listOf("water", "liquid, \"clear\""),
            on = listOf("スイ"),
            kun = listOf("みず"),
        )

        val first = repository.addManualSources(
            listOf(water, candidate("A", 1), water),
            nowMillis = 100,
        )
        assertEquals(3, first.requestedCount)
        assertEquals(setOf("水"), first.addedLiterals)
        assertEquals(1, first.invalidCount)
        assertEquals(1, first.duplicateCount)

        val updated = water.copy(meanings = listOf("fresh water"))
        val second = repository.addManualSources(listOf(updated), nowMillis = 200)
        assertEquals(setOf("水"), second.alreadyActiveLiterals)
        assertEquals("fresh water", repository.manualSources().single().candidate.primaryMeaning)
        assertEquals(100L, repository.manualSources().single().addedAt)
        assertEquals(200L, repository.manualSources().single().updatedAt)

        assertEquals(1, repository.deactivateManualSources(listOf("水"), 300))
        assertTrue(repository.manualSources().isEmpty())
        assertFalse(repository.manualSources(activeOnly = false).single().active)

        val reactivated = repository.addManualSources(listOf(water), nowMillis = 400)
        val source = repository.manualSources().single()
        assertEquals(setOf("水"), reactivated.reactivatedLiterals)
        assertEquals(MissingKanjiStore.SOURCE_TYPE_DICTIONARY, source.sourceType)
        assertEquals(100L, source.addedAt)
        assertEquals(listOf("water", "liquid, \"clear\""), source.candidate.meanings)
        assertEquals(listOf("スイ"), source.candidate.onReadings)
        assertEquals(listOf("みず"), source.candidate.kunReadings)
    }

    @Test
    fun manualSourcesRejectCandidatesThatCannotFormBothStudyCores() {
        val result = store.missingKanjiStore().addManualSources(
            listOf(
                candidate("水", 12, meanings = listOf("water"), kun = listOf("みず")),
                candidate("火", 13, meanings = emptyList(), on = listOf("カ")),
                candidate("風", 14, meanings = listOf("wind")),
            ),
            nowMillis = 100,
        )

        assertEquals(setOf("水"), result.addedLiterals)
        assertEquals(setOf("火"), result.missingMeaningLiterals)
        assertEquals(setOf("風"), result.missingReadingLiterals)
        assertEquals(listOf("水"), store.missingKanjiStore().manualSources().map { it.candidate.literal })
    }

    @Test
    fun unreviewedManualSourceCanBeRemovedButReviewedHistoryWins() {
        val repository = store.missingKanjiStore()
        val water = candidate("水", 12, meanings = listOf("water"), kun = listOf("みず"))
        repository.addManualSources(listOf(water), nowMillis = 100)
        val row = store.activeStudyDashboardRows().single()
        val item = StudyQueueSeeder().seedQueue(
            listOf(row),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            200,
            0,
            ladder = null,
        ).single()
        store.replaceStudyItems(listOf(item))

        assertEquals(setOf("水"), repository.removableManualSourceLiterals())
        val removed = repository.removeUnreviewedManualSources(listOf("水"), nowMillis = 300)
        assertEquals(setOf("水"), removed.removedLiterals)
        assertTrue(repository.manualSources().isEmpty())
        assertTrue(store.studyItems().isEmpty())

        repository.addManualSources(listOf(water), nowMillis = 400)
        val restoredRow = store.activeStudyDashboardRows().single()
        val restored = StudyQueueSeeder().seedQueue(
            listOf(restoredRow),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            500,
            0,
            ladder = null,
        ).single()
        store.replaceStudyItems(listOf(restored))
        store.writableDatabase.execSQL(
            "UPDATE ${LocalStoreBase.TABLE_STUDY_ITEMS} SET total_reviews=1 WHERE kanji='水'",
        )
        store.clearStudyItemsCache()

        assertTrue(repository.removableManualSourceLiterals().isEmpty())
        val protected = repository.removeUnreviewedManualSources(listOf("水"), nowMillis = 600)
        assertEquals(setOf("水"), protected.reviewedLiterals)
        assertTrue(repository.manualSources().single().active)
        assertEquals(1, store.studyItems().single().totalReviews)
    }

    @Test
    fun manualSourceWritesInvalidateDashboardViewsAcrossStoreInstances() {
        val observer = LocalStore(context)
        try {
            assertTrue(observer.activeStudyDashboardRows().isEmpty())
            store.missingKanjiStore().addManualSources(
                listOf(candidate("水", 12, meanings = listOf("water"), kun = listOf("みず"))),
                nowMillis = 100,
            )

            assertEquals("水", observer.activeStudyDashboardRows().single().kanji)
            assertTrue(observer.activeDashboardRows().isEmpty())

            val row = store.activeStudyDashboardRows().single()
            val item = StudyQueueSeeder().seedQueue(
                listOf(row),
                emptyList(),
                RecordsSyncModels.Settings.kikuDefaults(),
                200,
                0,
                ladder = null,
            ).single()
            store.replaceStudyItems(listOf(item))

            assertEquals("水", observer.activeDashboardRows().single().kanji)
        } finally {
            observer.close()
        }
    }

    @Test
    fun allManualSourcesRemainSchedulerVisibleWhileHomeShowsOnlyAdmittedItems() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val candidates = (1..20).map { index ->
            candidate(
                literal = String(Character.toChars(0x4E00 + index)),
                rank = index,
                meanings = listOf("meaning $index"),
                on = listOf("オン"),
            )
        }
        store.missingKanjiStore().addManualSources(candidates, nowMillis = 100)

        val allRows = store.activeStudyDashboardRows()
        assertEquals(20, allRows.size)
        assertTrue(store.activeDashboardRows().isEmpty())

        val firstDay = StudyQueueSeeder().seedQueue(
            allRows,
            emptyList(),
            settings,
            1_000,
            0,
            ladder = null,
        )
        store.replaceStudyItems(firstDay)
        val expectedFirstDay = minOf(settings.newPerDay, settings.activeQueueCap, candidates.size)
        assertEquals(expectedFirstDay, firstDay.count { it.state != LocalStoreBase.STATE_RETIRED })
        assertEquals(expectedFirstDay, store.activeDashboardRows().size)
        assertEquals(20, store.activeStudyDashboardRows().size)

        val secondDayStart = 86_400_000L
        val secondDay = StudyQueueSeeder().seedQueue(
            store.activeStudyDashboardRows(),
            store.studyItems(),
            settings,
            secondDayStart + 1_000,
            secondDayStart,
            ladder = null,
        )
        assertTrue(
            secondDay.count { it.state != LocalStoreBase.STATE_RETIRED } > expectedFirstDay,
        )
    }

    @Test
    fun normalConfiguredModelSyncDoesNotEraseManualSources() {
        store.missingKanjiStore().addManualSources(
            listOf(candidate("水", 12, meanings = listOf("water"), kun = listOf("みず"))),
            nowMillis = 100,
        )

        store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList<RecordsImportModels.SuspendedImport>(),
            emptyList<RecordsImportModels.DashboardRow>(),
            RecordsSyncModels.Settings.kikuDefaults(),
            200,
            300,
            null,
        )

        assertEquals("水", store.missingKanjiStore().manualSources().single().candidate.literal)
        assertTrue(store.missingKanjiStore().manualSources().single().active)
    }

    @Test
    fun exportReceiptsAreDestinationScopedAndSurviveReopen() {
        val repository = store.missingKanjiStore()
        val destination = "anki:model=123;deck=456"

        assertEquals(
            2,
            repository.recordExportReceipts(
                listOf(
                    MissingKanjiExportReceipt("水", destination, 100, 1_001),
                    MissingKanjiExportReceipt("火", destination, 101, 1_002),
                    MissingKanjiExportReceipt("A", destination, 102, 1_003),
                ),
            ),
        )
        repository.recordExportReceipts(
            listOf(MissingKanjiExportReceipt("水", destination, 200, 1_001)),
        )

        assertEquals(setOf("水", "火"), repository.exportReceipts(destination).keys)
        assertEquals(200, repository.exportReceipts(destination).getValue("水").exportedAt)
        assertTrue(repository.exportReceipts("anki:other").isEmpty())

        store.close()
        store = LocalStore(context)
        assertEquals(
            1_002L,
            store.missingKanjiStore().exportReceipts(destination).getValue("火").externalNoteId,
        )
    }

    @Test
    fun rawAnkiFieldTextCannotReachDurableMissingKanjiTables() {
        val privateField = "PRIVATE_SENTENCE:<ruby>秘密<rt>ひみつ</rt></ruby>[sound:private.mp3]"
        val collector = AnkiKanjiInventoryCollector()
        collector.addNormalizedField(AnkiFieldTextNormalizer.normalize(privateField))
        store.missingKanjiStore().publishInventory(
            collector.finish(notesScanned = 1, skippedNotes = 0, modelCount = 1),
            1,
            2,
            "authority=test;spec=2",
        )
        store.missingKanjiStore().recordUnsuccessfulScan(
            MissingKanjiScanStatus.FAILED,
            3,
            4,
            1,
            1,
            2,
            0,
            1,
            privateField,
            privateField,
        )

        val storedText = StringBuilder()
        for (table in expectedTables()) {
            store.readableDatabase.rawQuery("SELECT * FROM $table", null).use { cursor ->
                while (cursor.moveToNext()) {
                    for (index in 0 until cursor.columnCount) {
                        if (cursor.getType(index) == android.database.Cursor.FIELD_TYPE_STRING) {
                            storedText.append(cursor.getString(index)).append('\n')
                        }
                    }
                }
            }
        }

        assertEquals(setOf("秘", "密"), store.missingKanjiStore().inventoryState().published?.literals)
        assertFalse(storedText.contains("PRIVATE_SENTENCE"))
        assertFalse(storedText.contains("private.mp3"))
        assertFalse(storedText.contains("ひみつ"))
        assertFalse(storedText.contains(privateField))
        assertTrue(storedText.contains("authority=unknown;spec=-1"))
        assertTrue(storedText.contains("unknown"))
    }

    @Test
    fun unsuccessfulScanRejectsSuccessStatusAndHasNoPublishedInventory() {
        val repository = store.missingKanjiStore()

        assertThrows(IllegalArgumentException::class.java) {
            repository.recordUnsuccessfulScan(
                MissingKanjiScanStatus.SUCCESS,
                1,
                2,
                0,
                0,
                0,
                0,
                0,
                "test",
                "",
            )
        }
        val cancelled = repository.recordUnsuccessfulScan(
            MissingKanjiScanStatus.CANCELLED,
            1,
            2,
            2,
            10,
            4,
            0,
            1,
            "test",
            "cancelled",
        )

        assertEquals(MissingKanjiScanStatus.CANCELLED, cancelled.status)
        assertNull(repository.inventoryState().published)
        assertNotNull(repository.inventoryState().latestAttempt)
        assertTrue(repository.inventoryState().isStale)
    }

    private fun inventory(
        literals: Set<String>,
        notes: Int = literals.size,
        fields: Int = literals.size,
    ): AnkiKanjiInventory {
        return AnkiKanjiInventory(
            literals = literals,
            notesScanned = notes,
            fieldsScanned = fields,
            skippedNotes = 0,
            modelCount = 1,
            malformedRowWarning = null,
        )
    }

    private fun candidate(
        literal: String,
        rank: Int?,
        meanings: List<String> = listOf("meaning"),
        on: List<String> = emptyList(),
        kun: List<String> = emptyList(),
    ): MissingKanjiCandidate {
        return MissingKanjiCandidate(literal, meanings, on, kun, rank)
    }

    private fun expectedTables(): List<String> {
        return listOf(
            LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY,
            LocalStoreBase.TABLE_ANKI_KANJI_INVENTORY_SCANS,
            LocalStoreBase.TABLE_MANUAL_KANJI_SOURCES,
            LocalStoreBase.TABLE_MISSING_KANJI_EXPORTS,
        )
    }

    private fun tableExists(db: SQLiteDatabase, table: String): Boolean {
        return db.rawQuery(
            "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table),
        ).use { cursor -> cursor.moveToFirst() }
    }

    private fun rowCount(table: String): Int {
        return store.readableDatabase.rawQuery("SELECT COUNT(*) FROM $table", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            cursor.getInt(0)
        }
    }
}
