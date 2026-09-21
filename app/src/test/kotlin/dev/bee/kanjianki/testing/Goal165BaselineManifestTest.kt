package dev.bee.kanjianki.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class Goal165BaselineManifestTest {
    @Test
    fun manifestPinsEverySchemaTransactionAndStorageGolden() {
        val manifest = GoldenFixtureResources.properties(MANIFEST)

        assertEquals("1", manifest.getProperty("format"))
        assertEquals(
            "empty-production-schema-or-synthetic-test-state-only",
            manifest.getProperty("content_policy"),
        )
        val resourceKeys = manifest.stringPropertyNames()
            .filter { it.startsWith("resource.") }
            .sorted()
        assertEquals(
            listOf(
                "resource.schema_registry",
                "resource.schema_v33",
                "resource.schema_v33_fixture",
                "resource.storage_invariants",
                "resource.transaction_rows",
            ),
            resourceKeys,
        )
        for (key in resourceKeys) {
            val fields = manifest.getProperty(key).split('|')
            assertEquals("$key must contain path and SHA-256", 2, fields.size)
            assertTrue("$key must pin a SHA-256", fields[1].matches(Regex("[0-9a-f]{64}")))
            assertEquals(
                "$key content digest",
                fields[1],
                GoldenFixtureResources.sha256(GoldenFixtureResources.bytes(fields[0])),
            )
        }
    }

    private companion object {
        const val MANIFEST = "dev/bee/kanjianki/fixtures/goal165/baseline-manifest.properties"
    }
}
