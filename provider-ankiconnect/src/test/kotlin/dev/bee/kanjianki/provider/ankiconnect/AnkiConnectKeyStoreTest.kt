package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.platform.SecretPersistence
import dev.bee.kanjianki.platform.SecretReference
import dev.bee.kanjianki.platform.SecretStore
import dev.bee.kanjianki.platform.SecretValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectKeyStoreTest {
    /** An in-memory SecretStore; stores the last written value as chars. */
    private class FakeSecretStore(
        override val persistence: SecretPersistence,
    ) : SecretStore {
        private val values = HashMap<String, CharArray>()

        override fun read(reference: SecretReference): SecretValue? =
            values[reference.value]?.let { SecretValue.create(it) }

        override fun write(reference: SecretReference, value: SecretValue): Boolean {
            value.withValue { chars -> values[reference.value] = chars.copyOf() }
            return true
        }

        override fun delete(reference: SecretReference): Boolean =
            values.remove(reference.value) != null
    }

    @Test
    fun storesAndReadsTheKeyBack() {
        val store = AnkiConnectKeyStore(FakeSecretStore(SecretPersistence.OS_CREDENTIAL_STORE))
        assertFalse(store.hasKey())
        assertTrue(store.store("s3cret".toCharArray()))
        assertTrue(store.hasKey())
        val observed = store.withKey { it }
        assertEquals("s3cret", observed)
    }

    @Test
    fun withKeyPassesNullWhenNoKeyStored() {
        val store = AnkiConnectKeyStore(FakeSecretStore(SecretPersistence.SESSION_ONLY))
        assertNull(store.withKey { it })
    }

    @Test
    fun clearRemovesTheStoredKey() {
        val store = AnkiConnectKeyStore(FakeSecretStore(SecretPersistence.OS_CREDENTIAL_STORE))
        store.store("abc".toCharArray())
        assertTrue(store.clear())
        assertFalse(store.hasKey())
        assertFalse(store.clear())
    }

    @Test
    fun reportsPersistenceMode() {
        assertTrue(
            AnkiConnectKeyStore(FakeSecretStore(SecretPersistence.OS_CREDENTIAL_STORE)).persistsAcrossSessions,
        )
        assertFalse(
            AnkiConnectKeyStore(FakeSecretStore(SecretPersistence.SESSION_ONLY)).persistsAcrossSessions,
        )
    }

    @Test
    fun buildsARequestUsingTheStoredKeyWithoutRetainingIt() {
        val store = AnkiConnectKeyStore(FakeSecretStore(SecretPersistence.SESSION_ONLY))
        store.store("live-key".toCharArray())
        val request = store.withKey { key -> AnkiConnectEnvelope.request("version", apiKey = key) }
        val json = AnkiConnectJson.decode(request.json) as AnkiConnectJson.Json.Obj
        assertEquals(AnkiConnectJson.Json.Str("live-key"), json.entries["key"])
    }

    @Test
    fun honoursACustomReference() {
        val backing = FakeSecretStore(SecretPersistence.OS_CREDENTIAL_STORE)
        val store = AnkiConnectKeyStore(backing, SecretReference.create("ankiconnect.alt-key"))
        store.store("k".toCharArray())
        // The default-reference view sees nothing.
        assertFalse(AnkiConnectKeyStore(backing).hasKey())
        assertTrue(store.hasKey())
    }
}
