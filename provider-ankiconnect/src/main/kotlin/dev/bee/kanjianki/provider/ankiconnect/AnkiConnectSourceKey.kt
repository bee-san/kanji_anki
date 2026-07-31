package dev.bee.kanjianki.provider.ankiconnect

/**
 * Builds the stable source key that binds a Kani database to one Anki
 * profile.
 *
 * The endpoint alone is **not** a source identity. Every profile on a machine is
 * served by the same `http://127.0.0.1:8765`, so an endpoint-only key would
 * validate unchanged after the user switched Anki profiles, and Kani would
 * silently mirror a different collection into the same database. The key
 * therefore pairs the endpoint with the active profile name reported by
 * `getActiveProfile`.
 *
 * The composed key is fed to `CollectionSourceIdentity`, which digests it under
 * a per-binding salt; neither the endpoint nor the profile name is ever
 * persisted or logged in the clear.
 */
object AnkiConnectSourceKey {
    /**
     * Separator between the endpoint and the profile name. `|` cannot appear in
     * a validated loopback endpoint URL, so the two components stay unambiguous
     * even when a profile name contains unusual characters.
     */
    private const val SEPARATOR = '|'

    /**
     * Composes the source key for [endpointUrl] serving [activeProfile].
     * @throws IllegalArgumentException if either component is blank; a blank
     *   profile means no collection is open, which is not a bindable source.
     */
    fun of(endpointUrl: String, activeProfile: String): String {
        require(endpointUrl.isNotBlank()) { "endpoint URL must not be blank" }
        require(activeProfile.isNotBlank()) { "active profile must not be blank" }
        return "$endpointUrl$SEPARATOR$activeProfile"
    }
}
