package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UpdateRunScreenCopyTest {
    @Test
    fun cachedPendingRunPreservesInstallerCopy() {
        withLocale(Locale.ENGLISH) {
            val copy = UpdateRunScreenCopy.forRun(true)

            assertEquals("Preparing installer", copy.title())
            assertEquals("Verifying APK", copy.progressLabel())
        }
    }

    @Test
    fun manualRunPreservesReleaseCheckCopy() {
        withLocale(Locale.ENGLISH) {
            val copy = UpdateRunScreenCopy.forRun(false)

            assertEquals("Checking for updates", copy.title())
            assertEquals("Checking releases", copy.progressLabel())
        }
    }

    @Test
    fun updateRunCopyTranslatesToJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            val cachedPendingCopy = UpdateRunScreenCopy.forRun(true)
            assertEquals("インストーラーを準備中", cachedPendingCopy.title())
            assertEquals("APKを確認中", cachedPendingCopy.progressLabel())

            val manualRunCopy = UpdateRunScreenCopy.forRun(false)
            assertEquals("更新を確認中", manualRunCopy.title())
            assertEquals("リリースを確認中", manualRunCopy.progressLabel())
        }
    }

    private inline fun withLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
