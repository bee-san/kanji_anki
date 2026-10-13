package dev.bee.kanjianki.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.bee.kanjianki.presentation.KaniTab
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.presentation.UiTextResolver
import dev.bee.kanjianki.feature.shell.generated.resources.Res
import dev.bee.kanjianki.feature.shell.generated.resources.capability_backup_restore
import dev.bee.kanjianki.feature.shell.generated.resources.capability_closed_app_scheduling
import dev.bee.kanjianki.feature.shell.generated.resources.capability_notifications
import dev.bee.kanjianki.feature.shell.generated.resources.capability_provider_browser_handoff
import dev.bee.kanjianki.feature.shell.generated.resources.capability_provider_connectivity
import dev.bee.kanjianki.feature.shell.generated.resources.capability_provider_fsrs_memory
import dev.bee.kanjianki.feature.shell.generated.resources.capability_provider_missing_kanji_write
import dev.bee.kanjianki.feature.shell.generated.resources.capability_provider_note_tag_write
import dev.bee.kanjianki.feature.shell.generated.resources.capability_secret_persistence
import dev.bee.kanjianki.feature.shell.generated.resources.capability_tray_presence
import dev.bee.kanjianki.feature.shell.generated.resources.capability_update_delivery
import dev.bee.kanjianki.feature.shell.generated.resources.capability_writing_recognition
import dev.bee.kanjianki.feature.shell.generated.resources.failure_cancelled
import dev.bee.kanjianki.feature.shell.generated.resources.failure_capability_missing
import dev.bee.kanjianki.feature.shell.generated.resources.failure_configuration
import dev.bee.kanjianki.feature.shell.generated.resources.failure_conflict
import dev.bee.kanjianki.feature.shell.generated.resources.failure_provider_auth_required
import dev.bee.kanjianki.feature.shell.generated.resources.failure_provider_unavailable
import dev.bee.kanjianki.feature.shell.generated.resources.failure_transient
import dev.bee.kanjianki.feature.shell.generated.resources.failure_unknown
import dev.bee.kanjianki.feature.shell.generated.resources.nav_home
import dev.bee.kanjianki.feature.shell.generated.resources.nav_item_description
import dev.bee.kanjianki.feature.shell.generated.resources.nav_item_description_selected
import dev.bee.kanjianki.feature.shell.generated.resources.nav_settings
import dev.bee.kanjianki.feature.shell.generated.resources.nav_stats
import dev.bee.kanjianki.feature.shell.generated.resources.nav_study
import dev.bee.kanjianki.feature.shell.generated.resources.shell_back
import dev.bee.kanjianki.feature.shell.generated.resources.shell_dismiss
import dev.bee.kanjianki.feature.shell.generated.resources.shell_loading
import dev.bee.kanjianki.feature.shell.generated.resources.shell_retry
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Every string the shell itself displays, resolved once per composition.
 *
 * A holder rather than a `stringResource` call at each use site, for two reasons.
 * `stringResource` is a composable, so it cannot be called from the plain
 * functions that build a nav item's accessibility description or map a failure
 * kind to copy — and passing a resolved holder down keeps those functions pure and
 * unit-testable. It also means a test can substitute known strings and assert on
 * layout without depending on the shipped wording.
 */
data class ShellCopy(
    val navHome: String,
    val navStudy: String,
    val navStats: String,
    val navSettings: String,
    val back: String,
    val loading: String,
    val retry: String,
    val dismiss: String,
    private val navItemDescription: String,
    private val navItemDescriptionSelected: String,
    private val failures: Map<PresentationFailure.Kind, String>,
    private val capabilities: Map<PlatformCapability, String>,
) {
    fun tabLabel(tab: KaniTab): String = when (tab) {
        KaniTab.HOME -> navHome
        KaniTab.STUDY -> navStudy
        KaniTab.STATS -> navStats
        KaniTab.SETTINGS -> navSettings
    }

    /**
     * What a screen reader says for one tab.
     *
     * The selected state is spoken as well as set in semantics, matching the
     * Android bottom bar. TalkBack announces `selected` from the semantics
     * property, but the visible-label-plus-state phrasing is what the Android
     * host shipped and changing it would change what users hear.
     */
    fun tabDescription(tab: KaniTab, selected: Boolean): String {
        val template = if (selected) navItemDescriptionSelected else navItemDescription
        return template.replace(NAV_LABEL_PLACEHOLDER, tabLabel(tab))
    }

    /**
     * Copy for a failure, preferring what the host already resolved.
     *
     * A host that knows *why* the provider was unreachable can say so; the
     * per-kind fallback exists so a failure with no message is still explained
     * rather than rendering an error panel with an empty body.
     */
    fun failureMessage(failure: PresentationFailure, resolver: UiTextResolver): String {
        val hostMessage = resolver.resolve(failure.message)
        return hostMessage.ifBlank { failures.getValue(failure.kind) }
    }

    fun capabilityExplanation(capability: PlatformCapability): String =
        capabilities.getValue(capability)

    companion object {
        private const val NAV_LABEL_PLACEHOLDER = "%1\$s"
    }
}

/**
 * Resolves [ShellCopy] from this module's own Compose Multiplatform resources.
 *
 * The maps are built exhaustively from the enum entries, so a new
 * [PlatformCapability] or [PresentationFailure.Kind] is a compile error here
 * rather than a `NoSuchElementException` the first time a user reaches it.
 */
@Composable
fun rememberShellCopy(): ShellCopy {
    val failures = PresentationFailure.Kind.entries.associateWith { kind ->
        stringResource(kind.resource())
    }
    val capabilities = PlatformCapability.entries.associateWith { capability ->
        stringResource(capability.resource())
    }
    val navHome = stringResource(Res.string.nav_home)
    val navStudy = stringResource(Res.string.nav_study)
    val navStats = stringResource(Res.string.nav_stats)
    val navSettings = stringResource(Res.string.nav_settings)
    val back = stringResource(Res.string.shell_back)
    val loading = stringResource(Res.string.shell_loading)
    val retry = stringResource(Res.string.shell_retry)
    val dismiss = stringResource(Res.string.shell_dismiss)
    val navItemDescription = stringResource(Res.string.nav_item_description)
    val navItemDescriptionSelected =
        stringResource(Res.string.nav_item_description_selected)
    return remember(
        failures,
        capabilities,
        navHome,
        navStudy,
        navStats,
        navSettings,
        back,
        loading,
        retry,
        dismiss,
        navItemDescription,
        navItemDescriptionSelected,
    ) {
        ShellCopy(
            navHome = navHome,
            navStudy = navStudy,
            navStats = navStats,
            navSettings = navSettings,
            back = back,
            loading = loading,
            retry = retry,
            dismiss = dismiss,
            navItemDescription = navItemDescription,
            navItemDescriptionSelected = navItemDescriptionSelected,
            failures = failures,
            capabilities = capabilities,
        )
    }
}

private fun PresentationFailure.Kind.resource(): StringResource = when (this) {
    PresentationFailure.Kind.PROVIDER_UNAVAILABLE -> Res.string.failure_provider_unavailable
    PresentationFailure.Kind.PROVIDER_AUTH_REQUIRED ->
        Res.string.failure_provider_auth_required
    PresentationFailure.Kind.CONFIGURATION -> Res.string.failure_configuration
    PresentationFailure.Kind.CAPABILITY_MISSING -> Res.string.failure_capability_missing
    PresentationFailure.Kind.TRANSIENT -> Res.string.failure_transient
    PresentationFailure.Kind.CANCELLED -> Res.string.failure_cancelled
    PresentationFailure.Kind.CONFLICT -> Res.string.failure_conflict
    PresentationFailure.Kind.UNKNOWN -> Res.string.failure_unknown
}

private fun PlatformCapability.resource(): StringResource = when (this) {
    PlatformCapability.PROVIDER_CONNECTIVITY -> Res.string.capability_provider_connectivity
    PlatformCapability.PROVIDER_FSRS_MEMORY -> Res.string.capability_provider_fsrs_memory
    PlatformCapability.PROVIDER_NOTE_TAG_WRITE ->
        Res.string.capability_provider_note_tag_write
    PlatformCapability.PROVIDER_MISSING_KANJI_WRITE ->
        Res.string.capability_provider_missing_kanji_write
    PlatformCapability.PROVIDER_BROWSER_HANDOFF ->
        Res.string.capability_provider_browser_handoff
    PlatformCapability.WRITING_RECOGNITION -> Res.string.capability_writing_recognition
    PlatformCapability.TRAY_PRESENCE -> Res.string.capability_tray_presence
    PlatformCapability.NOTIFICATIONS -> Res.string.capability_notifications
    PlatformCapability.CLOSED_APP_SCHEDULING -> Res.string.capability_closed_app_scheduling
    PlatformCapability.SECRET_PERSISTENCE -> Res.string.capability_secret_persistence
    PlatformCapability.BACKUP_RESTORE -> Res.string.capability_backup_restore
    PlatformCapability.UPDATE_DELIVERY -> Res.string.capability_update_delivery
}

/**
 * A resolver for a [UiText] that carries no host-specific key.
 *
 * [UiText.Literal] is already final text, and nested arguments are substituted
 * positionally. A [UiText.Key] or [UiText.Quantity] returns the empty string:
 * this resolver has no table to look a key up in, and inventing one here would
 * put feature copy in the shell. Hosts and features supply their own resolver;
 * this is the honest default for the shell's own rendering, and the callers that
 * use it treat blank as "fall back to shell copy".
 */
val LiteralUiTextResolver: UiTextResolver = UiTextResolver { text ->
    when (text) {
        is UiText.Literal -> text.text
        is UiText.Key, is UiText.Quantity -> ""
    }
}
