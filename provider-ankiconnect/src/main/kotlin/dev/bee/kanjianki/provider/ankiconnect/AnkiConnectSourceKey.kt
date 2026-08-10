package dev.bee.kanjianki.provider.ankiconnect

/**
 * Builds the stable source key that binds a Kani database to one Anki
 * profile.
 *
 * The endpoint alone is **not** a source identity. Every profile on a machine is
 * served by the same `http://127.0.0.1:8765`, so an endpoint-only key would
 * validate unchanged after the user switched Anki profiles, and Kani would
 * silently mirror a different collection into the same database. The key
 * therefore pairs the endpoint with the loaded profile's identity: its media
 * directory path, from `getMediaDirPath`. AnkiConnect has no action that names
 * the loaded profile (see `AnkiConnectActions.required`), and the path is in
 * fact the stronger identity — two Anki base directories can each hold a
 * profile named `User 1`, and those are different collections.
 *
 * The composed key is fed to `CollectionSourceIdentity`, which digests it under
 * a per-binding salt; neither the endpoint nor the profile identity is ever
 * persisted or logged in the clear. That matters more for a path than it did
 * for a name, because the path contains the operator's home directory.
 */
object AnkiConnectSourceKey {
    /**
     * Separator between the endpoint and the profile identity. `|` cannot appear
     * in a validated loopback endpoint URL, so the two components stay
     * unambiguous even when a profile directory contains unusual characters.
     */
    private const val SEPARATOR = '|'

    /**
     * Composes the source key for [endpointUrl] serving [profileIdentity].
     * @throws IllegalArgumentException if either component is blank; a blank
     *   identity means no collection is open, which is not a bindable source.
     */
    fun of(endpointUrl: String, profileIdentity: String): String {
        require(endpointUrl.isNotBlank()) { "endpoint URL must not be blank" }
        require(profileIdentity.isNotBlank()) { "profile identity must not be blank" }
        return "$endpointUrl$SEPARATOR$profileIdentity"
    }
}
