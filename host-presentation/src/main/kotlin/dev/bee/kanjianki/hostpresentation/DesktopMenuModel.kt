package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.MenuBarCopy
import dev.bee.kanjianki.core.MissingKanjiTextCopy
import dev.bee.kanjianki.core.NavigationCopy
import dev.bee.kanjianki.core.SettingsKeybindingTextCopy
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniTab
import dev.bee.kanjianki.presentation.KeyboardPlatform
import dev.bee.kanjianki.presentation.SettingsSection
import dev.bee.kanjianki.presentation.ShellState
import dev.bee.kanjianki.presentation.StudyCommand
import dev.bee.kanjianki.presentation.StudyInputContext
import dev.bee.kanjianki.presentation.StudyKeybindingScreen
import dev.bee.kanjianki.presentation.StudyKeybindings
import dev.bee.kanjianki.presentation.StudyKeyboardPolicy
import dev.bee.kanjianki.presentation.StudySession

/**
 * The desktop window's native menu bar, as portable data.
 *
 * A menu bar is a platform expectation on the desktop and it is the only surface that
 * *advertises* the keyboard: a user who never opens Settings still learns that Space
 * reveals and `Ctrl+Z` undoes, because the menu prints the accelerator next to the
 * action. That is the whole reason this exists — the keys already work (Goal 203's
 * command model), and the menu makes them discoverable.
 *
 * Built here, in plain JVM code, rather than inline in the `MenuBar` composable, for the
 * same reason every other host mapping is: the rules worth checking are which items are
 * dispatchable, which accelerator each one shows, and that no item can dispatch anything
 * a visible control would not. None of those need a window to assert.
 *
 * Two invariants shape the types:
 *
 * - **A disabled item carries no action.** [DesktopMenuItem.action] is nullable and
 *   [DesktopMenuItem.enabled] is derived from it, so "greyed out" and "does nothing" are
 *   the same fact rather than two flags that can disagree. A host cannot render an
 *   enabled item that dispatches nothing, or a disabled one that still fires.
 * - **Every action is one the shared vocabulary already has.** Navigation items dispatch
 *   the same [KaniAction.Navigation] the rail and the back affordance dispatch; Study
 *   items dispatch whatever [StudyKeyboardPolicy] resolves for the visible card, which is
 *   by construction what the card's own button dispatches. The menu invents nothing, so
 *   it cannot become a third way to grade.
 */
object DesktopMenuModel {
    /**
     * The whole menu bar for the current shell, session, and bindings.
     *
     * [session] is null off the Study route and while the session is loading, which is
     * exactly when the Study actions must be inert — the items stay listed with their
     * accelerators (so the menu still teaches the keys) and simply carry no action.
     *
     * [context] is what else holds the keyboard, and it is the caller's to report: the
     * grades resolve to nothing until the answer is on screen, so a host that cannot yet
     * observe its own reveal state gets correctly-disabled grade items rather than a menu
     * that can grade a face-down card.
     */
    fun bar(
        shell: ShellState,
        session: StudySession? = null,
        bindings: StudyKeybindings = StudyKeybindings.DEFAULT,
        platform: KeyboardPlatform = KeyboardPlatform.LINUX,
        context: StudyInputContext = StudyInputContext(),
    ): DesktopMenuBar = DesktopMenuBar(
        menus = listOf(
            DesktopMenu(label = MenuBarCopy.goMenuLabel(), items = goItems(shell)),
            DesktopMenu(
                label = MenuBarCopy.studyMenuLabel(),
                items = studyItems(session, bindings, platform, context),
            ),
        ),
    )

    private fun goItems(shell: ShellState): List<DesktopMenuItem> {
        val tabs = KaniTab.entries.map { tab ->
            DesktopMenuItem(
                label = tabLabel(tab),
                action = KaniAction.Navigation.SelectTab(tab),
                checked = isCurrent(shell, tab.root),
            )
        }
        val destinations = listOf(
            HomeTextCopy.browseActionLabel() to KaniDestination.Browse(),
            HomeTextCopy.focusQueueTitle() to KaniDestination.FocusQueue,
            HomeTextCopy.recentMistakesTitle() to KaniDestination.RecentMistakes,
            HomeTextCopy.gamesActionLabel() to KaniDestination.Games,
            MissingKanjiTextCopy.actionLabel() to KaniDestination.MissingKanji,
        ).mapIndexed { index, (label, destination) ->
            DesktopMenuItem(
                label = label,
                action = KaniAction.Navigation.Open(destination),
                checked = isCurrent(shell, destination),
                startsGroup = index == 0,
            )
        }
        val back = DesktopMenuItem(
            label = MenuBarCopy.backLabel(),
            // Null rather than a dispatch the reducer would absorb: at the root of the app
            // there is nowhere to go back to, and an enabled Back that does nothing is
            // indistinguishable from a broken one.
            action = if (shell.canGoBack) KaniAction.Navigation.Back else null,
            startsGroup = true,
        )
        return tabs + destinations + back
    }

    private fun studyItems(
        session: StudySession?,
        bindings: StudyKeybindings,
        platform: KeyboardPlatform,
        context: StudyInputContext,
    ): List<DesktopMenuItem> {
        val screen = StudyKeybindingScreen.of(bindings, platform)
        // A dialog owns the screen while it is open, and a menu that could grade behind it
        // would be a way around the guard that keeps an open modal from committing a card.
        // The other keyboard-precedence rules are not repeated here: a focused text field
        // and a composing IME claim *typed characters*, which is not what choosing a menu
        // item is.
        val claimed = !context.modalActive
        val commands = StudyCommand.entries.map { command ->
            DesktopMenuItem(
                label = SettingsKeybindingTextCopy.commandLabel(command.id),
                // Resolved through the same policy the keyboard uses, so a card that does
                // not offer a grade greys the item out for the same reason the key does
                // nothing — one decision, not a menu-shaped copy of it.
                action = session?.takeIf { claimed }
                    ?.let { StudyKeyboardPolicy.actionFor(command, it, context) },
                // The accelerator is shown whether or not the item is dispatchable: a
                // greyed row that still prints `Ctrl+Z` is how the menu teaches the key.
                accelerator = screen.row(command).menuAccelerator,
            )
        }
        val editor = DesktopMenuItem(
            label = SettingsKeybindingTextCopy.keybindingsTitle(),
            action = KaniAction.Navigation.Open(
                KaniDestination.Settings(SettingsSection.KEYBINDINGS),
            ),
            startsGroup = true,
        )
        return commands + editor
    }

    private fun tabLabel(tab: KaniTab): String = when (tab) {
        KaniTab.HOME -> NavigationCopy.homeLabel()
        KaniTab.STUDY -> NavigationCopy.studyLabel()
        KaniTab.STATS -> NavigationCopy.statsLabel()
        KaniTab.SETTINGS -> NavigationCopy.settingsLabel()
    }

    /**
     * Whether the shell is already showing exactly what an item opens.
     *
     * The mark means "choosing this would go nowhere", which is the only reading that
     * stays true for every item. It is deliberately *not* the navigation rail's tab
     * highlight: on a Settings subpage the rail highlights Settings, but choosing Settings
     * from the menu really does move — to the Settings root — so marking it would promise
     * a no-op that is not one.
     *
     * Compared by [KaniDestination.route] rather than by value, so Browse stays marked
     * whatever the user has typed into it: a mark that vanished once a query was entered
     * would report the wrong screen.
     */
    private fun isCurrent(shell: ShellState, destination: KaniDestination): Boolean =
        shell.current.route == destination.route
}

/** Every menu in the window's menu bar, in bar order. */
data class DesktopMenuBar(val menus: List<DesktopMenu>) {
    /** The menu with a title; used by tests and by a host that renders one menu at a time. */
    fun menu(label: String): DesktopMenu = menus.first { it.label == label }
}

/** One menu's title and its items, in menu order. */
data class DesktopMenu(val label: String, val items: List<DesktopMenuItem>) {
    /** The item with a label; every label in a menu is distinct. */
    fun item(label: String): DesktopMenuItem = items.first { it.label == label }
}

/**
 * One menu item.
 *
 * [startsGroup] asks the host to draw a separator above this item. A flag on the item
 * rather than a list of groups because a menu is rendered as a flat sequence and the
 * grouping is presentation, not structure — and because a host that ignores separators
 * still renders every item in the right order.
 */
data class DesktopMenuItem(
    val label: String,
    val action: KaniAction?,
    val accelerator: String? = null,
    /**
     * Whether this item is the screen now showing, or null when it is not a screen at all.
     *
     * Three states rather than two because a menu holds both kinds of item. Back and the
     * Study commands are actions, not places, so "not the current screen" would be a
     * misleading thing to draw next to them — a host renders a selection marker only where
     * this is non-null, and an action item stays plain.
     */
    val checked: Boolean? = null,
    val startsGroup: Boolean = false,
) {
    /** True when the item can be chosen; false is exactly "carries no action". */
    val enabled: Boolean
        get() = action != null

    /**
     * The item's text with its accelerator, as one string.
     *
     * The accelerator is printed into the label rather than registered as a real menu
     * shortcut. That is the whole point of advertising it here: the key is already handled
     * by the Study surface's own handler, and a menu-level shortcut for the same key would
     * be a *second* handler for one press — which on a grade key means one keystroke
     * committing two reviews. The menu teaches the key; it does not claim it.
     */
    val displayLabel: String
        get() = accelerator?.let { "$label  ($it)" } ?: label
}
