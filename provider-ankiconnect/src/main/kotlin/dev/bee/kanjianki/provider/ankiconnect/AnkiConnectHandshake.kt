package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectEnvelope.Response
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectJson.Json

/**
 * The AnkiConnect handshake: determine whether a compatible AnkiConnect is
 * reachable and what it can do, without reading any collection data. The
 * sequence follows the pinned API v6 protocol:
 *
 *  1. `requestPermission` is sent first **without a key** — AnkiConnect either
 *     grants immediately (loopback) or returns a prompt state; only after this
 *     may Kani consult the secret store for a key.
 *  2. `version` confirms the wire protocol.
 *  3. `apiReflect` (scopes `["actions"]`) reports the supported actions, which
 *     are classified against [AnkiConnectActions] into required-gap / optional.
 *  4. `getMediaDirPath` confirms a collection is open and identifies which
 *     profile it belongs to.
 *
 * Step 4 asks an odd-looking question, and the reason is worth stating plainly:
 * AnkiConnect has no action that reports the loaded profile. Kani previously
 * probed `getActiveProfile`, which does not exist in any AnkiConnect — the real
 * server answers `unsupported action` — so this handshake reported every real
 * Anki as [Status.Unavailable] while passing against a mock that had been taught
 * to answer it. `getMediaDirPath` returns the open collection's media directory,
 * which serves both purposes at once: it fails when no collection is open, and
 * the path is under the loaded profile's directory, so it identifies the
 * profile. `getProfiles` would not do, because it lists every profile on the
 * machine whether or not it is open.
 *
 * The client itself performs no I/O beyond calling the injected [transport];
 * every step is fail-closed and maps to an actionable [Status].
 */
class AnkiConnectHandshake(
    private val transport: AnkiConnectTransport,
) {
    /** The overall handshake outcome. */
    sealed interface Status {
        /**
         * Fully ready: permission granted, version ok, no required-action gap.
         *
         * [profileIdentity] is the loaded profile's media directory path, and is
         * to be treated as opaque. It is used *as* the profile identity rather
         * than as a path, because it is a stronger one than a profile name: two
         * Anki base directories can both hold a profile called `User 1`, and
         * those are different collections. It may contain the operator's home
         * directory, so it is only ever fed to [AnkiConnectSourceKey], which
         * digests it under a per-binding salt.
         */
        data class Ready(
            val version: Long,
            val profileIdentity: String?,
            val availableOptionalActions: Set<String>,
        ) : Status

        /** Reachable but the user must grant permission in Anki first. */
        data object PermissionRequired : Status

        /** Reachable, but missing required API actions (too old / restricted). */
        data class MissingRequiredActions(val actions: Set<String>) : Status

        /** Reachable but no collection/profile is open. */
        data object NoActiveProfile : Status

        /** Wire version is not the supported v6 protocol. */
        data class UnsupportedVersion(val reported: Long) : Status

        /** Not reachable / protocol failure. Reason is safe to log. */
        data class Unavailable(val detail: String) : Status
    }

    /**
     * Runs the handshake. [apiKey] is provided by the caller only after the
     * keyless permission probe indicates a key is needed; pass null for the
     * first attempt.
     */
    fun run(apiKey: String? = null): Status {
        val permission = requestPermission() ?: return Status.Unavailable("permission probe failed")
        if (!permission) return Status.PermissionRequired

        val version = version(apiKey)
        if (version == null) return Status.Unavailable("version check failed")
        if (version != AnkiConnectEnvelope.API_VERSION) return Status.UnsupportedVersion(version)

        val reported = apiReflect(apiKey) ?: return Status.Unavailable("apiReflect failed")
        val missing = AnkiConnectActions.missingRequired(reported)
        if (missing.isNotEmpty()) return Status.MissingRequiredActions(missing)

        return when (val profile = profileIdentity(apiKey)) {
            is Probe.Found -> Status.Ready(
                version = version,
                profileIdentity = profile.identity,
                availableOptionalActions = AnkiConnectActions.availableOptional(reported),
            )
            Probe.NoCollection -> Status.NoActiveProfile
            Probe.Failed -> Status.Unavailable("profile identity probe failed")
        }
    }

    /**
     * The outcome of the profile-identity probe. The three cases are kept apart
     * because they mean different things to the user: [NoCollection] is fixed by
     * opening a collection in Anki, while [Failed] is a transport or protocol
     * problem that opening a collection will not help.
     */
    private sealed interface Probe {
        data class Found(val identity: String) : Probe
        data object NoCollection : Probe
        data object Failed : Probe
    }

    /** The keyless permission probe. true = granted, false = prompt, null = failure. */
    private fun requestPermission(): Boolean? {
        val result = call(AnkiConnectEnvelope.request("requestPermission")) ?: return null
        val obj = result as? Json.Obj ?: return null
        val permission = (obj.entries["permission"] as? Json.Str)?.value
        return when (permission) {
            "granted" -> true
            "denied" -> false
            else -> null
        }
    }

    private fun version(apiKey: String?): Long? {
        val result = call(AnkiConnectEnvelope.request("version", apiKey = apiKey)) ?: return null
        return (result as? Json.Num)?.value
    }

    private fun apiReflect(apiKey: String?): List<String>? {
        val params = AnkiConnectJson.obj(
            "scopes" to AnkiConnectJson.arr(listOf(AnkiConnectJson.str("actions"))),
            "actions" to Json.Null,
        )
        val result = call(AnkiConnectEnvelope.request("apiReflect", params, apiKey)) ?: return null
        val obj = result as? Json.Obj ?: return null
        val actions = obj.entries["actions"] as? Json.Arr ?: return null
        return actions.items.mapNotNull { (it as? Json.Str)?.value }
    }

    /**
     * Probes the loaded profile via `getMediaDirPath`.
     *
     * AnkiConnect resolves the media directory through the open collection and
     * *raises* when there is none — the failure arrives as an error envelope, not
     * as a null result. That error is therefore read as [Probe.NoCollection]
     * rather than as a protocol fault; a request that never got an envelope at
     * all is what counts as [Probe.Failed].
     */
    private fun profileIdentity(apiKey: String?): Probe {
        val request = AnkiConnectEnvelope.request("getMediaDirPath", apiKey = apiKey)
        val body = when (val exchange = transport.post(request)) {
            is AnkiConnectTransport.Exchange.Body -> exchange.text
            is AnkiConnectTransport.Exchange.Failure -> return Probe.Failed
        }
        return when (val response = AnkiConnectEnvelope.parse(body)) {
            is Response.Ok -> when (val result = response.result) {
                is Json.Str -> if (result.value.isBlank()) Probe.NoCollection else Probe.Found(result.value)
                Json.Null -> Probe.NoCollection
                else -> Probe.Failed
            }
            is Response.Failed -> Probe.NoCollection
            Response.ProtocolError -> Probe.Failed
        }
    }

    /** Sends one request and returns its success result, or null on any failure. */
    private fun call(request: AnkiConnectEnvelope.Request): Json? {
        val body = when (val exchange = transport.post(request)) {
            is AnkiConnectTransport.Exchange.Body -> exchange.text
            is AnkiConnectTransport.Exchange.Failure -> return null
        }
        return when (val response = AnkiConnectEnvelope.parse(body)) {
            is Response.Ok -> response.result
            is Response.Failed -> null
            Response.ProtocolError -> null
        }
    }
}
