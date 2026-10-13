package dev.bee.kanjianki.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.MenuBar
import dev.bee.kanjianki.hostpresentation.DesktopMenuBar
import dev.bee.kanjianki.presentation.KaniAction

/**
 * The window's native menu bar, from a [DesktopMenuBar].
 *
 * Everything user-visible about the menu — its titles, its items, their order, their
 * accelerators, and whether each one can be chosen — is decided by `DesktopMenuModel` in
 * `:host-presentation` and asserted there without a window. This is only the AWT
 * plumbing, which is why it is this short: a rule written here would be a rule no test
 * could reach.
 *
 * Two deliberate omissions:
 *
 * - **No `shortcut =`.** The accelerator is printed into the item's text — `Pass  (3)` —
 *   rather than registered with the menu. The Study surface's own handler already owns
 *   those keys; a menu shortcut for the same key would be a second handler for one press,
 *   and on a grade key that means one keystroke committing two reviews.
 * - **No Quit item.** The window's close button and the platform's own quit path already
 *   run [DesktopStartup]'s shutdown; a menu entry that bypassed the window's
 *   `onCloseRequest` would be a second exit path with different teardown.
 */
@Composable
internal fun FrameWindowScope.KaniMenuBar(
    bar: DesktopMenuBar,
    dispatch: (KaniAction) -> Unit,
) {
    MenuBar {
        for (menu in bar.menus) {
            Menu(text = menu.label) {
                for (item in menu.items) {
                    if (item.startsGroup) {
                        Separator()
                    }
                    // The action is the enabled flag: a null action cannot be dispatched
                    // and is drawn greyed, so the menu can never show a live item that
                    // does nothing.
                    val action = item.action
                    val onClick = { action?.let(dispatch); Unit }
                    val checked = item.checked
                    if (checked == null) {
                        // An action rather than a place: Back and the Study commands get no
                        // selection marker, because "not the current screen" would be a
                        // misleading thing to draw beside them.
                        Item(text = item.displayLabel, enabled = item.enabled, onClick = onClick)
                    } else {
                        CheckboxItem(
                            text = item.displayLabel,
                            checked = checked,
                            // The mark reports where the user is; it is not a control. The
                            // change handler navigates rather than toggling, and choosing
                            // the marked item is the no-op the mark promises.
                            onCheckedChange = { onClick() },
                        )
                    }
                }
            }
        }
    }
}
