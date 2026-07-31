package dev.bee.kanjianki.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.ReminderEligibilityPolicy
import dev.bee.kanjianki.presentation.ContentResult
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniEffect
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.RouteState
import dev.bee.kanjianki.platform.desktop.DesktopClipboardService
import dev.bee.kanjianki.platform.desktop.DesktopExternalNavigator
import dev.bee.kanjianki.shell.KaniShell
import dev.bee.kanjianki.shell.LiteralUiTextResolver
import dev.bee.kanjianki.shell.ShellBackAffordanceMode
import dev.bee.kanjianki.shell.ShellEffectHandler
import dev.bee.kanjianki.shell.ShellRouteContent
import dev.bee.kanjianki.shell.rememberShellCopy
import dev.bee.kanjianki.shell.shellRouteTestTag
import dev.bee.kanjianki.ui.KaniThemeId
import dev.bee.kanjianki.ui.KaniTheme
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The tag the desktop placeholder body renders under, mirroring the shell's. */
internal const val DESKTOP_PLACEHOLDER_TEST_TAG: String = "kani-desktop-placeholder"

/**
 * The shared shell, wired to a live desktop container.
 *
 * Structured so that everything the user sees comes from `:feature-shell` and
 * everything platform-specific is an argument. That is not a style preference: it
 * is the checkable form of Goal 193's claim that both hosts render the same shell
 * states. If a layout, a loading surface, or a failure banner were written here, the
 * claim would quietly stop being true.
 *
 * [ShellBackAffordanceMode.IN_SHELL] is the one deliberate divergence from Android.
 * A desktop window has no system back gesture, so the shell has to draw the button;
 * Android's shell must not, because it already has the gesture and adding a button
 * would change the shipped app's appearance.
 */
@Composable
internal fun DesktopShellScaffold(container: DesktopKaniContainer) {
    val scope = rememberCoroutineScope()
    val provider = remember(container) {
        DesktopProviderProbe.forLoopbackEndpoint(container.secretStore)
    }
    val host = remember(container) {
        DesktopShellHost(
            capabilities = PlatformCapabilities(
                desktopHostCapabilities(persistsSecrets = container.persistsSecrets),
            ),
            loadRoute = { loadDesktopRoute(container, provider) },
        )
    }

    // The reducers are pure and their outputs are plain values, so recomposition
    // needs an explicit signal. A revision counter is the smallest one that works:
    // the alternative, mirroring every reducer output into its own `mutableStateOf`,
    // gives two sources of truth for the same state.
    var revision by remember { mutableStateOf(0) }
    val shellState = remember(revision) { host.shell }
    val routeState = remember(revision) { host.route(shellState.current) }

    val dispatch: (KaniAction) -> Unit = { action ->
        val pending = host.dispatch(action)
        revision++
        if (pending != null) {
            scope.launch {
                host.perform(pending)
                revision++
            }
        }
    }

    // Entering is dispatched per destination rather than once: `RouteReducer` only
    // loads an Idle route, so returning to an already-loaded screen is free, and a
    // newly revealed one loads without the host tracking which is which.
    LaunchedEffect(shellState.current) {
        dispatch(KaniAction.Lifecycle.Entered)
    }

    val effectHandler = remember(container, provider) {
        desktopEffectHandler(container = container, provider = provider)
    }

    // The theme follows whatever the last load reported, and defaults until then.
    // Deriving it from route content rather than a separate read means the window
    // cannot show one theme while Settings believes another.
    KaniTheme(theme = KaniThemeId.fromStorageKey(routeState.content.valueOrNull?.themeChoice?.storageKey)) {
        KaniShell(
            state = shellState,
            resolver = LiteralUiTextResolver,
            effectHandler = effectHandler,
            dispatch = dispatch,
            backAffordance = ShellBackAffordanceMode.IN_SHELL,
        ) { destination ->
            DesktopRoutePlaceholder(
                destination = destination,
                state = routeState,
                dispatch = dispatch,
            )
        }
    }
}

/**
 * A placeholder body for every route, until Goals 194+ replace them one at a time.
 *
 * It goes through [ShellRouteContent] rather than rendering its own loading and
 * error states, so the placeholder already exercises the shared surfaces the real
 * screens will use — which means the loading spinner, refresh hint, failure banner,
 * and retry button on desktop are the ones covered by the shell's own tests, not a
 * temporary copy that has to be removed later.
 */
@Composable
private fun DesktopRoutePlaceholder(
    destination: KaniDestination,
    state: RouteState<DesktopRouteContent>,
    dispatch: (KaniAction) -> Unit,
) {
    ShellRouteContent(
        state = state,
        copy = rememberShellCopy(),
        resolver = LiteralUiTextResolver,
        dispatch = dispatch,
        modifier = Modifier.testTag(shellRouteTestTag(destination)),
    ) { content ->
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp).testTag(DESKTOP_PLACEHOLDER_TEST_TAG),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = destination.route, style = MaterialTheme.typography.titleLarge)
            Text(text = content.provider.message, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "${content.studyItemCount} kanji admitted, ${content.dueCount} due",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/**
 * Loads the one snapshot every placeholder route shows.
 *
 * On IO, because the provider probe is a blocking HTTP round trip and the profile
 * read is a blocking SQL query, and doing either on the Compose dispatcher stalls
 * the window. This is the whole reason [DesktopShellHost.perform] is suspending
 * rather than plain.
 */
private suspend fun loadDesktopRoute(
    container: DesktopKaniContainer,
    provider: DesktopProviderProbe,
): ContentResult<DesktopRouteContent> = withContext(Dispatchers.IO) {
    val now = System.currentTimeMillis()
    val snapshot = container.homeUseCases.loadRoute(now)
    val due = ReminderEligibilityPolicy
        .eligibleDueTimes(
            snapshot.study.studyItems,
            snapshot.study.activeRows,
            snapshot.study.studyLadder,
        )
        .count { dueAt -> dueAt <= now }
    ContentResult.Success(
        DesktopRouteContent(
            provider = provider.probe(),
            studyItemCount = snapshot.study.studyItems.size,
            dueCount = due,
            themeChoice = snapshot.settings.themeChoice,
        ),
    )
}

/**
 * The four effects the shell cannot perform itself, over the desktop adapters.
 *
 * The AWT calls are supplied as lambdas rather than reached from inside
 * `:platform-desktop`, which is why those adapters stay unit-testable headlessly:
 * `Toolkit.getDefaultToolkit()` throws with no display, and a test for "a blank
 * query is refused" should not need one.
 */
private fun desktopEffectHandler(
    container: DesktopKaniContainer,
    provider: DesktopProviderProbe,
): ShellEffectHandler {
    val navigator = DesktopExternalNavigator(
        browse = { uri ->
            val desktop = Desktop.getDesktop()
            if (!desktop.isSupported(Desktop.Action.BROWSE)) {
                false
            } else {
                desktop.browse(uri)
                true
            }
        },
        // Anki's own browser, not a web one. The callback exists because
        // `:platform-desktop` must not depend on `:provider-ankiconnect`; the
        // composition root is the only place allowed to see both.
        guiBrowse = provider::browse,
    )
    val clipboard = DesktopClipboardService { text ->
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        true
    }
    return object : ShellEffectHandler {
        override fun openUrl(url: String) {
            runCatching { navigator.openUrl(URI(url)) }
        }

        override fun copyToClipboard(text: String) {
            clipboard.setText(label = "Kani", text = text)
        }

        // Goal 199 wires backup export/import and the Missing Kanji CSV to the AWT
        // file dialog. Until then this is a no-op rather than a stub dialog: a
        // picker that opens and cannot deliver its file is worse than a button the
        // capability gate keeps disabled.
        override fun pickFile(purpose: KaniEffect.PickFile) = Unit

        // Focus targets are registered by the feature composables that own the
        // fields, and none exist yet. An unknown target is a no-op by contract.
        override fun requestFocus(target: String) = Unit
    }
}
