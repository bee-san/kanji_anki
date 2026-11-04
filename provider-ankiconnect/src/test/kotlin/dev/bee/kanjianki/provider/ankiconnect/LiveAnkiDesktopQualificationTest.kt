package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncapi.CollectionAvailability
import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionInventoryConsumer
import dev.bee.kanjianki.syncapi.CollectionProgress
import dev.bee.kanjianki.syncapi.CollectionProgressListener
import dev.bee.kanjianki.syncapi.MissingKanjiProgressListener
import dev.bee.kanjianki.syncapi.MissingKanjiReceiptSink
import dev.bee.kanjianki.syncapi.MissingKanjiWriteResult
import dev.bee.kanjianki.syncapi.testing.CrossProviderSnapshotSpec
import dev.bee.kanjianki.syncdomain.ProviderNotePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Qualifies Kani's desktop provider against a **real** pinned Anki Desktop.
 *
 * Every other test in this module runs against `FakeAnkiConnectServer`, and a fake
 * agrees with whatever the client believes. That is the gap this closes. The first
 * probe against a real host found that `getActiveProfile` — which Kani required and
 * refused to connect without — is not an AnkiConnect action at all, so the
 * handshake reported every real Anki as unavailable while passing against a mock
 * taught to answer it. Writing this suite's fixture seeder found a second one:
 * `setSpecificValueOfCard` reports per-item failures *inside* a success envelope.
 * Neither is findable against a mock.
 *
 * ### Opt-in: this does not run in the deterministic gate
 *
 * It needs a live Anki process, so it is skipped unless `-Dkani.liveAnkiDesktop=true`
 * is set. `ci/scripts/run_anki_desktop_fixture.sh` boots the pinned host and
 * `ci/scripts/seed_anki_desktop_kiku_collection.py` seeds the sanitized collection;
 * `docs/desktop-provider-qualification-runbook.md` is the operator procedure.
 * Skipping rather than failing is deliberate — a developer with no live Anki must
 * still be able to run `:provider-ankiconnect:test`.
 *
 * ### It refuses to run against a collection that is not the fixture
 *
 * Three of these tests write (two tag writes and one additive note), so
 * [assertIsolatedFixture] verifies before anything else that the port is not
 * AnkiConnect's standard 8765 and that the *loaded* profile's media directory names
 * the throwaway profile. That check fails the run rather than skipping it:
 * "probably isolated" is not good enough when the next step is a write. An
 * operator's own Anki can be running throughout and cannot be reached from here.
 *
 * ### Evidence is aggregate and sanitized
 *
 * Assertions are on counts, shapes, capabilities, and normalization outcomes. The
 * fixture's invented kanji appear because they are this suite's own content; no
 * field, deck, or model text from any real collection is read or reported.
 */
class LiveAnkiDesktopQualificationTest {
    @Test
    fun handshakeReportsReadyWithTheCapabilitiesThisAnkiActuallyHas() {
        val status = gateway().status()

        assertEquals(status.message, CollectionAvailability.READY, status.availability)
        assertTrue(
            "read capabilities missing: ${status.capabilities}",
            status.capabilities.containsAll(AnkiConnectGateway.READ_CAPABILITIES),
        )
        // The pinned AnkiConnect reports addTags, so the tag write must be
        // advertised. If a future pin drops the action this must fail loudly rather
        // than quietly degrade Kani to read-only.
        assertTrue(
            "tag write not advertised: ${status.capabilities}",
            CollectionCapability.NOTE_TAG_WRITE in status.capabilities,
        )
        // Never fabricated: stock AnkiConnect exposes no memory state, and
        // advertising it would make admission seed from values that do not exist.
        assertFalse(
            "FSRS memory must not be advertised",
            CollectionCapability.FSRS_MEMORY_STATE in status.capabilities,
        )
    }

    /** The settings model picker must see the fixture's note type and its fields. */
    @Test
    fun noteTypesIncludeTheFixtureModelWithItsFields() {
        val kiku = gateway().noteTypes().firstOrNull { it.name == MODEL }

        assertNotNull("note type $MODEL not reported", kiku)
        assertEquals(FIELDS, kiku!!.fields)
    }

    /**
     * The configured-model read end to end against a real collection: the fixture's
     * eight notes, their cards, and the scheduling state each was seeded with.
     */
    @Test
    fun configuredReadReturnsEveryFixtureNoteWithItsRealSchedulingState() {
        val settings = settings()
        val result = gateway().readWithDiagnostics(settings)
        val snapshot = result.snapshot

        assertEquals(FIXTURE_NOTES, snapshot.notes.size)
        assertEquals(FIXTURE_NOTES, snapshot.cards.size)
        // A real collection must not produce rows Kani cannot parse.
        assertEquals(0, result.skipped.total)
        assertNull(result.malformedRowWarning)

        // Kani studies the front template only; accepting a second template would
        // double-count a kanji's evidence.
        assertTrue("every card must be ord 0", snapshot.cards.all { it.ord == 0 })

        // Suspension is `queue < 0`, so the buried card counts too. AnkiConnect's
        // own areSuspended (`queue == -1`) would see one card here, not two.
        assertEquals(
            "suspended (including buried) count",
            FIXTURE_SUSPENDED + FIXTURE_BURIED,
            snapshot.cards.count { it.suspended },
        )
        assertTrue(
            "the buried card must read as suspended",
            snapshot.cards.any { it.queue == BURIED_QUEUE && it.suspended },
        )

        // Normalization held over real provider values.
        assertTrue("intervals must never be negative", snapshot.cards.all { it.intervalDays >= 0 })
        assertTrue(
            "counters must never be negative",
            snapshot.cards.all { it.reps >= 0 && it.lapses >= 0 },
        )

        assertEquals(
            "mature active cards",
            FIXTURE_MATURE_ACTIVE,
            snapshot.cards.count { it.mature(settings.matureDays) },
        )
    }

    /**
     * FSRS absence is the desktop path's defining difference, and it must present as
     * *absence* rather than as invented values: admission falls back to
     * interval/lapse evidence, which only helps if the interval is real and the
     * memory state is genuinely null.
     */
    @Test
    fun fsrsMemoryStateIsAbsentAndIntervalLapseEvidenceIsPresent() {
        val cards = gateway().readWithDiagnostics(settings()).snapshot.cards

        assertTrue(
            "no card may carry a fabricated memory state",
            cards.all {
                it.fsrsStability == null && it.fsrsDifficulty == null && it.fsrsRetrievability == null
            },
        )
        // The fallback evidence has to actually be there, or absence of FSRS would
        // amount to absence of evidence.
        assertTrue(
            "at least one card must carry real interval/lapse evidence",
            cards.any { it.intervalDays > 0 && it.reps > 0 },
        )
        assertTrue("lapse evidence must be present", cards.any { it.lapses > 0 })
    }

    /**
     * Two reads of an unchanged collection must produce identical snapshots.
     * Without this a "collection changed" signal downstream could fire on nothing
     * but provider ordering.
     */
    @Test
    fun repeatedReadsOfAnUnchangedCollectionAreIdentical() {
        val live = gateway()
        val first = live.readCollection(settings())
        val second = live.readCollection(settings())

        assertEquals(
            first.cards.map { CrossProviderSnapshotSpec.agreedView(it) },
            second.cards.map { CrossProviderSnapshotSpec.agreedView(it) },
        )
        assertEquals(
            first.notes.map { CrossProviderSnapshotSpec.agreedView(it) },
            second.notes.map { CrossProviderSnapshotSpec.agreedView(it) },
        )
    }

    /**
     * Every field under the cross-provider agreement must be populated from the real
     * provider. A snapshot that satisfies the shape but leaves the agreed fields at
     * their defaults would still compare equal to another empty snapshot, so the
     * conformance spec would be passing on nothing.
     */
    @Test
    fun agreedCrossProviderFieldsAreAllPopulatedFromTheRealProvider() {
        val snapshot = gateway().readCollection(settings())
        val cardView = CrossProviderSnapshotSpec.agreedView(snapshot.cards.first())
        val noteView = CrossProviderSnapshotSpec.agreedView(snapshot.notes.first())

        assertEquals(CrossProviderSnapshotSpec.agreedCardFields.toSet(), cardView.keys)
        assertEquals(CrossProviderSnapshotSpec.agreedNoteFields.toSet(), noteView.keys)
        assertTrue("noteId must be real", snapshot.cards.all { it.noteId > 0L })
        assertTrue("cardId must be real", snapshot.cards.all { it.cardId > 0L })
        assertTrue("model name must be reported", snapshot.notes.all { it.modelName == MODEL })
        assertTrue(
            "required fields must be populated",
            snapshot.notes.all { it.field(FIELDS.first()).isNotBlank() },
        )
    }

    /** A note selected by the user's own Anki search must merge in and be marked. */
    @Test
    fun browserQueryNotesMergeAndAreMarked() {
        val snapshot = gateway().readCollection(browserQuerySettings())

        val matched = snapshot.cards.filter { it.browserQueryMatched }
        assertEquals(FIXTURE_BROWSER_TAGGED, matched.size)
        // The whole configured model is still read; the query marks, it does not filter.
        assertEquals(FIXTURE_NOTES, snapshot.cards.size)
        assertTrue(
            "browser-matched cards must still be normalized",
            matched.all { it.intervalDays >= 0 },
        )
    }

    /** Progress must actually be reported, or a long desktop sync looks hung. */
    @Test
    fun readReportsProgressStages() {
        val stages = ArrayList<CollectionProgress.Stage>()
        gateway().readCollection(settings()) { progress -> stages.add(progress.stage) }

        assertTrue("no progress reported", stages.isNotEmpty())
        assertTrue(
            "note-type stage missing from $stages",
            CollectionProgress.Stage.FINDING_NOTE_TYPE in stages,
        )
        assertTrue(
            "card stage missing from $stages",
            CollectionProgress.Stage.SCANNING_CARDS in stages,
        )
    }

    /**
     * Cancellation must stop the read rather than drain the collection. Cancelled
     * before the first round trip, so the outcome is unambiguous.
     */
    @Test
    fun cancellationStopsTheReadInsteadOfCompleting() {
        val failure = runCatching {
            gateway().readProviderCollection(
                settings(),
                CollectionProgressListener.NONE,
                CollectionCancellation { true },
            )
        }.exceptionOrNull()

        assertNotNull("a cancelled read must not complete", failure)
    }

    /**
     * The provider snapshot the sync engine binds against: source identity present,
     * FSRS deliberately absent from the capability set.
     */
    @Test
    fun providerCollectionSnapshotCarriesSourceIdentityButNotFsrs() {
        val provider = gateway().readProviderCollection(
            settings(),
            CollectionProgressListener.NONE,
            CollectionCancellation.NONE,
        )

        assertTrue(
            "source identity must be advertised: ${provider.capabilities}",
            CollectionCapability.SOURCE_IDENTITY in provider.capabilities,
        )
        assertFalse(
            "FSRS memory must not be advertised",
            CollectionCapability.FSRS_MEMORY_STATE in provider.capabilities,
        )
        assertNotNull("source identity missing", provider.sourceIdentity)
        assertEquals(FIXTURE_NOTES, provider.snapshot.notes.size)
    }

    /**
     * The collection-wide inventory scan spans every note type, including the stock
     * ones Anki ships, so it must see at least the fixture's own notes.
     */
    @Test
    fun inventoryScanReadsTheWholeCollection() {
        val scannedModels = ArrayList<String>()
        val result = AnkiConnectInventoryGateway(transport()).scan(
            CollectionInventoryConsumer { note -> scannedModels.add(note.modelName) },
            CollectionProgressListener.NONE,
            CollectionCancellation.NONE,
        )

        assertTrue("notesRead=${result.notesRead}", result.notesRead >= FIXTURE_NOTES)
        assertEquals("no note may be skipped", 0, result.skippedNotes)
        assertTrue("modelCount=${result.modelCount}", result.modelCount >= 1)
        // Every note read is handed to the consumer; the gateway accumulates none.
        assertEquals(result.notesRead, scannedModels.size)
        assertTrue("fixture model not scanned", scannedModels.contains(MODEL))
    }

    /**
     * The one tag write Kani's automatic sync path may perform against a real Anki:
     * `kani_archived` on fully-suspended imported notes. Idempotent, so re-running
     * the suite re-tags the same notes and still reports success.
     */
    @Test
    fun archiveTagWriteLandsOnSuspendedNotesAndIsIdempotent() {
        val live = gateway()
        val snapshot = live.readCollection(settings())

        val first = live.removeArchivedSuspendedCards(snapshot)
        assertEquals(first.message, FIXTURE_ARCHIVABLE, first.taggedNotes)
        // Kani never deletes a note; archiving is a tag write only.
        assertEquals(0, first.deletedNotes)

        // Read back through the provider: the tag is really on the notes.
        val tagged = live.readCollection(settings()).notes.count {
            ProviderNotePolicy.isArchivedTagPresent(it.tags)
        }
        assertEquals(FIXTURE_ARCHIVABLE, tagged)

        val second = live.removeArchivedSuspendedCards(snapshot)
        assertEquals("re-tagging must still succeed", FIXTURE_ARCHIVABLE, second.taggedNotes)
    }

    /**
     * The repaired tag is the manual-confirm-only write. This drives the gateway
     * method directly, which is what the confirmed path does; it deliberately does
     * not exercise any automatic runner, because the automatic runner is not
     * authorized to perform this write.
     */
    @Test
    fun repairedTagWriteLandsOnTheRequestedNote() {
        val live = gateway()
        val target = live.readCollection(settings()).notes.first()

        val summary = live.tagRepairedNotes(setOf(target.noteId), CollectionProgressListener.NONE)

        assertEquals(summary.message, setOf(target.noteId), summary.taggedNoteIds)
        assertEquals(emptySet<Long>(), summary.failedNoteIds)
        val reread = live.readCollection(settings()).notes.first { it.noteId == target.noteId }
        assertTrue(
            "repaired tag not present on the target note",
            ProviderNotePolicy.isRepairedTagPresent(reread.tags),
        )
    }

    /** An empty request must not reach the provider at all. */
    @Test
    fun repairedTagWriteWithNoNotesIsANoOp() {
        val summary = gateway().tagRepairedNotes(emptySet(), CollectionProgressListener.NONE)

        assertEquals(emptySet<Long>(), summary.taggedNoteIds)
        assertEquals(emptySet<Long>(), summary.failedNoteIds)
    }

    /**
     * Missing Kanji is Kani's only non-tag provider write, and it is additive into
     * Kani's own dedicated model and deck. Run twice: the second export must report
     * the note as already present rather than creating a duplicate, which is the
     * property that makes a retry after a partial or unacknowledged write safe.
     */
    @Test
    fun missingKanjiExportCreatesItsOwnNoteThenReportsItAlreadyPresentOnRetry() {
        val writer = AnkiConnectMissingKanjiWriter(transport())
        // Capability-gated: without every proving action the flow must refuse and
        // the caller offers CSV instead. The pinned AnkiConnect has them all.
        val status = writer.status()
        assertEquals(
            status.message,
            setOf(CollectionCapability.MISSING_KANJI_WRITE),
            status.capabilities,
        )

        val candidate = MissingKanjiCandidate(
            literal = MISSING_KANJI_LITERAL,
            meanings = listOf("fixture"),
            onReadings = listOf("シ"),
            kunReadings = listOf("ためし"),
        )

        val first = export(writer, candidate)
        assertTrue("first export did not complete: ${first.failureKind}", first.completed)
        assertNull(first.failureKind)
        assertEquals(emptySet<String>(), first.unfinishedLiterals)
        assertNotNull("destination key missing", first.destinationKey)
        assertEquals(
            "the literal must be either created now or already present from a prior run",
            1,
            first.createdNotes.size + first.alreadyPresentNotes.size,
        )

        val retry = export(writer, candidate)
        assertTrue("retry did not complete: ${retry.failureKind}", retry.completed)
        assertEquals("a retry must not create a duplicate", 0, retry.createdNotes.size)
        assertEquals(1, retry.alreadyPresentNotes.size)
        // Same collection, same model: the destination must be the same key, or
        // receipts earned by the first run would not suppress the second.
        assertEquals(first.destinationKey, retry.destinationKey)
    }

    /**
     * The browser handoff. Only that Anki accepted the search is asserted:
     * `guiBrowse`'s matched ids are deliberately discarded, so there is nothing else
     * to check without turning a handoff into a read path.
     */
    @Test
    fun browserHandoffIsAcceptedByTheRealAnki() {
        val handoff = AnkiConnectBrowseHandoff(transport())

        assertTrue(handoff.browse(ProviderNotePolicy.modelSearch(MODEL)))
        // A blank query is refused locally rather than sent, because Anki would
        // answer it by selecting the entire collection.
        assertFalse(handoff.browse("   "))
    }

    /**
     * Source binding must key on the loaded profile, not the endpoint alone: every
     * profile on a machine answers on the same loopback endpoint, so an
     * endpoint-only key would survive a profile switch and mirror a different
     * collection into the same database.
     */
    @Test
    fun sourceKeyBindsToTheLoadedProfileNotJustTheEndpoint() {
        val liveTransport = transport()
        val ready = AnkiConnectHandshake(liveTransport).run(null)
            as AnkiConnectHandshake.Status.Ready
        val profileIdentity = ready.profileIdentity

        assertNotNull("profile identity must be reported", profileIdentity)
        assertTrue(
            "the identity must name the throwaway fixture profile",
            profileIdentity!!.contains(expectedProfile),
        )
        val key = AnkiConnectSourceKey.of(liveTransport.endpointUrl(), profileIdentity)
        assertTrue(key.startsWith(liveTransport.endpointUrl()))
        assertTrue(key.endsWith(profileIdentity))
    }

    private fun export(
        writer: AnkiConnectMissingKanjiWriter,
        candidate: MissingKanjiCandidate,
    ): MissingKanjiWriteResult = writer.export(
        listOf(candidate),
        MISSING_KANJI_DECK,
        MissingKanjiProgressListener.NONE,
        MissingKanjiReceiptSink.NONE,
        CollectionCancellation.NONE,
    )

    private fun gateway(): AnkiConnectGateway = AnkiConnectGateway(transport())

    private fun settings(): RecordsSyncModels.Settings = RecordsSyncModels.Settings.kikuDefaults()

    /** The fixture's settings plus the browser query the seeder tagged one note with. */
    private fun browserQuerySettings(): RecordsSyncModels.Settings {
        val base = settings()
        return RecordsSyncModels.Settings(
            base.modelName,
            base.templateName,
            base.expressionField,
            base.readingField,
            base.meaningField,
            base.sentenceField,
            base.frequencyField,
            base.frequencySortField,
            base.matureDays,
            base.matureSupportThreshold,
            base.suspendedRankMin,
            base.suspendedRankMax,
            base.activeQueueCap,
            base.newPerDay,
            base.writingTriggerMissDays,
            base.recognitionPromotionPasses,
            base.realDueReviewsToMove,
            base.importActiveCards,
            base.importSuspendedCards,
            base.importTaggedCards,
            base.importTags,
            base.importWeakCards,
            base.importWeakFsrsDifficultyThreshold,
            base.importWeakLapsesThreshold,
            base.importMinMatchingCardsPerKanji,
            true,
            "tag:$BROWSER_TAG",
            base.newCardSortMode,
            base.ladderPromotionIntervalDays,
            base.ladderDemotionFailStreak,
            base.ladderPromotionMinPasses,
        )
    }

    companion object {
        /** Set to `true` to run against a live fixture; the suite is skipped otherwise. */
        private const val ENABLE_PROPERTY = "kani.liveAnkiDesktop"
        private const val ENDPOINT_PROPERTY = "kani.liveAnkiDesktopEndpoint"
        private const val PROFILE_PROPERTY = "kani.liveAnkiDesktopProfile"

        private const val DEFAULT_ENDPOINT = "http://127.0.0.1:18765"
        private const val DEFAULT_PROFILE = "KaniFixture"

        /** AnkiConnect's standard port, where an operator's real Anki listens. */
        private const val RESERVED_LIVE_PORT = ":8765"

        private const val MODEL = "Kiku"
        private const val BROWSER_TAG = "kani_query_test"

        /** Must match `ci/scripts/seed_anki_desktop_kiku_collection.py`. */
        private const val FIXTURE_NOTES = 8
        private const val FIXTURE_SUSPENDED = 1
        private const val FIXTURE_BURIED = 1
        private const val FIXTURE_BROWSER_TAGGED = 1

        /**
         * Notes eligible for `kani_archived`. Every fixture note has exactly one
         * card, so it is the notes whose only card Kani reads as suspended — the
         * suspended one and the buried one, since Kani's rule is `queue < 0`.
         */
        private const val FIXTURE_ARCHIVABLE = FIXTURE_SUSPENDED + FIXTURE_BURIED

        /**
         * Active cards at or above the 21-day maturity default: 箱 42, 箸 28, 端 35.
         * 鍵 (90) and 窓 (21) are excluded because neither is active, and 橋 (7) is
         * below the threshold.
         */
        private const val FIXTURE_MATURE_ACTIVE = 3

        private const val BURIED_QUEUE = -2

        private val FIELDS = listOf(
            "Expression",
            "ExpressionReading",
            "MainDefinition",
            "Sentence",
            "Frequency",
            "FreqSort",
        )

        /** Kani's own additive destination inside the fixture. Never a user deck. */
        private const val MISSING_KANJI_DECK = "KaniFixture::Missing Kanji"
        private const val MISSING_KANJI_LITERAL = "試"

        private var endpointUrl: String = DEFAULT_ENDPOINT
        private var expectedProfile: String = DEFAULT_PROFILE

        @BeforeClass
        @JvmStatic
        fun requireIsolatedLiveFixture() {
            assumeTrue(
                "Set -D$ENABLE_PROPERTY=true with a live Anki Desktop fixture running. " +
                    "See docs/desktop-provider-qualification-runbook.md.",
                System.getProperty(ENABLE_PROPERTY) == "true",
            )
            endpointUrl = System.getProperty(ENDPOINT_PROPERTY) ?: DEFAULT_ENDPOINT
            expectedProfile = System.getProperty(PROFILE_PROPERTY) ?: DEFAULT_PROFILE
            assertIsolatedFixture()
        }

        /**
         * Fails the run — rather than skipping it — if the target is not provably the
         * throwaway fixture.
         */
        private fun assertIsolatedFixture() {
            if (endpointUrl.contains(RESERVED_LIVE_PORT)) {
                throw AssertionError(
                    "Refusing to qualify against $endpointUrl: that is where a real Anki " +
                        "listens. Use the fixture's isolated port.",
                )
            }
            val status = AnkiConnectHandshake(transport()).run(null)
            val ready = status as? AnkiConnectHandshake.Status.Ready
                ?: throw AssertionError("Live Anki at $endpointUrl is not ready: $status")
            // getMediaDirPath reports the *loaded* profile, which is the only answer
            // to "which collection is this". getProfiles lists every profile on the
            // machine regardless of which one is open.
            if (!ready.profileIdentity.orEmpty().contains(expectedProfile)) {
                throw AssertionError(
                    "Refusing to qualify: the loaded profile is not the expected throwaway " +
                        "profile '$expectedProfile'. This may be a real collection.",
                )
            }
        }

        private fun transport(): AnkiConnectTransport {
            val parsed = AnkiConnectEndpoint.parse(endpointUrl)
            val endpoint = (parsed as? AnkiConnectEndpoint.Result.Valid)?.endpoint
                ?: throw AssertionError("Live endpoint $endpointUrl is not a loopback endpoint.")
            return AnkiConnectTransport(endpoint, JdkHttpExchange())
        }
    }
}
