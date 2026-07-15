package dev.bee.kanjianki.data

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable

class LocalStoreCloseCompatibilityTest {
    @Test
    fun localStoreHasApi26CompatibleCloseableContract() {
        assertTrue(Closeable::class.java.isAssignableFrom(LocalStore::class.java))
    }
}
