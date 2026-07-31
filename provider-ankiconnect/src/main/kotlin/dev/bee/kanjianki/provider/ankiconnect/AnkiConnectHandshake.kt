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
 *  4. `getActiveProfile` confirms a collection is open.
 *
 * The client itself performs no I/O beyond calling the injected [transport];
 * every step is fail-closed and maps to an actionable [Status].
 */
class AnkiConnectHandshake(
    private val transport: AnkiConnectTransport,
) {
    /** The overall handshake outcome. */
    sealed interface Status {
        /** Fully ready: permission granted, version ok, no required-action gap. */
        data class Ready(
            val version: Long,
            val activeProfile: String?,
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

        val profile = activeProfile(apiKey)
        if (profile == null) return Status.Unavailable("getActiveProfile failed")
        if (profile.isBlank()) return Status.NoActiveProfile

        return Status.Ready(
            version = version,
            activeProfile = profile,
            availableOptionalActions = AnkiConnectActions.availableOptional(reported),
        )
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

    private fun activeProfile(apiKey: String?): String? {
        val result = call(AnkiConnectEnvelope.request("getActiveProfile", apiKey = apiKey)) ?: return null
        // AnkiConnect returns the profile name string, or null when none is open.
        return when (result) {
            is Json.Str -> result.value
            Json.Null -> ""
            else -> null
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
