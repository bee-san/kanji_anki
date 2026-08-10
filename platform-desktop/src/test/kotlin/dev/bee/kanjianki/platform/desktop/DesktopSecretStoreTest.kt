package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.SecretPersistence
import dev.bee.kanjianki.platform.SecretReference
import dev.bee.kanjianki.platform.SecretValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopSecretStoreTest {
    private val reference = SecretReference.create("ankiconnect.api-key")
    private val other = SecretReference.create("other.key")

    @Test
    fun withoutAQualifiedVaultTheStoreReportsSessionOnly() {
        // The load-bearing claim of this whole class: no vault adapter is qualified,
        // so the honest answer is SESSION_ONLY, and Settings can say so.
        val store = DesktopSecretStore()

        assertEquals(SecretPersistence.SESSION_ONLY, store.persistence)
        assertNull(store.vaultName)
    }

    @Test
    fun aSessionKeyIsReadableUntilItIsCleared() {
        val store = DesktopSecretStore()

        assertTrue(store.write(reference, SecretValue.create("secret-key")))

        assertEquals("secret-key", store.text(reference))
    }

    @Test
    fun readingAnUnknownReferenceIsNullRatherThanAnotherKeysValue() {
        val store = DesktopSecretStore()
        store.write(reference, SecretValue.create("secret-key"))

        assertNull(store.read(other))
    }

    @Test
    fun overwritingAKeyReplacesItAndReportsTheNewValue() {
        val store = DesktopSecretStore()
        store.write(reference, SecretValue.create("first"))

        store.write(reference, SecretValue.create("second"))

        assertEquals("second", store.text(reference))
    }

    @Test
    fun deletingReportsWhetherThereWasAnythingToDelete() {
        val store = DesktopSecretStore()
        store.write(reference, SecretValue.create("secret-key"))

        assertTrue(store.delete(reference))
        assertNull(store.read(reference))
        assertFalse(store.delete(reference))
    }

    @Test
    fun clearingTheSessionDropsEveryHeldKey() {
        // Called on shutdown and on profile switch: one profile's provider key must
        // not stay readable after switching to another.
        val store = DesktopSecretStore()
        store.write(reference, SecretValue.create("one"))
        store.write(other, SecretValue.create("two"))

        store.clearSession()

        assertNull(store.read(reference))
        assertNull(store.read(other))
    }

    @Test
    fun aWrittenValueSurvivesTheCallersOwnSecretValueBeingClosed() {
        // SecretValue zeroes its buffer on close, so a store that kept the caller's
        // array instead of copying would hand back blanks on the next read -- and
        // the symptom would be "AnkiConnect says unauthorized", not "we lost it".
        val store = DesktopSecretStore()
        val value = SecretValue.create("secret-key")

        store.write(reference, value)
        value.close()

        assertEquals("secret-key", store.text(reference))
    }

    @Test
    fun readingTwiceGivesTwoIndependentlyClosableValues() {
        val store = DesktopSecretStore()
        store.write(reference, SecretValue.create("secret-key"))

        val first = store.read(reference)!!
        first.close()

        assertEquals("secret-key", store.text(reference))
    }

    @Test
    fun aQualifiedVaultTakesOverEveryOperationAndReportsPersistence() {
        val vault = RecordingVault()
        val store = DesktopSecretStore(vault)

        assertEquals(SecretPersistence.OS_CREDENTIAL_STORE, store.persistence)
        assertEquals("recording", store.vaultName)
        assertTrue(store.write(reference, SecretValue.create("vaulted")))
        assertEquals("vaulted", store.text(reference))
        assertTrue(store.delete(reference))
        assertNull(store.read(reference))
        assertFalse(store.delete(reference))
    }

    @Test
    fun aVaultReadIsCopiedSoTheVaultsOwnBufferCanBeCleared() {
        // The store zeroes what the vault handed it; if SecretValue did not copy,
        // that scrub would blank the value the caller is about to use.
        val vault = RecordingVault()
        val store = DesktopSecretStore(vault)
        store.write(reference, SecretValue.create("vaulted"))

        val value = store.read(reference)!!

        assertEquals("vaulted", value.use { it.withValue(::String) })
    }

    @Test
    fun aVaultWriteFailureIsReportedRatherThanFallingBackToMemory() {
        // The rule with no exceptions: a key never persists anywhere weaker than
        // the vault that was supposed to hold it. Quietly keeping it in memory
        // would make a broken vault look like a working one.
        val store = DesktopSecretStore(RejectingVault())

        assertFalse(store.write(reference, SecretValue.create("vaulted")))
        assertNull(store.read(reference))
    }

    private fun DesktopSecretStore.text(reference: SecretReference): String? =
        read(reference)?.use { value -> value.withValue(::String) }

    private class RecordingVault : DesktopSecretStore.Vault {
        private val entries = HashMap<String, CharArray>()

        override val name: String = "recording"

        override fun read(reference: SecretReference): CharArray? =
            entries[reference.value]?.copyOf()

        override fun write(reference: SecretReference, value: CharArray): Boolean {
            entries[reference.value] = value.copyOf()
            return true
        }

        override fun delete(reference: SecretReference): Boolean =
            entries.remove(reference.value) != null
    }

    private class RejectingVault : DesktopSecretStore.Vault {
        override val name: String = "rejecting"

        override fun read(reference: SecretReference): CharArray? = null

        override fun write(reference: SecretReference, value: CharArray): Boolean = false

        override fun delete(reference: SecretReference): Boolean = false
    }
}
