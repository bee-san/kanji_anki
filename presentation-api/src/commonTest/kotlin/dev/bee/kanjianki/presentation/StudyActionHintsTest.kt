package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a visible study control may announce as its key.
 *
 * The assertions that matter are agreement ones: an announced key must be a key the policy
 * would actually honour in that state, because a control that names a key which does
 * nothing is worse for a screen reader user than one that names none.
 */
class StudyActionHintsTest {
    @Test
    fun everyCommandAnnouncesTheAnkiCanonicalKeyInThePlatformsNotation() {
        val hints = StudyActionHints.of(KeyboardPlatform.LINUX)

        assertEquals("Space", hints.accelerator(StudyCommand.PRIMARY))
        assertEquals("3", hints.accelerator(StudyCommand.GRADE_PASS))
        assertEquals("1", hints.accelerator(StudyCommand.GRADE_FAIL))
        assertEquals("Ctrl+Z", hints.accelerator(StudyCommand.UNDO))
        assertEquals(KeyboardPlatform.LINUX, hints.platform)

        // macOS writes the same chord the way its own menus do.
        assertEquals("⌃Z", StudyActionHints.of(KeyboardPlatform.MACOS).accelerator(StudyCommand.UNDO))
    }

    @Test
    fun everyAnnouncedKeyIsOneThePolicyHonoursInThatState() {
        // The whole contract in one loop: for each command, the key the control announces
        // must resolve back to that command through the policy the keyboard uses.
        for (platform in KeyboardPlatform.entries) {
            val hints = StudyActionHints.of(platform)
            for (command in StudyCommand.entries) {
                val announced = hints.accelerator(command)
                val stroke = StudyKeybindings.DEFAULT.strokesFor(command)
                    .first { it.label(platform) == announced }
                assertEquals(
                    command,
                    StudyKeyboardPolicy.commandFor(StudyKeyPress(stroke)),
                    "$platform announces $announced for $command, which must resolve to it",
                )
            }
        }
    }

    @Test
    fun theTypedCardsPrimaryAnnouncesEnterBecauseTheAnswerBoxOwnsSpace() {
        // Space, Enter and NumpadEnter all ask for the primary; with the field focused the
        // first two behave differently and only one of them still submits.
        val typing = StudyActionHints.of(
            KeyboardPlatform.LINUX,
            context = StudyInputContext(textFieldFocused = true),
        )
        assertEquals("Enter", typing.accelerator(StudyCommand.PRIMARY))
        // And the key it names really is honoured there, while the one it dropped is not.
        assertEquals(
            StudyCommand.PRIMARY,
            StudyKeyboardPolicy.commandFor(
                press = StudyKeyPress(StudyKeystroke(StudyKey.ENTER)),
                context = StudyInputContext(textFieldFocused = true),
            ),
        )
        assertNull(
            StudyKeyboardPolicy.commandFor(
                press = StudyKeyPress(StudyKeystroke(StudyKey.SPACE)),
                context = StudyInputContext(textFieldFocused = true),
            ),
        )
        // Undo keeps its chord while typing: `Ctrl+Z` is not text input, and taking undo
        // away from the one card that most needs it would be the wrong reading of the rule.
        assertEquals("Ctrl+Z", typing.accelerator(StudyCommand.UNDO))
        // The grades are bare printable keys, so the field claims all of them and the
        // buttons announce nothing rather than a key that types into the answer.
        assertNull(typing.accelerator(StudyCommand.GRADE_PASS))
        assertNull(typing.accelerator(StudyCommand.GRADE_FAIL))
    }

    @Test
    fun aRemapMovesTheAnnouncementAndAnUnboundCommandAnnouncesNothing() {
        val remapped = StudyKeybindings(
            linkedMapOf(
                StudyKeystroke(StudyKey.G) to StudyCommand.GRADE_PASS,
                StudyKeystroke(StudyKey.DIGIT_1) to StudyCommand.GRADE_FAIL,
            ),
        )
        val hints = StudyActionHints.of(KeyboardPlatform.LINUX, bindings = remapped)

        assertEquals("G", hints.accelerator(StudyCommand.GRADE_PASS))
        assertEquals("1", hints.accelerator(StudyCommand.GRADE_FAIL))
        // Unbound commands announce nothing: the action stays available at the control,
        // and a stale key would be a promise the bindings no longer keep.
        assertNull(hints.accelerator(StudyCommand.PRIMARY))
        assertNull(hints.accelerator(StudyCommand.UNDO))
        assertNull(StudyActionHints.of(KeyboardPlatform.LINUX, bindings = StudyKeybindings(emptyMap()))
            .accelerator(StudyCommand.PRIMARY))
    }

    @Test
    fun aChoiceAnnouncesItsPositionsDigitAndNothingPastTheNinth() {
        val hints = StudyActionHints.of(KeyboardPlatform.LINUX)

        assertEquals("1", hints.choiceAccelerator(1))
        assertEquals("4", hints.choiceAccelerator(4))
        assertEquals("9", hints.choiceAccelerator(9))
        // There is no tenth digit key, and position 0 is not a position.
        assertNull(hints.choiceAccelerator(10))
        assertNull(hints.choiceAccelerator(0))

        // Positional, not bound: `3` picks the third option even after Pass is remapped off
        // it, because a choice card resolves digits ahead of the bindings.
        val remapped = StudyActionHints.of(
            KeyboardPlatform.LINUX,
            bindings = StudyKeybindings(
                StudyKeybindings.DEFAULT.bindings.filterKeys { it.key != StudyKey.DIGIT_3 },
            ),
        )
        assertEquals("3", remapped.choiceAccelerator(3))
    }

    @Test
    fun aTextFieldClaimsPrintableKeysAndLeavesChordsAlone() {
        // The property the policy and the announcement now share, asserted directly so the
        // two cannot drift apart.
        assertEquals(true, StudyKeystroke(StudyKey.SPACE).isClaimedByTextField)
        assertEquals(true, StudyKeystroke(StudyKey.DIGIT_3).isClaimedByTextField)
        assertEquals(false, StudyKeystroke(StudyKey.ENTER).isClaimedByTextField)
        assertEquals(false, StudyKeystroke(StudyKey.Z, ctrl = true).isClaimedByTextField)
    }
}
