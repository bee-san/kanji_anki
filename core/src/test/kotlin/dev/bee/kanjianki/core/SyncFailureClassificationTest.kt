package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SyncFailureClassificationTest {

    @Test
    fun classifiesPermissionDenied() {
        assertEquals(
            SyncFailureClassification.PERMISSION_DENIED,
            SyncFailureClassification.classify("AnkiDroid permission is missing: com.ichi2.anki.permission.READ_WRITE_DATABASE", true, false),
        )
    }

    @Test
    fun classifiesPermissionDeniedAccess() {
        assertEquals(
            SyncFailureClassification.PERMISSION_DENIED,
            SyncFailureClassification.classify("AnkiDroid denied database access.", true, false),
        )
    }

    @Test
    fun classifiesProviderUnavailable() {
        assertEquals(
            SyncFailureClassification.PROVIDER_UNAVAILABLE,
            SyncFailureClassification.classify("AnkiDroid's flashcard provider is not installed.", true, false),
        )
    }

    @Test
    fun classifiesTransientTimeout() {
        assertEquals(
            SyncFailureClassification.TRANSIENT_LOCK,
            SyncFailureClassification.classify("Timed out while reading AnkiDroid.", false, true),
        )
    }

    @Test
    fun classifiesTransientRetryable() {
        assertEquals(
            SyncFailureClassification.TRANSIENT_LOCK,
            SyncFailureClassification.classify("AnkiDroid returned no note model cursor.", false, true),
        )
    }

    @Test
    fun classifiesPermanentOther() {
        assertEquals(
            SyncFailureClassification.PERMANENT_OTHER,
            SyncFailureClassification.classify("Kiku note type was not found in AnkiDroid.", true, false),
        )
    }

    @Test
    fun classifiesNullMessageAsPermanent() {
        assertEquals(
            SyncFailureClassification.PERMANENT_OTHER,
            SyncFailureClassification.classify(null, true, false),
        )
    }

    @Test
    fun userMessageNotEmptyForEachClassification() {
        for (classification in SyncFailureClassification.entries) {
            val message = SyncFailureClassification.userMessage(classification)
            assertNotNull(message)
            assert(message.isNotBlank()) { "Empty user message for $classification" }
        }
    }

    @Test
    fun permissionDeniedMessageContainsPermission() {
        val message = SyncFailureClassification.userMessage(SyncFailureClassification.PERMISSION_DENIED)
        assert(message.contains("permission", ignoreCase = true) || message.contains("権限"))
    }

    @Test
    fun providerUnavailableMessageMentionsInstall() {
        val message = SyncFailureClassification.userMessage(SyncFailureClassification.PROVIDER_UNAVAILABLE)
        assert(message.contains("install", ignoreCase = true) || message.contains("インストール"))
    }

    @Test
    fun transientLockMessageMentionsRetry() {
        val message = SyncFailureClassification.userMessage(SyncFailureClassification.TRANSIENT_LOCK)
        assert(message.contains("retry", ignoreCase = true) || message.contains("リトライ"))
    }
}
