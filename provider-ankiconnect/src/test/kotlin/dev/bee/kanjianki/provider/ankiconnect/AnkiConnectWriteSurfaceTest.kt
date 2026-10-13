package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiExportPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The enforcement boundary for Kani's desktop provider write surface: note tags,
 * plus additive notes in Kani's own deck and note type. Nothing else.
 *
 * The surface is enforced three ways, because each catches what the others miss:
 *
 * 1. **Positively, at the allowlist.** [AnkiConnectActions] enumerates every
 *    action Kani may send, and [AnkiConnectEnvelope.request] refuses to build a
 *    request for anything else. A new adapter cannot reach an unlisted action
 *    without editing the allowlist, which is a visible change.
 * 2. **Negatively, by name.** Every scheduling, note-rewriting, deck-options, and
 *    whole-collection action real AnkiConnect offers is named here and asserted
 *    absent from the allowlist. The positive list alone would not catch an action
 *    added to it by mistake; this catches exactly that.
 * 3. **Behaviorally, against a collection.** A full Missing Kanji export and a
 *    full tag write run against a [FakeAnkiCollection] holding cards with real
 *    review history, and every pre-existing card, deck options group, and note
 *    field is asserted unchanged afterward — new entries are allowed, because the
 *    write is additive. This is the assertion that would survive a refactor: it
 *    does not care which actions exist, only that nothing already there moved.
 */
class AnkiConnectWriteSurfaceTest {
    /**
     * Actions real AnkiConnect offers that would change scheduling state, note
     * content, deck configuration, or the collection as a whole. Kani writes none
     * of them — see the note-tag-only rule and the additive Missing Kanji
     * exception.
     */
    private val deniedActions = listOf(
        // Scheduling.
        "suspend", "unsuspend", "suspended", "areSuspended",
        "setDueDate", "forgetCards", "relearnCards", "answerCards",
        "setSpecificValueOfCard", "cardsSetFlag", "insertReviews",
        // Note and model rewrites.
        "updateNote", "updateNoteFields", "updateNoteTags", "updateNoteModel",
        "removeTags", "replaceTags", "replaceTagsInAllNotes", "clearUnusedTags",
        "deleteNotes", "updateModelTemplates", "updateModelStyling",
        "modelTemplateRename", "modelTemplateRemove", "modelFieldRemove",
        "modelFieldRename", "modelFieldReposition",
        // Decks and deck options.
        "deleteDecks", "changeDeck", "saveDeckConfig", "setDeckConfigId",
        "cloneDeckConfigId", "removeDeckConfigId",
        // Collection-wide and GUI-driving.
        "sync", "reloadCollection", "loadProfile", "deleteMediaFile",
        "storeMediaFile", "guiAnswerCard", "guiDeckReview", "guiUndo",
        "guiImportFile", "guiExitAnki", "exportPackage", "importPackage",
        "multiAction",
    )

    // ---- Positive allowlist ----------------------------------------------

    @Test
    fun everyActionKaniSendsIsOnTheAllowlist() {
        // Every action literal in the module's own sources, as sent to the wire.
        val sent = actionLiteralsInMainSources()

        assertTrue("no action literals found; the scanner is broken", sent.isNotEmpty())
        assertEquals(emptySet<String>(), sent - AnkiConnectActions.allowlist)
    }

    /**
     * The allowlist is not aspirational: an action nobody sends is an action nobody
     * reviewed the safety of, and it widens the surface for free.
     */
    @Test
    fun everyAllowlistedActionIsActuallySent() {
        val sent = actionLiteralsInMainSources()

        assertEquals(emptySet<String>(), AnkiConnectActions.allowlist - sent - INDIRECT_ACTIONS)
    }

    @Test
    fun theEnvelopeRefusesToBuildADeniedAction() {
        for (action in deniedActions) {
            val error = assertThrows(action, IllegalArgumentException::class.java) {
                AnkiConnectEnvelope.request(action)
            }
            assertTrue(action, error.message!!.contains(action))
        }
    }

    /**
     * A `multi` cannot smuggle one in either. Nesting is where an unchecked action
     * would be easiest to miss, because the outer action is allowlisted.
     */
    @Test
    fun aMultiRefusesToNestADeniedAction() {
        for (action in deniedActions) {
            assertThrows(action, IllegalArgumentException::class.java) {
                AnkiConnectEnvelope.multiRequest(listOf("findNotes" to null, action to null))
            }
        }
    }

    // ---- Negative deny-list ----------------------------------------------

    @Test
    fun noDeniedActionIsOnTheAllowlist() {
        for (action in deniedActions) {
            assertFalse(action, AnkiConnectActions.isAllowed(action))
        }
    }

    /**
     * The only writes on the list. Everything else Kani may send is a read, so a
     * future action arriving here is a decision someone has to make deliberately.
     */
    @Test
    fun theAllowlistedWritesAreExactlyTheDocumentedOnes() {
        assertEquals(
            setOf("addTags", "createDeck", "createModel", "addNotes"),
            AnkiConnectActions.allowlist.intersect(WRITE_ACTIONS),
        )
    }

    /** The fake implements every denied action the behavioral tests rely on. */
    @Test
    fun theFakeImplementsTheDeniedActionsItClaimsTo() {
        assertEquals(
            emptySet<String>(),
            FakeAnkiCollection.SCHEDULING_ACTIONS - deniedActions.toSet(),
        )
    }

    // ---- Behavioral: nothing moved ---------------------------------------

    /**
     * The assertion that matters. A complete Missing Kanji export — deck creation,
     * model creation, note creation, reconciliation — leaves every card's queue,
     * due, interval, ease, reps, lapses, and deck placement exactly as it found
     * them, along with every pre-existing note's fields and every deck's options
     * group.
     */
    @Test
    fun aFullMissingKanjiExportMovesNoSchedulingState() {
        val anki = collectionWithHistory()
        val before = anki.schedulingSnapshot()

        val result = AnkiConnectMissingKanjiWriter(anki.transport())
            .export(candidates("橋", "山", "川"), MissingKanjiExportPlanner.DEFAULT_DECK_NAME)

        assertNull(result.failureKind)
        assertEquals(3, result.createdNotes.size)
        assertPreserved(before, anki)
        assertEquals(0, anki.syncCount)
        assertNoDeniedActionSent(anki)
    }

    /** The same claim for the tag write, which touches notes the user owns. */
    @Test
    fun aFullTagWriteMovesNoSchedulingState() {
        val anki = collectionWithHistory()
        val before = anki.schedulingSnapshot()

        val outcome = AnkiConnectTagWriter(anki.transport()).addTag("kani_archived", setOf(900L, 901L))

        assertEquals(setOf(900L, 901L), outcome.tagged)
        assertPreserved(before, anki)
        assertEquals(0, anki.syncCount)
        assertNoDeniedActionSent(anki)
    }

    /** The tag write adds its tag and takes nothing away. */
    @Test
    fun aTagWriteOnlyEverAddsATag() {
        val anki = collectionWithHistory()
        val existing = anki.tagsOf(900L)

        AnkiConnectTagWriter(anki.transport()).addTag("kani_archived", setOf(900L))

        assertEquals(existing + "kani_archived", anki.tagsOf(900L))
    }

    /** A collection read is a read: even a failing export changes nothing. */
    @Test
    fun aRefusedExportMovesNoSchedulingState() {
        val anki = collectionWithHistory()
            .withModel(MissingKanjiExportPlanner.MODEL_NAME, fields = listOf("Front", "Back"))
        val before = anki.schedulingSnapshot()

        AnkiConnectMissingKanjiWriter(anki.transport())
            .export(candidates("橋"), MissingKanjiExportPlanner.DEFAULT_DECK_NAME)

        assertPreserved(before, anki)
        assertNoDeniedActionSent(anki)
    }

    /**
     * The inventory read is the largest surface Kani points at a user's collection,
     * and it is the one a scheduling write would be easiest to hide inside.
     */
    @Test
    fun aFullInventoryScanMovesNoSchedulingState() {
        val anki = collectionWithHistory()
        val before = anki.schedulingSnapshot()
        var notesSeen = 0

        val result = AnkiConnectInventoryGateway(anki.transport()).scan({ notesSeen += 1 })

        // The scan really ran; otherwise "nothing moved" would be vacuous.
        assertEquals(2, result.notesRead)
        assertEquals(2, notesSeen)
        assertPreserved(before, anki)
        assertEquals(0, anki.syncCount)
        assertNoDeniedActionSent(anki)
    }

    /**
     * Guards the guard. If a scheduling write ever *were* sent, the preservation
     * assertion has to notice — so the fake's own scheduling actions are proved to
     * change the snapshot. Without this, a fake that silently ignored those actions
     * would make every test above pass for the wrong reason.
     */
    @Test
    fun theSnapshotDetectsASchedulingWrite() {
        for (action in FakeAnkiCollection.SCHEDULING_ACTIONS - "sync") {
            // One suspended card and one active one, so both `suspend` and
            // `unsuspend` have something to move.
            val anki = collectionWithHistory().withCard(1_502L, 901L, queue = -1L)
            val before = anki.schedulingSnapshot()

            // Bypasses the allowlist deliberately: this is the only caller in the
            // codebase that sends a denied action, and it sends it to a fake.
            anki.applyUnchecked(action)

            val unchanged = before == anki.schedulingSnapshot()
            assertFalse(action, unchanged)
        }
    }

    /** `sync` changes no local state, so it is counted rather than snapshotted. */
    @Test
    fun theFakeCountsAFullCollectionSync() {
        val anki = collectionWithHistory()

        anki.applyUnchecked("sync")

        assertEquals(1, anki.syncCount)
    }

    /**
     * Every entry [before] held is still present and unchanged. New entries are
     * allowed: Kani's writes are additive, so a new note or a new deck options group
     * is the flow working. A *changed* or *missing* entry is the failure.
     */
    private fun assertPreserved(before: Map<String, String>, anki: FakeAnkiCollection) {
        val after = anki.schedulingSnapshot()
        for ((key, value) in before) {
            assertEquals(key, value, after[key])
        }
    }

    private fun assertNoDeniedActionSent(anki: FakeAnkiCollection) {
        assertEquals(emptySet<String>(), anki.log.toSet() intersect deniedActions.toSet())
        assertTrue(anki.log.all(AnkiConnectActions::isAllowed))
    }

    private fun collectionWithHistory(): FakeAnkiCollection = FakeAnkiCollection()
        .withModel("Japanese", id = 42L)
        .withRawNote(900L, "Japanese", mapOf("Expression" to "脱出", "Reading" to "だっしゅつ"))
        .withRawNote(901L, "Japanese", mapOf("Expression" to "橋", "Reading" to "はし"))
        .withCard(1_500L, 900L)
        .withCard(1_501L, 901L)

    private fun candidates(vararg literals: String): List<MissingKanjiCandidate> =
        literals.map { MissingKanjiCandidate(it, meanings = listOf("meaning of $it")) }

    /**
     * Every string literal in this module's main sources that names an AnkiConnect
     * action, found by scanning for the argument of an action-taking call. The scan
     * is deliberately source-level: it sees an action a future adapter hardcodes
     * even if no test exercises that adapter.
     */
    private fun actionLiteralsInMainSources(): Set<String> {
        val found = LinkedHashSet<String>()
        val pattern = Regex("""(?:request|requireAllowed|isAllowed)\(\s*"([A-Za-z]+)"""")
        val sources = File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }
        for (source in sources) {
            val text = source.readText()
            for (match in pattern.findAll(text)) {
                found.add(match.groupValues[1])
            }
            // Nested multi actions are built as `"action" to params` pairs.
            for (match in Regex(""""([a-zA-Z]+)"\s+to\s+\w*[Pp]arams""").findAll(text)) {
                found.add(match.groupValues[1])
            }
        }
        return found
    }

    private companion object {
        /**
         * Allowlisted actions no source literal names, because a caller supplies the
         * action name. `multi` is built by [AnkiConnectEnvelope.multiRequest] from
         * its nested pairs, and `modelFieldsOnTemplates` is reserved for the note-type
         * inspection path that has no builder yet.
         */
        val INDIRECT_ACTIONS = setOf("modelFieldsOnTemplates")

        /** Every allowlisted action that changes anything. Used to pin the count. */
        val WRITE_ACTIONS = setOf(
            "addTags",
            "createDeck",
            "createModel",
            "addNotes",
        )
    }
}
