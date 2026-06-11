package dev.bee.kanjianki.updatecore

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateTextPolicyTest {
    @Test
    fun readableMessageUsesExceptionMessageWhenPresent() {
        assertEquals("HTTP 403", UpdateTextPolicy.readableMessage(RuntimeException("HTTP 403")))
    }

    @Test
    fun readableMessageFallsBackToClassOrUnknown() {
        assertEquals("RuntimeException", UpdateTextPolicy.readableMessage(RuntimeException()))
        assertEquals("IllegalStateException", UpdateTextPolicy.readableMessage(IllegalStateException("   ")))
        assertEquals("unknown error", UpdateTextPolicy.readableMessage(null))
    }

    @Test
    fun notificationBodyPrefersVerifiedVersion() {
        assertEquals(
            "Version 0.4.3 is ready. Open Kani to install it.",
            UpdateTextPolicy.notificationBody("v0.4.3", "manual message")
        )
    }

    @Test
    fun notificationBodyFallsBackToMessageOrDefault() {
        assertEquals("Kani update is ready. Open Kani to install it.", UpdateTextPolicy.DEFAULT_PENDING_UPDATE_MESSAGE)
        assertEquals(
            "Checksum verified. Open Kani to install it.",
            UpdateTextPolicy.notificationBody("", "Checksum verified.")
        )
        assertEquals(
            "Kani update is ready. Open Kani to install it.",
            UpdateTextPolicy.notificationBody(null, "  ")
        )
        assertEquals(
            "Kani update is ready. Open Kani to install it.",
            UpdateTextPolicy.notificationBody(null, null)
        )
    }

    @Test
    fun notificationCopyTranslatesToJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            assertEquals("Kaniの更新をインストールできます", UpdateTextPolicy.notificationTitle())
            assertEquals("アプリの更新", UpdateTextPolicy.notificationChannelName())
            assertEquals("Kaniの更新をわかりやすくお知らせします。", UpdateTextPolicy.notificationChannelDescription())
            assertEquals(
                "バージョン 0.4.3 の準備ができました。Kaniを開いてインストールします。",
                UpdateTextPolicy.notificationBody("v0.4.3", "manual message")
            )
            assertEquals(
                "Checksum verified. Kaniを開いてインストールします。",
                UpdateTextPolicy.notificationBody("", "Checksum verified.")
            )
            assertEquals(
                "Kaniの更新準備ができました。Kaniを開いてインストールします。",
                UpdateTextPolicy.notificationBody(null, null)
            )
        }
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
