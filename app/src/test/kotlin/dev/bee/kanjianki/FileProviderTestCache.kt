package dev.bee.kanjianki

import androidx.core.content.FileProvider

/**
 * Clears [FileProvider]'s static per-authority PathStrategy cache (`sCache`).
 *
 * Robolectric gives every test method a fresh temp `filesDir`, but the sandbox classloader (and
 * therefore FileProvider's static cache) is shared across tests with the same config. The first
 * `getUriForFile` call caches a path strategy rooted at that test's `filesDir`; later tests then
 * fail with "Failed to find configured root" because the cached root no longer contains their
 * fresh `filesDir`. Call this from `@Before` in any test that builds share intents.
 */
fun clearFileProviderPathStrategyCache() {
    runCatching {
        val cacheField = FileProvider::class.java.getDeclaredField("sCache")
        cacheField.isAccessible = true
        (cacheField.get(null) as? MutableMap<*, *>)?.clear()
    }
}
