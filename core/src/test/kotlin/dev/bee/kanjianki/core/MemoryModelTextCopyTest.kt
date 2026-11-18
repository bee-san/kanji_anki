package dev.bee.kanjianki.core

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MemoryModelTextCopyTest {
    private val original = Locale.getDefault()

    @After
    fun restoreLocale() {
        Locale.setDefault(original)
    }

    @Test
    fun englishCopyNamesTheModelAndItsState() {
        Locale.setDefault(Locale.US)
        assertEquals("Memory model", MemoryModelTextCopy.title())
        assertTrue(MemoryModelTextCopy.body().contains("RWKV-Curve"))
        assertEquals("Model: SM92 (331,511 parameters)", MemoryModelTextCopy.modelLine("SM92", 331_511))
        assertTrue(MemoryModelTextCopy.provenanceLine().contains("AGPL-3.0-or-later"))
        assertTrue(MemoryModelTextCopy.provenanceLine().contains("EUPL-1.2"))
        assertEquals("Ready", MemoryModelTextCopy.statusReady())
        assertEquals("Checking…", MemoryModelTextCopy.statusChecking())
        assertEquals("Preparing…", MemoryModelTextCopy.statusRebuilding(0, 0))
        assertEquals(
            "Reading your review history… 1,200 of 4,000",
            MemoryModelTextCopy.statusRebuilding(1_200, 4_000),
        )
        assertTrue(MemoryModelTextCopy.statusFailed("disk full").endsWith("disk full"))
        assertEquals("Rebuild from history", MemoryModelTextCopy.rebuildLabel())
        assertTrue(MemoryModelTextCopy.rebuildStartedToast().startsWith("Rebuilding"))
        assertTrue(MemoryModelTextCopy.preparingToast(40).contains("(40%)"))
        assertTrue(!MemoryModelTextCopy.preparingToast(null).contains("%"))
        assertTrue(MemoryModelTextCopy.failedToast().contains("Settings"))
    }

    @Test
    fun japaneseCopyIsLocalized() {
        Locale.setDefault(Locale.JAPAN)
        assertEquals("記憶モデル", MemoryModelTextCopy.title())
        assertEquals("準備完了", MemoryModelTextCopy.statusReady())
        assertTrue(MemoryModelTextCopy.statusRebuilding(1, 2).contains("2件中1件"))
        assertTrue(MemoryModelTextCopy.preparingToast(10).contains("10%"))
        listOf(
            MemoryModelTextCopy.body(),
            MemoryModelTextCopy.modelLine("SM92", 1),
            MemoryModelTextCopy.provenanceLine(),
            MemoryModelTextCopy.statusChecking(),
            MemoryModelTextCopy.statusRebuilding(0, 0),
            MemoryModelTextCopy.statusFailed("x"),
            MemoryModelTextCopy.rebuildLabel(),
            MemoryModelTextCopy.rebuildStartedToast(),
            MemoryModelTextCopy.preparingToast(null),
            MemoryModelTextCopy.failedToast(),
        ).forEach { assertTrue(it.isNotBlank()) }
    }
}
