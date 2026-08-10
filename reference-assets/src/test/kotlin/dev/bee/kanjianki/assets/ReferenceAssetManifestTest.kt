package dev.bee.kanjianki.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceAssetManifestTest {
    @Test
    fun bundledManifestHasOneEntryPerKindWithLicenceRecords() {
        val manifest = ReferenceAssetManifest.bundled()
        assertEquals(ReferenceAssetManifest.SCHEMA_VERSION, manifest.schemaVersion)
        assertEquals(
            setOf(
                ReferenceAssetKind.DICTIONARY_DATABASE,
                ReferenceAssetKind.FREQUENCY_RANKS,
                ReferenceAssetKind.STROKE_GUIDES,
                ReferenceAssetKind.STUDY_FONT,
            ),
            manifest.assets.map { it.kind }.toSet(),
        )
        manifest.assets.forEach { asset ->
            assertTrue("every asset carries an attribution", asset.license.attribution.isNotBlank())
            assertTrue("every asset has a target", asset.extractionTarget.startsWith("reference/"))
            // Real digests, not placeholders. This previously asserted the opposite —
            // it pinned the incomplete state, so recording the true hashes failed it.
            // The durable property is that a shipped asset is verifiable at all: a
            // placeholder means the verifier cannot check the bytes it installs.
            assertFalse(
                "a bundled asset must carry its real digest: ${asset.id}",
                asset.hasPlaceholderHash(),
            )
            assertEquals(64, asset.expectedSha256.length)
        }
    }

    @Test
    fun byIdAndByFileNameResolveEntries() {
        val manifest = ReferenceAssetManifest.bundled()
        assertEquals("kanji_dictionary.db", manifest.byId("kanji_dictionary")?.fileName)
        assertEquals("kanji_dictionary", manifest.byFileName("kanji_dictionary.db")?.id)
        assertEquals(null, manifest.byId("missing"))
    }

    @Test
    fun manifestRejectsDuplicateIdsAndFileNames() {
        val duplicateId = ReferenceAssetManifest.bundled().assets.let { it + it.first() }
        assertThrows(IllegalArgumentException::class.java) {
            ReferenceAssetManifest(ReferenceAssetManifest.SCHEMA_VERSION, duplicateId)
        }
    }

    @Test
    fun assetRejectsNonHexHash() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            ReferenceAsset(
                id = "x",
                kind = ReferenceAssetKind.STUDY_FONT,
                fileName = "x.otf",
                expectedSha256 = "not-a-hash",
                formatVersion = 1,
                extractionTarget = "reference/x.otf",
                license = license(),
            )
        }
        assertTrue(error.message!!.contains("expectedSha256"))
    }

    @Test
    fun realHashIsNotFlaggedAsPlaceholder() {
        val asset = ReferenceAsset(
            id = "x",
            kind = ReferenceAssetKind.STUDY_FONT,
            fileName = "x.otf",
            expectedSha256 = "a".repeat(64),
            formatVersion = 1,
            extractionTarget = "reference/x.otf",
            license = license(),
        )
        assertFalse(asset.hasPlaceholderHash())
    }

    private fun license() = ReferenceAssetLicense("n", "spdx", null, "attr")
}
