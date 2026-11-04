package dev.bee.kanjianki.host

import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.ProviderReadiness
import dev.bee.kanjianki.syncapi.CollectionAvailability
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionSourceStatus

/**
 * What the Android host knows about its AnkiDroid collection provider right now.
 *
 * The portable projection of one [CollectionSourceStatus], mirroring desktop's
 * `DesktopProviderStatus`: the shared onboarding and Home surfaces read
 * [readiness]/[message]/[capabilities], never the provider object itself. Holding the
 * projection rather than the gateway is what lets the thin Android host feed the same
 * `KaniShellHost` and shared feature graph desktop does.
 */
data class AndroidProviderStatus(
    val message: String,
    val availability: CollectionAvailability,
    val capabilities: Set<PlatformCapability>,
) {
    /** True only for an installed, permission-granted, syncable AnkiDroid. */
    val isReady: Boolean
        get() = availability == CollectionAvailability.READY

    /**
     * The onboarding step's view of [availability].
     *
     * [CollectionAvailability.INVALID_CONFIGURATION] folds into
     * [ProviderReadiness.ABSENT] rather than [ProviderReadiness.UNAUTHORIZED], the same
     * way desktop's status does: a provider that is present but unusable is not
     * something granting permission fixes, and the `ABSENT` onboarding copy is the
     * host-supplied [message] which already says which case it is.
     */
    val readiness: ProviderReadiness
        get() = when (availability) {
            CollectionAvailability.READY -> ProviderReadiness.READY
            CollectionAvailability.AUTH_REQUIRED -> ProviderReadiness.UNAUTHORIZED
            CollectionAvailability.NOT_AVAILABLE,
            CollectionAvailability.INVALID_CONFIGURATION,
            -> ProviderReadiness.ABSENT
        }
}

/**
 * Projects an AnkiDroid [CollectionSourceStatus] to the portable [AndroidProviderStatus].
 *
 * A `fun interface` over the status rather than the gateway, so a test can supply a
 * status without a `ContentResolver`; the host wires it to `AnkiDroidGateway.status()`.
 */
fun interface AndroidProviderProbe {
    fun probe(): AndroidProviderStatus

    companion object {
        /**
         * Maps AnkiDroid's provider capabilities to the portable [PlatformCapability]s.
         *
         * Only the ones a live AnkiDroid connection actually implies. Note-tag write is
         * Kani's one supported write surface on Android; the browser-handoff and
         * Missing-Kanji-write capabilities are AnkiConnect-only, so they never appear
         * here — which is correct, and is why the capability set is derived rather than
         * assumed equal across hosts.
         */
        fun capabilitiesFor(status: CollectionSourceStatus): Set<PlatformCapability> = buildSet {
            if (CollectionCapability.READ_COLLECTION in status.capabilities) {
                add(PlatformCapability.PROVIDER_CONNECTIVITY)
            }
            if (CollectionCapability.NOTE_TAG_WRITE in status.capabilities) {
                add(PlatformCapability.PROVIDER_NOTE_TAG_WRITE)
            }
        }

        /** A probe that projects the status [source] returns each call. */
        fun of(source: () -> CollectionSourceStatus): AndroidProviderProbe = AndroidProviderProbe {
            val status = source()
            AndroidProviderStatus(
                message = status.message,
                availability = status.availability,
                capabilities = capabilitiesFor(status),
            )
        }
    }
}
