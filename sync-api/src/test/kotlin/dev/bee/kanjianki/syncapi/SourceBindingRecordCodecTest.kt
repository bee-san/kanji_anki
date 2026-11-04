package dev.bee.kanjianki.syncapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SourceBindingRecordCodecTest {
    @Test
    fun emptySettingsContainNoBinding() {
        assertNull(SourceBindingRecordCodec.decode(emptyMap()))
    }

    @Test
    fun opaqueBindingRoundTripsWithoutUnrelatedSettings() {
        val binding = fixture()
        val encoded = SourceBindingRecordCodec.encode(binding)

        val decoded = SourceBindingRecordCodec.decode(
            encoded + ("unrelated.setting" to "preserved elsewhere"),
        )

        assertEquals(SourceBindingRecordCodec.keys, encoded.keys)
        assertEquals(binding, decoded)
    }

    @Test
    fun incompleteAndMalformedRecordsFailClosed() {
        val encoded = SourceBindingRecordCodec.encode(fixture())

        assertThrows(IllegalArgumentException::class.java) {
            SourceBindingRecordCodec.decode(
                encoded - SourceBindingRecordCodec.KEY_SOURCE_KEY_DIGEST,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SourceBindingRecordCodec.decode(
                encoded + (SourceBindingRecordCodec.KEY_NOTE_ID_DIGESTS to "raw-note-id"),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            SourceBindingRecordCodec.decode(
                encoded + (
                    SourceBindingRecordCodec.KEY_NOTE_ID_DIGESTS to
                        "${"c".repeat(64)},${"c".repeat(64)}"
                    ),
            )
        }
    }

    private fun fixture(): PersistedSourceBinding =
        PersistedSourceBinding(
            version = PersistedSourceBinding.CURRENT_VERSION,
            providerKindDigest = "a".repeat(64),
            sourceKeyDigest = "b".repeat(64),
            bindingSalt = "database-local-random-salt",
            noteIdDigests = listOf("c".repeat(64), "d".repeat(64)),
            cardIdDigests = listOf("e".repeat(64)),
            validationState = SourceBindingValidationState.REVALIDATION_REQUIRED,
            lastValidatedAtMillis = 42L,
        )
}
