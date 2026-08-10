package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the keybinding editor screen owes a host that renders it, and what the edit ids
 * owe a host that resolves them.
 *
 * The screen is data, so these are the assertions a Settings-screen test could not make
 * honestly: that every command has a row even when unbound, that a refused candidate
 * carries its reason rather than vanishing, and that an edit id this build cannot read
 * changes nothing rather than rebinding a grade key by accident.
 */
class StudyKeybindingScreenTest {
    @Test
    fun everyCommandGetsARowSoNoActionIsInvisibleInTheEditor() {
        val screen = StudyKeybindingScreen.of(StudyKeybindings.DEFAULT, KeyboardPlatform.LINUX)

        assertEquals(StudyCommand.entries.toList(), screen.rows.map { it.command })
        // Unbinding must leave the row present and empty. A missing row is how a command
        // becomes permanently unreachable from the editor that is supposed to restore it.
        val unbound = StudyKeybindingScreen.of(StudyKeybindings(emptyMap()), KeyboardPlatform.LINUX)
        assertEquals(StudyCommand.entries.toList(), unbound.rows.map { it.command })
        for (row in unbound.rows) {
            assertEquals(emptyList(), row.bound)
            assertEquals("", row.acceleratorLabel)
            assertNull(row.menuAccelerator)
        }
    }

    @Test
    fun aRowShowsEveryKeyItsCommandHoldsInThePlatformsNotation() {
        val screen = StudyKeybindingScreen.of(StudyKeybindings.DEFAULT, KeyboardPlatform.LINUX)

        assertEquals("Space, Enter, Numpad Enter", screen.row(StudyCommand.PRIMARY).acceleratorLabel)
        assertEquals("3, Numpad 3, P", screen.row(StudyCommand.GRADE_PASS).acceleratorLabel)
        assertEquals("1, Numpad 1, F", screen.row(StudyCommand.GRADE_FAIL).acceleratorLabel)
        assertEquals("Ctrl+Z, Super+Z", screen.row(StudyCommand.UNDO).acceleratorLabel)
        // The same set read on macOS names the same physical keys the Mac way.
        val mac = StudyKeybindingScreen.of(StudyKeybindings.DEFAULT, KeyboardPlatform.MACOS)
        assertEquals("⌃Z, ⌘Z", mac.row(StudyCommand.UNDO).acceleratorLabel)
    }

    @Test
    fun aMenuAdvertisesOneAcceleratorAndItIsTheCanonicalOne() {
        // A native menu item takes a single accelerator, and the reviewed defaults list
        // the Anki-canonical key first, so the menu should show that one rather than
        // whichever key happens to sort first.
        val screen = StudyKeybindingScreen.of(StudyKeybindings.DEFAULT, KeyboardPlatform.LINUX)
        assertEquals("Space", screen.row(StudyCommand.PRIMARY).menuAccelerator)
        assertEquals("3", screen.row(StudyCommand.GRADE_PASS).menuAccelerator)
        assertEquals("1", screen.row(StudyCommand.GRADE_FAIL).menuAccelerator)
        assertEquals("Ctrl+Z", screen.row(StudyCommand.UNDO).menuAccelerator)
        assertEquals("⌃Z", StudyKeybindingScreen.of(StudyKeybindings.DEFAULT, KeyboardPlatform.MACOS)
            .row(StudyCommand.UNDO).menuAccelerator)
    }

    @Test
    fun aCandidateCarriesTheReasonItCannotBeChosenRatherThanBeingHidden() {
        // Hiding an unavailable key leaves the user hunting for a row that is not there.
        // Showing it with "already Fail" is the difference between a bug and an answer.
        val screen = StudyKeybindingScreen.of(
            bindings = StudyKeybindings.DEFAULT,
            platform = KeyboardPlatform.LINUX,
            candidates = listOf(
                StudyKeystroke(StudyKey.DIGIT_1),
                StudyKeystroke(StudyKey.G),
                StudyKeystroke(StudyKey.C, ctrl = true),
            ),
        )
        val pass = screen.row(StudyCommand.GRADE_PASS)

        assertEquals(
            StudyKeybindingIssue.Conflict(StudyKeystroke(StudyKey.DIGIT_1), StudyCommand.GRADE_FAIL),
            pass.candidates.first { it.stroke == StudyKeystroke(StudyKey.DIGIT_1) }.issue,
        )
        assertEquals(
            StudyKeybindingIssue.Reserved(StudyKeystroke(StudyKey.C, ctrl = true), "Copy"),
            pass.candidates.first { it.stroke == StudyKeystroke(StudyKey.C, ctrl = true) }.issue,
        )
        assertEquals(
            listOf(StudyKeystroke(StudyKey.G)),
            pass.bindable.map { it.stroke },
        )
        // The row's own keys are offered without an issue, so re-picking a shown key is
        // not reported as a conflict with itself.
        val fail = screen.row(StudyCommand.GRADE_FAIL)
        assertNull(fail.candidates.first { it.stroke == StudyKeystroke(StudyKey.DIGIT_1) }.issue)
    }

    @Test
    fun theOfferedCandidatesAreOnlyKeystrokesTheStudyAdapterCanActuallyReceive() {
        // Offering a chord the key adapter drops would advertise a binding that silently
        // does nothing once saved.
        val letters = StudyKey.entries.filter { it.isLetter }
        assertEquals(26, letters.size)

        val linux = StudyKeybindingScreen.defaultCandidates(KeyboardPlatform.LINUX)
        assertEquals(StudyKey.entries.size + letters.size, linux.size)
        assertTrue(linux.containsAll(StudyKey.entries.map { StudyKeystroke(it) }))
        // Only the platform's own primary modifier: `⌘Z` on Linux would name a key that
        // is not on the keyboard.
        assertTrue(StudyKeystroke(StudyKey.Z, ctrl = true) in linux)
        assertFalse(StudyKeystroke(StudyKey.Z, meta = true) in linux)
        // No chord on a digit, numpad key, Space, or Enter — chords no platform documents
        // and several IMEs claim.
        assertTrue(linux.none { !it.isPlain && !it.key.isLetter })

        val macos = StudyKeybindingScreen.defaultCandidates(KeyboardPlatform.MACOS)
        assertEquals(linux.size, macos.size)
        assertTrue(StudyKeystroke(StudyKey.Z, meta = true) in macos)
        assertFalse(StudyKeystroke(StudyKey.Z, ctrl = true) in macos)

        // Every reviewed default is offerable on its own platform, so a reset is always
        // reachable by hand too. The default undo chord is `Ctrl+Z` and `⌘Z` both, so
        // each platform offers the one it can type.
        for (platform in KeyboardPlatform.entries) {
            val offered = StudyKeybindingScreen.defaultCandidates(platform)
            val missing = StudyKeybindings.DEFAULT.bindings.keys.filterNot { it in offered }
            assertEquals(
                listOf(StudyKeystroke(StudyKey.Z, meta = !platform.metaIsCommand, ctrl = platform.metaIsCommand)),
                missing,
            )
        }
    }

    @Test
    fun aRowsBindActionRoundTripsBackToTheExactEditItNamed() {
        val screen = StudyKeybindingScreen.of(StudyKeybindings.DEFAULT, KeyboardPlatform.LINUX)
        val candidate = screen.row(StudyCommand.GRADE_PASS)
            .candidates.first { it.stroke == StudyKeystroke(StudyKey.G) }
        val action = candidate.bindAction(StudyCommand.GRADE_PASS)

        assertTrue(action is KaniAction.Settings.Command)
        assertEquals(
            StudyKeybindingEdit.Bind(StudyKeystroke(StudyKey.G), StudyCommand.GRADE_PASS),
            StudyKeybindingCommands.parse(action.id),
        )
        // And a chord's modifiers survive the id, which is the part a flat string gets
        // wrong first.
        val chord = KeystrokeCandidate(
            stroke = StudyKeystroke(StudyKey.Z, ctrl = true, shift = true, alt = true, meta = true),
            label = "",
        ).bindAction(StudyCommand.UNDO)
        assertEquals(
            StudyKeybindingEdit.Bind(
                StudyKeystroke(StudyKey.Z, ctrl = true, shift = true, alt = true, meta = true),
                StudyCommand.UNDO,
            ),
            StudyKeybindingCommands.parse((chord as KaniAction.Settings.Command).id),
        )
    }

    @Test
    fun aRowsUnbindActionNamesTheKeystrokeItWasShownOn() {
        val screen = StudyKeybindingScreen.of(StudyKeybindings.DEFAULT, KeyboardPlatform.MACOS)
        val bound = screen.row(StudyCommand.UNDO).bound.first { it.stroke.meta }
        val action = bound.unbindAction

        assertTrue(action is KaniAction.Settings.Command)
        assertEquals(
            StudyKeybindingEdit.Unbind(StudyKeystroke(StudyKey.Z, meta = true)),
            StudyKeybindingCommands.parse(action.id),
        )
    }

    @Test
    fun anEditIdThisBuildCannotReadIsIgnoredRatherThanGuessedAt() {
        // Fail-closed on purpose: a misread keybinding edit rebinds a grade key, and the
        // user finds out by mis-grading a card.
        val unreadable = listOf(
            "",
            "settings.something_else",
            // A command this build dropped, and a key it does not have.
            "study_keybindings.bind:grade_hard:3",
            "study_keybindings.bind:grade_pass:escape",
            // Structurally wrong: missing and extra fields.
            "study_keybindings.bind:grade_pass",
            "study_keybindings.bind:grade_pass:3:extra",
            "study_keybindings.unbind",
            "study_keybindings.unbind:3:extra",
            "study_keybindings.rebind:grade_pass:3",
        )
        for (id in unreadable) {
            assertNull(StudyKeybindingCommands.parse(id), id)
        }
        assertNotNull(StudyKeybindingCommands.parse(StudyKeybindingCommands.RESET))
    }

    @Test
    fun applyingAnEditReturnsTheNewSetAndNullWhenNothingWouldChange() {
        val bind = StudyKeybindingEdit.Bind(StudyKeystroke(StudyKey.G), StudyCommand.GRADE_PASS)
        val bound = StudyKeybindingCommands.apply(bind, StudyKeybindings.DEFAULT, KeyboardPlatform.LINUX)
        assertNotNull(bound)
        assertEquals(StudyCommand.GRADE_PASS, bound.commandFor(StudyKeystroke(StudyKey.G)))

        // Null means "do not persist": either the edit was refused, or it was already the
        // state. Writing on a no-op would churn the settings file on every screen open.
        assertNull(
            StudyKeybindingCommands.apply(
                StudyKeybindingEdit.Bind(StudyKeystroke(StudyKey.DIGIT_1), StudyCommand.GRADE_PASS),
                StudyKeybindings.DEFAULT,
                KeyboardPlatform.LINUX,
            ),
            "a conflicting bind must not persist",
        )
        assertNull(
            StudyKeybindingCommands.apply(
                StudyKeybindingEdit.Bind(StudyKeystroke(StudyKey.DIGIT_1), StudyCommand.GRADE_FAIL),
                StudyKeybindings.DEFAULT,
                KeyboardPlatform.LINUX,
            ),
            "re-binding a key to the command it already has changes nothing",
        )
        assertNull(
            StudyKeybindingCommands.apply(
                StudyKeybindingEdit.Unbind(StudyKeystroke(StudyKey.G)),
                StudyKeybindings.DEFAULT,
                KeyboardPlatform.LINUX,
            ),
            "unbinding a key nothing holds changes nothing",
        )
        assertNull(
            StudyKeybindingCommands.apply(
                StudyKeybindingEdit.Reset,
                StudyKeybindings.DEFAULT,
                KeyboardPlatform.LINUX,
            ),
            "resetting an already-default set changes nothing",
        )
        assertEquals(
            StudyKeybindings.DEFAULT.bindings,
            StudyKeybindingCommands.apply(
                StudyKeybindingEdit.Reset,
                StudyKeybindings(emptyMap()),
                KeyboardPlatform.LINUX,
            )?.bindings,
        )
    }

    @Test
    fun aPlatformReservedKeyIsRefusedByApplyAndNotOnlyGreyedInTheEditor() {
        // The editor's disabled state is a courtesy; this is the gate. A host that
        // dispatched the id anyway must still not be able to store a dead binding.
        assertNull(
            StudyKeybindingCommands.apply(
                StudyKeybindingEdit.Bind(StudyKeystroke(StudyKey.Q, meta = true), StudyCommand.UNDO),
                StudyKeybindings.DEFAULT,
                KeyboardPlatform.MACOS,
            ),
        )
        // And the same chord is storable where no platform claims it.
        assertNotNull(
            StudyKeybindingCommands.apply(
                StudyKeybindingEdit.Bind(StudyKeystroke(StudyKey.Q, ctrl = true), StudyCommand.UNDO),
                StudyKeybindings.DEFAULT,
                KeyboardPlatform.MACOS,
            ),
        )
    }

    @Test
    fun anEditedSetSurvivesStorageAndThenDrivesTheKeyboardPolicy() {
        // The whole path in one test: edit, encode, store, decode, press.
        val edited = StudyKeybindingCommands.apply(
            StudyKeybindingEdit.Bind(StudyKeystroke(StudyKey.G), StudyCommand.GRADE_PASS),
            StudyKeybindings.DEFAULT,
            KeyboardPlatform.LINUX,
        )
        assertNotNull(edited)
        val restored = StudyKeybindingsCodec.decode(StudyKeybindingsCodec.encode(edited))
        val session = StudySession(
            state = StudySessionState.CARD,
            card = StudyCard.Flashcard(
                subject = "脱",
                prompt = UiText.Literal("脱"),
                answer = UiText.Literal("take off"),
                pass = StudyGradeAction(UiText.EMPTY, "good"),
                fail = StudyGradeAction(UiText.EMPTY, "again"),
            ),
        )
        assertEquals(
            KaniAction.Study.Grade(rating = "good"),
            StudyKeyboardPolicy.actionFor(
                press = StudyKeyPress(StudyKeystroke(StudyKey.G)),
                session = session,
                context = StudyInputContext(answerRevealed = true),
                bindings = restored,
            ),
        )
        // The editor read of the stored set shows the new key on the row it was bound to.
        assertEquals(
            "3, Numpad 3, P, G",
            StudyKeybindingScreen.of(restored, KeyboardPlatform.LINUX)
                .row(StudyCommand.GRADE_PASS).acceleratorLabel,
        )
    }
}
