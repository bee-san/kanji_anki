package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the keybinding editor and its storage owe a user who remaps a key.
 *
 * Three claims, each with a way to be wrong that a screen test would not catch: an edit
 * either applies or is refused with a reason (never applied halfway), a stored string
 * round-trips or falls open to a *coherent* set, and a label reads the way it reads in
 * every other application on that platform.
 */
class StudyKeybindingSettingsTest {
    @Test
    fun aChordReadsTheWayItsPlatformWritesIt() {
        val undo = StudyKeystroke(StudyKey.Z, ctrl = true)
        assertEquals("Ctrl+Z", undo.label(KeyboardPlatform.WINDOWS))
        assertEquals("Ctrl+Z", undo.label(KeyboardPlatform.LINUX))
        // macOS writes glyphs with no separator, in Apple's own modifier order.
        assertEquals("⌃Z", undo.label(KeyboardPlatform.MACOS))
        val command = StudyKeystroke(StudyKey.Z, meta = true)
        assertEquals("⌘Z", command.label(KeyboardPlatform.MACOS))
        // Showing a Windows user "Meta" would name a key that is not on the keyboard.
        assertEquals("Super+Z", command.label(KeyboardPlatform.WINDOWS))
        assertEquals(
            "⌃⌥⇧⌘Z",
            StudyKeystroke(StudyKey.Z, ctrl = true, shift = true, alt = true, meta = true)
                .label(KeyboardPlatform.MACOS),
        )
        assertEquals(
            "Ctrl+Alt+Shift+Super+Z",
            StudyKeystroke(StudyKey.Z, ctrl = true, shift = true, alt = true, meta = true)
                .label(KeyboardPlatform.WINDOWS),
        )
    }

    @Test
    fun everyBindableKeyHasAReadableNameOnEveryPlatform() {
        // A row labelled "NUMPAD_3" is a leaked identifier; a blank one is worse.
        for (platform in KeyboardPlatform.entries) {
            for (key in StudyKey.entries) {
                val label = StudyKeystroke(key).label(platform)
                assertTrue(label.isNotBlank(), "$key on $platform")
                assertFalse(label.contains('_'), "$key on $platform: $label")
            }
        }
        assertEquals("Space", StudyKeystroke(StudyKey.SPACE).label(KeyboardPlatform.LINUX))
        assertEquals("Numpad Enter", StudyKeystroke(StudyKey.NUMPAD_ENTER).label(KeyboardPlatform.LINUX))
        assertEquals("Numpad 3", StudyKeystroke(StudyKey.NUMPAD_3).label(KeyboardPlatform.LINUX))
        assertEquals("3", StudyKeystroke(StudyKey.DIGIT_3).label(KeyboardPlatform.LINUX))
        assertEquals("P", StudyKeystroke(StudyKey.P).label(KeyboardPlatform.LINUX))
    }

    @Test
    fun theHostsKeyboardConventionsAreReadFromTheOsNameAndDefaultToCtrl() {
        assertEquals(KeyboardPlatform.WINDOWS, KeyboardPlatform.of("Windows 11"))
        assertEquals(KeyboardPlatform.MACOS, KeyboardPlatform.of("Mac OS X"))
        assertEquals(KeyboardPlatform.MACOS, KeyboardPlatform.of("Darwin"))
        assertEquals(KeyboardPlatform.LINUX, KeyboardPlatform.of("Linux"))
        // Case and stray whitespace are how a real `os.name` arrives on some JVMs.
        assertEquals(KeyboardPlatform.MACOS, KeyboardPlatform.of("  mac os x  "))
        // An unknown or absent OS falls to Ctrl-primary notation with no reserved chord
        // set, which is the safe answer: it names keys that exist everywhere, and it
        // cannot advertise `⌘` on a machine that has no Command key.
        assertEquals(KeyboardPlatform.LINUX, KeyboardPlatform.of("Haiku"))
        assertEquals(KeyboardPlatform.LINUX, KeyboardPlatform.of(null))
        assertEquals(KeyboardPlatform.LINUX, KeyboardPlatform.of(""))
    }

    @Test
    fun bindingAKeyAnotherCommandHoldsIsRefusedWithTheCommandThatHoldsIt() {
        // The conflict is reported rather than overwritten, so the editor can say which
        // command would lose the key instead of silently taking it away.
        val issue = StudyKeybindingEditor.issueFor(
            stroke = StudyKeystroke(StudyKey.DIGIT_1),
            command = StudyCommand.GRADE_PASS,
            bindings = StudyKeybindings.DEFAULT,
            platform = KeyboardPlatform.LINUX,
        )
        assertEquals(
            StudyKeybindingIssue.Conflict(StudyKeystroke(StudyKey.DIGIT_1), StudyCommand.GRADE_FAIL),
            issue,
        )
        assertNull(
            StudyKeybindingEditor.bind(
                stroke = StudyKeystroke(StudyKey.DIGIT_1),
                command = StudyCommand.GRADE_PASS,
                bindings = StudyKeybindings.DEFAULT,
                platform = KeyboardPlatform.LINUX,
            ),
            "a refused bind must not produce a set",
        )
    }

    @Test
    fun rebindingAKeyToTheCommandItAlreadyHasIsNotAConflict() {
        // Otherwise the editor refuses an edit that changes nothing, which reads as a
        // bug to the user re-picking the key already shown on the row.
        assertNull(
            StudyKeybindingEditor.issueFor(
                stroke = StudyKeystroke(StudyKey.DIGIT_1),
                command = StudyCommand.GRADE_FAIL,
                bindings = StudyKeybindings.DEFAULT,
                platform = KeyboardPlatform.LINUX,
            ),
        )
        assertEquals(
            StudyKeybindings.DEFAULT.bindings,
            StudyKeybindingEditor.bind(
                stroke = StudyKeystroke(StudyKey.DIGIT_1),
                command = StudyCommand.GRADE_FAIL,
                bindings = StudyKeybindings.DEFAULT,
                platform = KeyboardPlatform.LINUX,
            )?.bindings,
        )
    }

    @Test
    fun anOsShortcutIsRefusedBecauseItWouldNeverReachTheApp() {
        // A binding the platform swallows first reads to the user as Kani ignoring the
        // key, so it is refused at the editor rather than accepted and dead.
        assertEquals(
            StudyKeybindingIssue.Reserved(StudyKeystroke(StudyKey.C, ctrl = true), "Copy"),
            StudyKeybindingEditor.issueFor(
                stroke = StudyKeystroke(StudyKey.C, ctrl = true),
                command = StudyCommand.GRADE_PASS,
                bindings = StudyKeybindings.DEFAULT,
                platform = KeyboardPlatform.LINUX,
            ),
        )
        // Reserved outranks conflict: the platform's claim is not something the editor
        // can resolve by unbinding a Kani command.
        assertEquals(
            StudyKeybindingIssue.Reserved(StudyKeystroke(StudyKey.Q, meta = true), "Quit"),
            StudyKeybindingEditor.issueFor(
                stroke = StudyKeystroke(StudyKey.Q, meta = true),
                command = StudyCommand.UNDO,
                bindings = StudyKeybindings.DEFAULT,
                platform = KeyboardPlatform.MACOS,
            ),
        )
    }

    @Test
    fun eachPlatformReservesItsOwnPrimaryModifierAndNotTheOthers() {
        val ctrlC = StudyKeystroke(StudyKey.C, ctrl = true)
        val cmdC = StudyKeystroke(StudyKey.C, meta = true)
        // Copy is Ctrl+C on Windows/Linux and ⌘C on macOS — and the *other* chord is
        // free on each, because nothing on that platform claims it.
        assertEquals("Copy", StudyKeybindingEditor.reservedFor(ctrlC, KeyboardPlatform.WINDOWS))
        assertNull(StudyKeybindingEditor.reservedFor(cmdC, KeyboardPlatform.WINDOWS))
        assertEquals("Copy", StudyKeybindingEditor.reservedFor(cmdC, KeyboardPlatform.MACOS))
        assertNull(StudyKeybindingEditor.reservedFor(ctrlC, KeyboardPlatform.MACOS))
        // The desktop shells take Super chords; macOS has no equivalent claim.
        val superL = StudyKeystroke(StudyKey.L, meta = true)
        assertEquals("Lock screen", StudyKeybindingEditor.reservedFor(superL, KeyboardPlatform.LINUX))
        assertEquals("Lock screen", StudyKeybindingEditor.reservedFor(superL, KeyboardPlatform.WINDOWS))
        assertNull(StudyKeybindingEditor.reservedFor(superL, KeyboardPlatform.MACOS))
    }

    @Test
    fun addingAnotherModifierLeavesAChordNoPlatformClaims() {
        // Ctrl+Shift+C is not Copy. Refusing it would shrink the bindable space for no
        // reason, so the list matches only the platform's primary modifier held alone.
        for (platform in KeyboardPlatform.entries) {
            assertNull(
                StudyKeybindingEditor.reservedFor(
                    StudyKeystroke(StudyKey.C, ctrl = true, shift = true),
                    platform,
                ),
                platform.name,
            )
            assertNull(
                StudyKeybindingEditor.reservedFor(
                    StudyKeystroke(StudyKey.C, meta = true, alt = true),
                    platform,
                ),
                platform.name,
            )
        }
    }

    @Test
    fun aBareKeyIsNeverReservedBecauseItBelongsToTheFocusedWindow() {
        for (platform in KeyboardPlatform.entries) {
            for (key in StudyKey.entries) {
                assertNull(StudyKeybindingEditor.reservedFor(StudyKeystroke(key), platform), "$key")
            }
        }
    }

    @Test
    fun noReviewedDefaultIsReservedOnAnyPlatformKaniShipsTo() {
        // The gate on the reserved list: if it ever grows to cover a default, that
        // default is dead on that platform and the editor refuses to restore it.
        for (platform in KeyboardPlatform.entries) {
            for (stroke in StudyKeybindings.DEFAULT.bindings.keys) {
                assertNull(
                    StudyKeybindingEditor.reservedFor(stroke, platform),
                    "${stroke.label(platform)} on $platform",
                )
            }
        }
    }

    @Test
    fun anAcceptedBindKeepsTheCommandsOtherKeysAndTheRestOfTheSet() {
        val bound = StudyKeybindingEditor.bind(
            stroke = StudyKeystroke(StudyKey.G),
            command = StudyCommand.GRADE_PASS,
            bindings = StudyKeybindings.DEFAULT,
            platform = KeyboardPlatform.LINUX,
        )
        assertNotNull(bound)
        assertEquals(StudyCommand.GRADE_PASS, bound.commandFor(StudyKeystroke(StudyKey.G)))
        // Remapping is additive per keystroke: a command may hold several keys, so
        // adding G must not silently drop 3 or P.
        assertTrue(StudyKeystroke(StudyKey.DIGIT_3) in bound.bindings)
        assertTrue(StudyKeystroke(StudyKey.P) in bound.bindings)
        assertEquals(
            StudyKeybindings.DEFAULT.bindings.size + 1,
            bound.bindings.size,
        )
        // And the original is untouched, so a host can show a pending edit safely.
        assertNull(StudyKeybindings.DEFAULT.commandFor(StudyKeystroke(StudyKey.G)))
    }

    @Test
    fun unbindingLeavesACommandReachableOnlyByPointerWhichIsAllowed() {
        var bindings = StudyKeybindings.DEFAULT
        for (stroke in StudyKeybindings.DEFAULT.strokesFor(StudyCommand.GRADE_PASS)) {
            bindings = StudyKeybindingEditor.unbind(stroke, bindings)
        }
        assertEquals(emptyList(), bindings.strokesFor(StudyCommand.GRADE_PASS))
        // Every other command is untouched, and the buttons still pass the card — the
        // editor's job is to show this state, not to prevent it.
        assertEquals(
            StudyKeybindings.DEFAULT.strokesFor(StudyCommand.GRADE_FAIL),
            bindings.strokesFor(StudyCommand.GRADE_FAIL),
        )
        // Unbinding a key nothing holds changes nothing rather than failing.
        assertEquals(bindings.bindings, StudyKeybindingEditor.unbind(StudyKeystroke(StudyKey.G), bindings).bindings)
    }

    @Test
    fun resetRestoresExactlyTheReviewedDefaults() {
        assertEquals(StudyKeybindings.DEFAULT.bindings, StudyKeybindingEditor.resetToDefaults().bindings)
    }

    @Test
    fun everyStoredSetRoundTripsIncludingTheDefaultsAndEveryModifier() {
        assertEquals(
            StudyKeybindings.DEFAULT.bindings,
            StudyKeybindingsCodec.decode(StudyKeybindingsCodec.encode(StudyKeybindings.DEFAULT)).bindings,
        )
        // Every key and every modifier combination, so no shape is stored unreadably.
        val everyKey = StudyKeybindings(
            StudyKey.entries.associate { StudyKeystroke(it) to StudyCommand.PRIMARY },
        )
        assertEquals(
            everyKey.bindings,
            StudyKeybindingsCodec.decode(StudyKeybindingsCodec.encode(everyKey)).bindings,
        )
        val chords = StudyKeybindings(
            linkedMapOf(
                StudyKeystroke(StudyKey.G, ctrl = true) to StudyCommand.UNDO,
                StudyKeystroke(StudyKey.G, shift = true) to StudyCommand.PRIMARY,
                StudyKeystroke(StudyKey.G, alt = true) to StudyCommand.GRADE_PASS,
                StudyKeystroke(StudyKey.G, meta = true) to StudyCommand.GRADE_FAIL,
                StudyKeystroke(StudyKey.H, ctrl = true, shift = true, alt = true, meta = true) to
                    StudyCommand.UNDO,
            ),
        )
        assertEquals(
            chords.bindings,
            StudyKeybindingsCodec.decode(StudyKeybindingsCodec.encode(chords)).bindings,
        )
    }

    @Test
    fun theStoredFormIsFlatAndOrderedSoADiagnosticsDumpIsReadable() {
        assertEquals(
            "3>grade_pass;ctrl+alt+shift+meta+z>undo",
            StudyKeybindingsCodec.encode(
                StudyKeybindings(
                    linkedMapOf(
                        StudyKeystroke(StudyKey.DIGIT_3) to StudyCommand.GRADE_PASS,
                        StudyKeystroke(StudyKey.Z, ctrl = true, shift = true, alt = true, meta = true) to
                            StudyCommand.UNDO,
                    ),
                ),
            ),
        )
    }

    @Test
    fun nothingStoredMeansTheReviewedDefaults() {
        assertEquals(StudyKeybindings.DEFAULT.bindings, StudyKeybindingsCodec.decode(null).bindings)
        assertNull(StudyKeybindingsCodec.parse(null))
    }

    @Test
    fun aDeliberatelyEmptySetIsKeptRatherThanResetOnEveryLaunch() {
        // A user who unbound every key chose that; restoring the defaults next launch
        // would quietly undo it. Distinguishable from "nothing stored", which is null.
        assertEquals(emptyMap(), StudyKeybindingsCodec.parse("")?.bindings)
        assertEquals(emptyMap(), StudyKeybindingsCodec.parse("   ")?.bindings)
        assertEquals(emptyMap(), StudyKeybindingsCodec.decode("").bindings)
        assertEquals("", StudyKeybindingsCodec.encode(StudyKeybindings(emptyMap())))
    }

    @Test
    fun malformedStoredStateFailsOpenToTheWholeDefaultSetNotAPartialOne() {
        // Per-entry recovery is the dangerous outcome: if the Pass entry is the corrupt
        // one and Fail survives, the user studies with a keyboard that can only fail
        // cards. So one bad entry rejects the string, and the defaults are coherent.
        val malformed = listOf(
            "3>grade_pass;garbage",
            "3>grade_pass;>undo",
            "3>grade_pass;z>",
            // An unknown command: a stored binding for one this build dropped.
            "3>grade_pass;z>grade_hard",
            // An unknown key: a stored binding from a build with more keys.
            "3>grade_pass;escape>undo",
            "3>grade_pass;hyper+z>undo",
            // A repeated modifier, and a repeated keystroke: a writer disagreeing with
            // itself about what one key does.
            "ctrl+ctrl+z>undo",
            "3>grade_pass;3>grade_fail",
        )
        for (stored in malformed) {
            assertNull(StudyKeybindingsCodec.parse(stored), stored)
            assertEquals(StudyKeybindings.DEFAULT.bindings, StudyKeybindingsCodec.decode(stored).bindings, stored)
        }
    }

    @Test
    fun storedStateIsReadCaseAndWhitespaceInsensitivelyBecauseItIsHandEditable() {
        // It lands in a plain settings store and in diagnostics dumps, so a
        // hand-corrected value should not be rejected on capitalization alone.
        assertEquals(
            StudyKeybindings.DEFAULT.bindings,
            StudyKeybindingsCodec.decode(
                StudyKeybindingsCodec.encode(StudyKeybindings.DEFAULT).uppercase(),
            ).bindings,
        )
        assertEquals(
            mapOf(StudyKeystroke(StudyKey.Z, ctrl = true) to StudyCommand.UNDO),
            StudyKeybindingsCodec.parse("  CTRL+Z>UNDO  ")?.bindings,
        )
    }

    @Test
    fun aDecodedSetDispatchesTheSameGuardedActionsTheDefaultsDo() {
        // The end of the storage path: a set that survived a round trip still drives the
        // policy, so this is not merely a string test.
        val bindings = StudyKeybindingsCodec.decode(StudyKeybindingsCodec.encode(StudyKeybindings.DEFAULT))
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
                press = StudyKeyPress(StudyKeystroke(StudyKey.DIGIT_3)),
                session = session,
                context = StudyInputContext(answerRevealed = true),
                bindings = bindings,
            ),
        )
    }
}
