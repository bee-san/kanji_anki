package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class KaniThemeChoiceTest {
    @Test
    fun storageKeysRoundTripAndInvalidValuesUseDefault() {
        for (choice in KaniThemeChoice.entries) {
            assertEquals(choice, KaniThemeChoice.fromStorageKey(choice.storageKey))
        }

        assertEquals(KaniThemeChoice.GIRLYPOP, KaniThemeChoice.fromStorageKey(null))
        assertEquals(KaniThemeChoice.GIRLYPOP, KaniThemeChoice.fromStorageKey("unknown"))
    }
}
