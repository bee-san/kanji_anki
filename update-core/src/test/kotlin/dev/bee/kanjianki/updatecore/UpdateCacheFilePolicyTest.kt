package dev.bee.kanjianki.updatecore

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateCacheFilePolicyTest {
    @Test
    fun safeFileNameStripsPathTraversal() {
        assertEquals("kani-0.4.3.apk", UpdateCacheFilePolicy.safeFileName("../release/kani-0.4.3.apk"))
        assertEquals("kani-0.4.3.apk", UpdateCacheFilePolicy.safeFileName("../../kani-0.4.3.apk"))
    }

    @Test
    fun safeFileNameDefaultsMissingNames() {
        assertEquals(UpdateCacheFilePolicy.DEFAULT_APK_NAME, UpdateCacheFilePolicy.safeFileName(""))
        assertEquals(UpdateCacheFilePolicy.DEFAULT_APK_NAME, UpdateCacheFilePolicy.safeFileName(null))
    }
}
