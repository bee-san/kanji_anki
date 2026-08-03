package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.hostpresentation.KaniRouteContent
import dev.bee.kanjianki.presentation.ContentResult
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniLaunchRequest
import dev.bee.kanjianki.presentation.KaniShellHost
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.syncapi.CollectionFailure

/**
 * The desktop host's presentation state.
 *
 * The reducer-driving machinery is the shared [KaniShellHost]; this factory only
 * supplies the desktop-specific failure classification. A [CollectionFailure] already
 * knows what went wrong, so its kind is carried across rather than flattened to
 * [PresentationFailure.Kind.UNKNOWN] — which makes the retry button honest, since
 * `UNKNOWN` is retryable by design and offering "try again" for an unopened profile or
 * a too-old AnkiConnect sends the user in a circle. Only the kind crosses; the
 * exception's own message stays in the failure's diagnostic field.
 */
internal typealias DesktopShellHost = KaniShellHost<DesktopRouteContent>

internal fun DesktopShellHost(
    launch: KaniLaunchRequest? = null,
    restored: KaniDestination? = null,
    capabilities: PlatformCapabilities = PlatformCapabilities.NONE,
    loadRoute: suspend (KaniDestination) -> ContentResult<DesktopRouteContent>,
): DesktopShellHost = KaniShellHost(
    launch = launch,
    restored = restored,
    capabilities = capabilities,
    classifyFailure = { failure ->
        (failure as? CollectionFailure)
            ?.let { DesktopHomeModels.failureKind(it.kind) }
            ?: PresentationFailure.Kind.UNKNOWN
    },
    loadRoute = loadRoute,
)

/**
 * What a desktop route has to show: the shared [KaniRouteContent], which both hosts
 * fill from their own snapshots and render through the same feature surfaces.
 */
internal typealias DesktopRouteContent = KaniRouteContent
