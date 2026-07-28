package dev.bee.kanjianki

import dev.bee.kanjianki.sync.SourceBindingEvidence
import dev.bee.kanjianki.syncapi.CollectionProviderKind
import dev.bee.kanjianki.syncapi.CollectionSourceIdentity
import dev.bee.kanjianki.syncapi.SourceBindingReason
import java.util.Locale
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceBindingRecoveryUiTest {
    @Test
    fun firstBindIsAvailableWithoutSnapshotSupportButDestructiveActionsAreNot() = withEnglish {
        val presentation = SourceBindingRecoveryUi.presentation(
            SourceBindingReason.FIRST_BIND_REQUIRED,
            evidence(),
            safeStorageAvailable = false,
        )

        assertTrue(presentation.firstBindAllowed)
        assertFalse(presentation.rebindAllowed)
        assertFalse(presentation.newProfileAllowed)
    }

    @Test
    fun qualifyingMismatchOffersBackupBackedRecoveryOnlyWhenSnapshotsAreSafe() = withEnglish {
        val supported = SourceBindingRecoveryUi.presentation(
            SourceBindingReason.SOURCE_KEY_CHANGED,
            evidence(),
            safeStorageAvailable = true,
        )
        val unsupported = SourceBindingRecoveryUi.presentation(
            SourceBindingReason.SOURCE_KEY_CHANGED,
            evidence(),
            safeStorageAvailable = false,
        )

        assertFalse(supported.firstBindAllowed)
        assertTrue(supported.rebindAllowed)
        assertTrue(supported.newProfileAllowed)
        assertFalse(unsupported.firstBindAllowed)
        assertFalse(unsupported.rebindAllowed)
        assertFalse(unsupported.newProfileAllowed)
        assertTrue(
            unsupported.lines.any { line ->
                line.contains("Android 11 or later")
            },
        )
    }

    @Test
    fun presentationContainsCountsButNeverRawSourceIdentity() = withEnglish {
        val text = SourceBindingRecoveryUi.presentation(
            SourceBindingReason.INSUFFICIENT_OVERLAP,
            evidence(),
            safeStorageAvailable = true,
        ).lines.joinToString("\n")

        assertTrue(text.contains("2 note IDs"))
        assertTrue(text.contains("1 card IDs"))
        assertTrue(text.contains("11 note"))
        assertTrue(text.contains("12 card"))
        assertFalse(text.contains(SOURCE_KEY))
        assertFalse(text.contains(RAW_NOTE_ID.toString()))
        assertFalse(text.contains(RAW_CARD_ID.toString()))
    }

    private fun evidence(): SourceBindingEvidence {
        val identity = CollectionSourceIdentity.create(
            CollectionProviderKind.ANKIDROID,
            SOURCE_KEY,
            listOf(RAW_NOTE_ID, 2L),
            listOf(RAW_CARD_ID),
        )
        return SourceBindingEvidence(
            candidate = identity.redactedEvidence(),
            priorNoteSampleSize = 11,
            priorCardSampleSize = 12,
        )
    }

    private inline fun withEnglish(block: () -> Unit) {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private companion object {
        const val SOURCE_KEY = "private.provider.authority"
        const val RAW_NOTE_ID = 123_456_789L
        const val RAW_CARD_ID = 987_654_321L
    }
}
