package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class UpdateRunScreenCopyTest {
    @Test
    fun cachedPendingRunPreservesInstallerCopy() {
        val copy = UpdateRunScreenCopy.forRun(true)

        assertEquals("Preparing installer", copy.title())
        assertEquals("Verifying APK", copy.progressLabel())
    }

    @Test
    fun manualRunPreservesReleaseCheckCopy() {
        val copy = UpdateRunScreenCopy.forRun(false)

        assertEquals("Checking for updates", copy.title())
        assertEquals("Checking releases", copy.progressLabel())
    }

    @Test
    fun japaneseLocaleTranslatesUpdateRunCopy() {
        withLocale(Locale.JAPANESE) {
            val cachedCopy = UpdateRunScreenCopy.forRun(true)
            val manualCopy = UpdateRunScreenCopy.forRun(false)

            assertEquals("インストーラーを準備中", cachedCopy.title())
            assertEquals("APKを確認中", cachedCopy.progressLabel())
            assertEquals("更新を確認中", manualCopy.title())
            assertEquals("リリースを確認中", manualCopy.progressLabel())
        }
    }

    private inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
