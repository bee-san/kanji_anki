package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniTab
import dev.bee.kanjianki.presentation.KeyboardPlatform
import dev.bee.kanjianki.presentation.SettingsSection
import dev.bee.kanjianki.presentation.ShellState
import dev.bee.kanjianki.presentation.StudyCard
import dev.bee.kanjianki.presentation.StudyCommand
import dev.bee.kanjianki.presentation.StudyFeedback
import dev.bee.kanjianki.presentation.StudyFeedbackPhase
import dev.bee.kanjianki.presentation.StudyGradeAction
import dev.bee.kanjianki.presentation.StudyInputContext
import dev.bee.kanjianki.presentation.StudyKey
import dev.bee.kanjianki.presentation.StudyKeyPress
import dev.bee.kanjianki.presentation.StudyKeybindingCommands
import dev.bee.kanjianki.presentation.StudyKeybindingEdit
import dev.bee.kanjianki.presentation.StudyKeybindings
import dev.bee.kanjianki.presentation.StudyKeyboardPolicy
import dev.bee.kanjianki.presentation.StudyKeystroke
import dev.bee.kanjianki.presentation.StudySession
import dev.bee.kanjianki.presentation.StudySessionState
import dev.bee.kanjianki.presentation.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the desktop menu bar owes the window that renders it.
 *
 * The menu is the surface that advertises the keyboard, so the assertions that matter are
 * about agreement rather than layout: that an item's accelerator is the key the policy
 * actually honours, that an item's action is one a visible control also dispatches, and
 * that "greyed out" and "does nothing" are never separable.
 */
class DesktopMenuModelTest {
    @Test
    fun theBarIsTheTwoMenusADesktopWindowNeeds() {
        val bar = DesktopMenuModel.bar(shell())

        assertEquals(listOf("Go", "Study"), bar.menus.map { it.label })
        // Nothing is nameless, and no menu is empty — an empty menu is a menu title that
        // opens onto nothing.
        assertTrue(bar.menus.all { it.items.isNotEmpty() })
        assertTrue(bar.menus.flatMap { it.items }.all { it.label.isNotBlank() })
        // Labels within a menu are distinct, so `menu.item(label)` and a host's own
        // keying by label cannot silently pick the wrong row.
        for (menu in bar.menus) {
            assertEquals(menu.items.size, menu.items.map { it.label }.distinct().size)
        }
    }

    @Test
    fun goListsTheTabsAndTheHomeDestinationsUnderTheirOwnNames() {
        val go = DesktopMenuModel.bar(shell()).menu("Go")

        assertEquals(
            listOf(
                "Home", "Study", "Stats", "Settings",
                "Browse Kanji", "Focus queue", "Recent mistakes", "Games", "Missing Kanji",
                "Back",
            ),
            go.items.map { it.label },
        )
        // The tabs dispatch tab selection, not an Open: selecting a tab resets that tab's
        // stack, and Open would push the root on top of wherever the user was.
        assertEquals(KaniAction.Navigation.SelectTab(KaniTab.STATS), go.item("Stats").action)
        assertEquals(
            KaniAction.Navigation.Open(KaniDestination.FocusQueue),
            go.item("Focus queue").action,
        )
        // Separators group the tabs, the destinations, and Back; the tabs open the bar.
        assertEquals(
            listOf("Browse Kanji", "Back"),
            go.items.filter { it.startsGroup }.map { it.label },
        )
    }

    @Test
    fun theCurrentScreenIsMarkedAndTheMarkSurvivesAScreensOwnState() {
        val onStats = DesktopMenuModel.bar(shell(KaniDestination.Stats)).menu("Go")
        assertEquals(listOf("Stats"), onStats.items.filter { it.checked == true }.map { it.label })

        // Browse stays marked once the user has typed into it: comparing destinations by
        // value would drop the mark the moment a query existed.
        val searching = shell(KaniDestination.Browse(query = "脱", showSuspended = true))
        assertEquals(
            listOf("Browse Kanji"),
            DesktopMenuModel.bar(searching).menu("Go").items.filter { it.checked == true }.map { it.label },
        )
        // A Settings subpage marks nothing: the mark means "choosing this goes nowhere",
        // and choosing Settings from the keybinding editor really does move — up to the
        // Settings root. The rail's tab highlight is a different question.
        val keybindings = shell(KaniDestination.Settings(SettingsSection.KEYBINDINGS))
        assertEquals(
            emptyList<String>(),
            DesktopMenuModel.bar(keybindings).menu("Go").items.filter { it.checked == true }.map { it.label },
        )
        // Settings' own root is marked, because that is where the item leads.
        assertEquals(
            listOf("Settings"),
            DesktopMenuModel.bar(shell(KaniDestination.Settings()))
                .menu("Go").items.filter { it.checked == true }.map { it.label },
        )
    }

    @Test
    fun backIsInertAtTheRootAndDispatchesTheShellsOwnBackAnywhereElse() {
        // At Home with nothing behind it there is nowhere to go, and an enabled Back that
        // does nothing reads as broken.
        val root = DesktopMenuModel.bar(shell()).menu("Go").item("Back")
        assertNull(root.action)
        assertFalse(root.enabled)

        val nested = DesktopMenuModel.bar(shell(KaniDestination.Stats)).menu("Go").item("Back")
        assertEquals(KaniAction.Navigation.Back, nested.action)
        assertTrue(nested.enabled)

        // Back is an action, not a place, so it carries no selection mark at all — an
        // unmarked-but-markable row would read as "not the current screen", which is not a
        // sensible thing to say about going back.
        assertNull(root.checked)
        assertNull(nested.checked)
        val study = DesktopMenuModel.bar(shell()).menu("Study")
        assertTrue(study.items.all { it.checked == null })
    }

    @Test
    fun studyNamesTheCommandsTheCardsButtonsNameAndPrintsTheirKeys() {
        val study = DesktopMenuModel.bar(shell(KaniDestination.Study)).menu("Study")

        assertEquals(
            listOf("Show answer / continue", "Pass", "Fail", "Undo", "Keyboard shortcuts"),
            study.items.map { it.label },
        )
        // One accelerator per command, and it is the Anki-canonical key the reviewed
        // defaults list first — not whichever key happens to sort first.
        assertEquals("Space", study.item("Show answer / continue").accelerator)
        assertEquals("3", study.item("Pass").accelerator)
        assertEquals("1", study.item("Fail").accelerator)
        assertEquals("Ctrl+Z", study.item("Undo").accelerator)
        // The editor is reachable from the menu that advertises the keys, and it is the
        // only Study item that is not a card action.
        assertEquals(
            KaniAction.Navigation.Open(KaniDestination.Settings(SettingsSection.KEYBINDINGS)),
            study.item("Keyboard shortcuts").action,
        )
        assertTrue(study.item("Keyboard shortcuts").startsGroup)
        assertNull(study.item("Keyboard shortcuts").accelerator)
    }

    @Test
    fun theAcceleratorIsPrintedIntoTheLabelRatherThanClaimedAsASecondShortcut() {
        // A real menu-level shortcut on a grade key would be a second handler for one
        // press, and on `3` that means one keystroke committing two reviews. The menu
        // advertises the key in its text and leaves the handling to the Study surface.
        val study = DesktopMenuModel.bar(shell()).menu("Study")
        assertEquals("Pass  (3)", study.item("Pass").displayLabel)
        assertEquals("Undo  (Ctrl+Z)", study.item("Undo").displayLabel)
        // An item with no key reads as itself, with no empty bracket.
        assertEquals("Keyboard shortcuts", study.item("Keyboard shortcuts").displayLabel)
        assertEquals("Home", DesktopMenuModel.bar(shell()).menu("Go").item("Home").displayLabel)
    }

    @Test
    fun anAcceleratorIsNamedTheWayTheRunningPlatformWritesIt() {
        val mac = DesktopMenuModel.bar(shell(), platform = KeyboardPlatform.MACOS)
        assertEquals("⌃Z", mac.menu("Study").item("Undo").accelerator)
        val windows = DesktopMenuModel.bar(shell(), platform = KeyboardPlatform.WINDOWS)
        assertEquals("Ctrl+Z", windows.menu("Study").item("Undo").accelerator)
    }

    @Test
    fun aRemapMovesTheAdvertisedKeyAndAnUnboundCommandAdvertisesNone() {
        val remapped = StudyKeybindingCommands.apply(
            StudyKeybindingEdit.Bind(StudyKeystroke(StudyKey.G), StudyCommand.GRADE_PASS),
            StudyKeybindings(
                StudyKeybindings.DEFAULT.bindings
                    .filterValues { it != StudyCommand.GRADE_PASS },
            ),
            KeyboardPlatform.LINUX,
        )
        assertNotNull("binding a free key must produce storable state", remapped)

        val study = DesktopMenuModel.bar(shell(), bindings = remapped!!).menu("Study")
        assertEquals("G", study.item("Pass").accelerator)
        // Fail is untouched by a Pass remap.
        assertEquals("1", study.item("Fail").accelerator)

        // A command with no key at all prints no accelerator rather than a stale one; the
        // item stays listed because the action is still available at the button.
        val unbound = DesktopMenuModel.bar(shell(), bindings = StudyKeybindings(emptyMap()))
            .menu("Study")
        assertTrue(unbound.items.dropLast(1).all { it.accelerator == null })
        assertEquals(
            listOf("Show answer / continue", "Pass", "Fail", "Undo", "Keyboard shortcuts"),
            unbound.items.map { it.label },
        )
    }

    @Test
    fun everyAdvertisedAcceleratorIsAKeyThePolicyActuallyHonours() {
        // The menu's whole job is teaching the keyboard, so an accelerator the policy
        // would drop is worse than none: the user presses it and nothing happens.
        val session = revealedFlashcard()
        val context = StudyInputContext(answerRevealed = true)
        val bar = DesktopMenuModel.bar(shell(KaniDestination.Study), session = session, context = context)

        for (command in StudyCommand.entries) {
            val item = bar.menu("Study").item(commandLabel(command))
            val stroke = StudyKeybindings.DEFAULT.strokesFor(command).first()
            assertEquals(
                "the key ${item.accelerator} advertises for $command must resolve to it",
                command,
                StudyKeyboardPolicy.commandFor(StudyKeyPress(stroke), context),
            )
            // And the item dispatches exactly what pressing that key dispatches.
            assertEquals(
                item.action,
                StudyKeyboardPolicy.actionFor(StudyKeyPress(stroke), session, context),
            )
        }
    }

    @Test
    fun aStudyItemIsOnlyEnabledWhenTheVisibleCardOffersIt() {
        // Off the Study route, and while the session loads, there is no card: the items
        // stay listed with their keys and simply cannot be chosen.
        val noSession = DesktopMenuModel.bar(shell()).menu("Study")
        for (command in StudyCommand.entries) {
            val item = noSession.item(commandLabel(command))
            assertNull("no session must not dispatch $command", item.action)
            assertFalse(item.enabled)
            assertNotNull("the key is advertised anyway", item.accelerator)
        }

        // Face down: reveal is offered, the grades are not — the menu cannot grade an
        // answer the user has not seen, exactly as the keys cannot.
        val faceDown = DesktopMenuModel.bar(shell(), session = revealedFlashcard()).menu("Study")
        assertEquals(KaniAction.Study.Reveal, faceDown.item("Show answer / continue").action)
        assertNull(faceDown.item("Pass").action)
        assertNull(faceDown.item("Fail").action)

        // Revealed: both grades are the ratings the card itself declares.
        val revealed = DesktopMenuModel.bar(
            shell(),
            session = revealedFlashcard(),
            context = StudyInputContext(answerRevealed = true),
        ).menu("Study")
        assertEquals(KaniAction.Study.Grade(rating = "good"), revealed.item("Pass").action)
        assertEquals(KaniAction.Study.Grade(rating = "again"), revealed.item("Fail").action)
    }

    @Test
    fun undoIsOfferedOnlyWhenTheSessionReportsAReversibleCard() {
        val notUndoable = DesktopMenuModel.bar(shell(), session = revealedFlashcard()).menu("Study")
        assertNull(notUndoable.item("Undo").action)

        val undoable = DesktopMenuModel.bar(
            shell(),
            session = revealedFlashcard().copy(undoable = true),
        ).menu("Study")
        assertEquals(KaniAction.Study.Undo, undoable.item("Undo").action)
    }

    @Test
    fun anAnsweredCardOffersContinueAndNothingElse() {
        val applied = revealedFlashcard().copy(
            feedback = StudyFeedback(phase = StudyFeedbackPhase.APPLIED),
        )
        val study = DesktopMenuModel.bar(
            shell(),
            session = applied,
            context = StudyInputContext(answerRevealed = true),
        ).menu("Study")

        // The primary became Continue, and a second grade is refused here for the same
        // reason it is refused at the button.
        assertEquals(KaniAction.Study.Continue, study.item("Show answer / continue").action)
        assertNull(study.item("Pass").action)
        assertNull(study.item("Fail").action)
    }

    @Test
    fun anOpenModalTakesTheMenusCardActionsAndATypedFieldDoesNot() {
        val session = revealedFlashcard()

        // A dialog owns the screen while it is open, so the menu must not be the way around
        // the guard that stops a card being committed behind it.
        val modal = DesktopMenuModel.bar(
            shell(),
            session = session,
            context = StudyInputContext(answerRevealed = true, modalActive = true),
        ).menu("Study")
        for (command in StudyCommand.entries) {
            assertNull(modal.item(commandLabel(command)).action)
        }

        // A focused answer box and a composing IME claim *typed characters*, which is not
        // what choosing a menu item is: `P` must not pass a card while the user is typing
        // "possible", but Pass on the menu is an explicit choice and stays available.
        val typing = DesktopMenuModel.bar(
            shell(),
            session = session,
            context = StudyInputContext(
                answerRevealed = true,
                textFieldFocused = true,
                imeComposing = true,
            ),
        ).menu("Study")
        assertEquals(KaniAction.Study.Grade(rating = "good"), typing.item("Pass").action)
        // And the key path still refuses the same press, which is the asymmetry this pins.
        assertNull(
            StudyKeyboardPolicy.actionFor(
                press = StudyKeyPress(StudyKeystroke(StudyKey.P)),
                session = session,
                context = StudyInputContext(
                    answerRevealed = true,
                    textFieldFocused = true,
                    imeComposing = true,
                ),
            ),
        )
    }

    @Test
    fun noMenuItemDispatchesAnythingOutsideTheSharedVocabulary() {
        // The menu must not become a third way to act. Every action it can produce is a
        // navigation the rail also dispatches or a study action the card's own button
        // dispatches — nothing host-only, and nothing that reaches a repository.
        val bar = DesktopMenuModel.bar(
            shell(KaniDestination.Study),
            session = revealedFlashcard().copy(undoable = true),
            context = StudyInputContext(answerRevealed = true),
        )
        val actions = bar.menus.flatMap { it.items }.mapNotNull { it.action }
        assertTrue(actions.isNotEmpty())
        assertTrue(
            actions.all { it is KaniAction.Navigation || it is KaniAction.Study },
        )
    }

    private fun commandLabel(command: StudyCommand): String = when (command) {
        StudyCommand.PRIMARY -> "Show answer / continue"
        StudyCommand.GRADE_PASS -> "Pass"
        StudyCommand.GRADE_FAIL -> "Fail"
        StudyCommand.UNDO -> "Undo"
    }

    private fun shell(current: KaniDestination = KaniDestination.Home): ShellState =
        if (current == KaniDestination.Home) {
            ShellState()
        } else {
            ShellState(backStack = listOf(KaniDestination.Home, current))
        }

    private fun revealedFlashcard(): StudySession = StudySession(
        state = StudySessionState.CARD,
        card = StudyCard.Flashcard(
            subject = "脱",
            prompt = UiText.Literal("脱"),
            answer = UiText.Literal("take off"),
            pass = StudyGradeAction(UiText.EMPTY, "good"),
            fail = StudyGradeAction(UiText.EMPTY, "again"),
        ),
    )
}
