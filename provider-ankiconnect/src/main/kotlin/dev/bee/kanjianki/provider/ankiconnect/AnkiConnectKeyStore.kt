package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.platform.SecretPersistence
import dev.bee.kanjianki.platform.SecretReference
import dev.bee.kanjianki.platform.SecretStore
import dev.bee.kanjianki.platform.SecretValue

/**
 * Manages the AnkiConnect API-key lifecycle over the platform [SecretStore]
 * port. The key is consulted only after the keyless permission probe indicates
 * one is needed ([AnkiConnectHandshake]); it is never written to the database or
 * a backup. When the backing store is [SecretPersistence.OS_CREDENTIAL_STORE]
 * the key survives across sessions; when it is [SecretPersistence.SESSION_ONLY]
 * the key is held only for the process session and never falls back to
 * plaintext persistence.
 *
 * The secret value is always read inside a [SecretValue.withValue] block and the
 * transient char/`String` copies are cleared as soon as the request body has
 * been built, so the key spends the minimum possible time materialized.
 */
class AnkiConnectKeyStore(
    private val secrets: SecretStore,
    private val reference: SecretReference = DEFAULT_REFERENCE,
) {
    /** Whether a stored key survives process restarts. */
    val persistsAcrossSessions: Boolean
        get() = secrets.persistence == SecretPersistence.OS_CREDENTIAL_STORE

    /** True when a key is currently stored for this reference. */
    fun hasKey(): Boolean = secrets.read(reference)?.use { true } ?: false

    /**
     * Runs [block] with the current API key as a `String`, or with null when no
     * key is stored. The materialized key string is not retained by this method;
     * callers must not retain it either.
     */
    fun <T> withKey(block: (String?) -> T): T {
        val secret = secrets.read(reference) ?: return block(null)
        return secret.use { value ->
            value.withValue { chars -> block(String(chars)) }
        }
    }

    /** Stores [key] for later use. Returns whether the write succeeded. */
    fun store(key: CharArray): Boolean {
        SecretValue.create(key).use { value ->
            return secrets.write(reference, value)
        }
    }

    /** Removes any stored key. Returns whether a deletion occurred. */
    fun clear(): Boolean = secrets.delete(reference)

    companion object {
        @JvmField
        val DEFAULT_REFERENCE: SecretReference = SecretReference.create("ankiconnect.api-key")
    }
}
