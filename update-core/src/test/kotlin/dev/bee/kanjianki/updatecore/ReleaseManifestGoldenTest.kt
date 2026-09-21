package dev.bee.kanjianki.updatecore

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins the exact bytes a release signature covers.
 *
 * The signature is produced by `ci/scripts/kani_release_manifest.py` and verified by
 * [ReleaseManifestCodec] here. A drift between the two is undetectable until a real
 * release fails to install on every host at once, so both sides assert against the same
 * golden fixture: this copy, and `ci/fixtures/release-manifest-canonical-golden.txt`,
 * which `ci/tests/test_kani_release_manifest.py` asserts is byte-identical to it.
 *
 * If this fails after an intentional format change, the manifest schema version must be
 * bumped — old signed manifests verify against the old bytes, so a silent format change
 * is a breaking change to every already-published release.
 */
class ReleaseManifestGoldenTest {
    @Test
    fun canonicalBytesMatchTheSharedGoldenFixture() {
        val golden = requireNotNull(
            javaClass.classLoader.getResourceAsStream(GOLDEN_RESOURCE),
        ) { "missing golden fixture $GOLDEN_RESOURCE" }.use { stream ->
            String(stream.readAllBytes(), StandardCharsets.UTF_8)
        }

        val manifest = ReleaseManifest(
            schemaVersion = 1,
            releaseTag = "v1.2.3",
            semanticVersion = "1.2.3",
            buildSha = "abc123",
            keyId = "kani-release-key-1",
            assets = listOf(
                ManifestAsset(
                    filename = "kani-desktop-linux-x64-1.2.3.deb",
                    sizeBytes = 1024L,
                    sha256 = "a".repeat(64),
                    os = "linux",
                    arch = "x64",
                    packageType = "deb",
                ),
            ),
        )

        assertEquals(golden, ReleaseManifestCodec.canonicalText(manifest))
        // The parse side must accept the golden bytes too, or the app could not verify a
        // manifest the release job actually produces.
        assertEquals(manifest, ReleaseManifestCodec.parse(golden.toByteArray(StandardCharsets.UTF_8)))
    }

    private companion object {
        const val GOLDEN_RESOURCE = "release-manifest-canonical-golden.txt"
    }
}
