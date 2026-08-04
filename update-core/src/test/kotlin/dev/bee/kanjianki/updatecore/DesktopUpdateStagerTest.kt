package dev.bee.kanjianki.updatecore

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DesktopUpdateStagerTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun matchingBytesArePublishedAtomicallyWithNoPartialLeftBehind() {
        val payload = "kani desktop installer".toByteArray()

        val result = stage(payload, asset(payload))

        val published = result as DesktopUpdateStager.Result.Published
        assertEquals(directory().resolve(NAME), published.path)
        assertEquals(payload.size.toLong(), published.sizeBytes)
        assertArrayEquals(payload, Files.readAllBytes(published.path))
        assertNoPartialRemains()
    }

    @Test
    fun aTruncatedDownloadIsRejectedAndNothingIsPublished() {
        val payload = "kani desktop installer".toByteArray()
        val declared = asset(payload)

        // The transport ends early: a truncated installer can still be a valid archive
        // prefix, so a short read must not be accepted.
        val result = stage(payload.copyOf(payload.size - 4), declared)

        assertRejected(result, "size does not match")
    }

    @Test
    fun anOverlongResponseIsStoppedWhileStreamingRatherThanAfterwards() {
        val payload = "kani desktop installer".toByteArray()
        val declared = asset(payload)

        val result = stage(payload + "trailing junk".toByteArray(), declared)

        assertRejected(result, "size does not match")
    }

    @Test
    fun anEndlessResponseCannotFillTheDiskBeforeBeingRejected() {
        val payload = "kani desktop installer".toByteArray()
        val declared = asset(payload)
        // A stream that never ends: it is cut off at the declared size, so the partial
        // file cannot grow without bound.
        val endless = object : InputStream() {
            var served = 0L
            override fun read(): Int {
                served++
                return 'x'.code
            }
        }

        val result = DesktopUpdateStager.stage(directory(), declared) { endless }

        assertRejected(result, "size does not match")
        assertTrue(
            "the stream must be cut off near the declared size, not read forever",
            endless.served <= declared.sizeBytes + DesktopUpdateStager.MAX_ARTIFACT_BYTES / 1024,
        )
    }

    @Test
    fun correctlySizedButTamperedBytesAreRejected() {
        val payload = "kani desktop installer".toByteArray()
        val declared = asset(payload)
        // Same length, different content: only the digest catches this.
        val tampered = payload.copyOf().also { it[0] = 'K'.code.toByte() }

        val result = stage(tampered, declared)

        assertRejected(result, "do not match the signed SHA-256")
    }

    @Test
    fun aDeclaredSizeAboveTheHardCeilingIsRejectedBeforeReadingAnything() {
        var opened = false
        val oversized = ManifestAsset(
            filename = NAME,
            sizeBytes = DesktopUpdateStager.MAX_ARTIFACT_BYTES + 1,
            sha256 = "d".repeat(64),
            os = "linux",
            arch = "x64",
            packageType = "deb",
        )

        val result = DesktopUpdateStager.stage(directory(), oversized) {
            opened = true
            ByteArrayInputStream(ByteArray(0))
        }

        assertRejected(result, "exceeds the")
        assertFalse("an oversized declaration must not start a download", opened)
    }

    @Test
    fun aManifestFilenameContainingAPathIsRejected() {
        val payload = "x".toByteArray()
        for (filename in listOf("../escaped.deb", "nested/kani.deb")) {
            val result = DesktopUpdateStager.stage(
                directory(),
                asset(payload).copy(filename = filename),
            ) { ByteArrayInputStream(payload) }

            assertRejected(result, "must not contain a path")
        }
    }

    @Test
    fun aTransportFailureMidStreamLeavesNothingBehind() {
        val payload = "kani desktop installer".toByteArray()
        val declared = asset(payload)
        val failing = object : InputStream() {
            var served = 0
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (served > 0) throw IOException("connection reset")
                served++
                buffer[offset] = payload[0]
                return 1
            }

            override fun read(): Int = throw IOException("connection reset")
        }

        val result = DesktopUpdateStager.stage(directory(), declared) { failing }

        assertRejected(result, "could not stage the update")
    }

    @Test
    fun aLeftoverPartialFromAnEarlierAttemptIsOverwrittenNotAppendedTo() {
        val payload = "kani desktop installer".toByteArray()
        val stale = directory().resolve(NAME + DesktopUpdateStager.PARTIAL_SUFFIX)
        Files.write(stale, "a much longer stale partial download".toByteArray())

        val result = stage(payload, asset(payload))

        assertTrue("$result", result is DesktopUpdateStager.Result.Published)
        assertArrayEquals(payload, Files.readAllBytes(directory().resolve(NAME)))
        assertNoPartialRemains()
    }

    @Test
    fun republishingReplacesAPreviouslyStagedArtifact() {
        val first = "first installer".toByteArray()
        val second = "second installer!".toByteArray()

        assertTrue(stage(first, asset(first)) is DesktopUpdateStager.Result.Published)
        assertTrue(stage(second, asset(second)) is DesktopUpdateStager.Result.Published)

        assertArrayEquals(second, Files.readAllBytes(directory().resolve(NAME)))
    }

    @Test
    fun theUpdateDirectoryIsCreatedWhenAbsent() {
        val nested = directory().resolve("nested/updates")
        val payload = "kani".toByteArray()

        val result = DesktopUpdateStager.stage(nested, asset(payload)) {
            ByteArrayInputStream(payload)
        }

        assertTrue("$result", result is DesktopUpdateStager.Result.Published)
        assertTrue(Files.isRegularFile(nested.resolve(NAME)))
    }

    @Test
    fun aStreamReturningZeroBytesStillCompletes() {
        val payload = "kani".toByteArray()
        // A source that yields a zero-length read before its data; the copy loop must
        // treat it as "not done" rather than as end-of-stream.
        val stuttering = object : InputStream() {
            var stuttered = false
            val delegate = ByteArrayInputStream(payload)
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (!stuttered) {
                    stuttered = true
                    return 0
                }
                return delegate.read(buffer, offset, length)
            }

            override fun read(): Int = delegate.read()
        }

        val result = DesktopUpdateStager.stage(directory(), asset(payload)) { stuttering }

        assertTrue("$result", result is DesktopUpdateStager.Result.Published)
    }

    private fun stage(bytes: ByteArray, declared: ManifestAsset): DesktopUpdateStager.Result =
        DesktopUpdateStager.stage(directory(), declared) { ByteArrayInputStream(bytes) }

    private fun directory(): Path = temporaryFolder.root.toPath()

    private fun asset(payload: ByteArray) = ManifestAsset(
        filename = NAME,
        sizeBytes = payload.size.toLong(),
        sha256 = sha256(payload),
        os = "linux",
        arch = "x64",
        packageType = "deb",
    )

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun assertRejected(result: DesktopUpdateStager.Result, expectedReason: String) {
        val failed = result as DesktopUpdateStager.Result.Failed
        assertTrue(failed.reason, failed.reason.contains(expectedReason))
        // The final path is the one the confirm step would open; it must not exist.
        assertFalse(
            "a rejected download must publish nothing",
            Files.exists(directory().resolve(NAME)),
        )
        assertNoPartialRemains()
    }

    private fun assertNoPartialRemains() {
        assertFalse(
            "the partial file must never survive",
            Files.exists(directory().resolve(NAME + DesktopUpdateStager.PARTIAL_SUFFIX)),
        )
    }

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        org.junit.Assert.assertArrayEquals(expected, actual)
    }

    private companion object {
        const val NAME = "kani-desktop-linux-x64-0.5.0.deb"
    }
}
