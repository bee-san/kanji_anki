package dev.bee.kanjianki.updatecore

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Streams a verified desktop update into a private partial file and publishes it
 * atomically (Goal 202).
 *
 * The download half of the update gate. [DesktopUpdatePolicy] establishes *what* may be
 * installed from a signed manifest; this establishes that the bytes on disk are exactly
 * those bytes before anything can open them:
 *
 * - **A private `.partial` file, never the final name.** A half-written file at the final
 *   path is indistinguishable from a complete one, so an interrupted download must not be
 *   able to leave something the confirm step would hand to the OS installer.
 * - **The manifest's exact size is the limit.** Enforced while streaming, not after, so a
 *   response that keeps sending cannot fill the disk before being rejected; a short
 *   response is rejected too, since a truncated installer can still be a valid archive
 *   prefix.
 * - **SHA-256 computed over what was actually written**, not over a re-read (which could
 *   read a file substituted between write and check).
 * - **Published by an atomic move after an fsync.** The final path either does not exist
 *   or is the complete verified artifact — there is no window where it is partial. Kani's
 *   backup path holds the same guarantee and for the same reason.
 *
 * Any failure deletes the partial file and leaves nothing at the final path. Verification
 * failure in particular is not retried in place: the bytes are discarded.
 */
object DesktopUpdateStager {
    /** The suffix of the in-progress file; never a name the confirm step would open. */
    const val PARTIAL_SUFFIX: String = ".partial"

    /**
     * A hard ceiling on any single desktop artifact, independent of the manifest.
     *
     * The manifest's size is authoritative, but it is only trusted after its signature
     * verifies, and this bound also guards the case of a signed-but-absurd size caused by
     * a broken release job. Kani's desktop installers are tens of megabytes.
     */
    const val MAX_ARTIFACT_BYTES: Long = 512L * 1024L * 1024L

    private const val BUFFER_BYTES = 1 shl 16

    sealed interface Result {
        /** [path] is the complete, verified artifact, safe to hand to the installer. */
        data class Published(val path: Path, val sizeBytes: Long) : Result

        /**
          * The bytes did not match the signed manifest, or could not be written. Nothing
          * remains at the final path and the partial file is gone.
          */
        data class Failed(val reason: String) : Result
    }

    /**
     * Streams [open]'s bytes into `[directory]/[asset].filename`, verifying against
     * [asset], and publishes atomically on success.
     *
     * [open] is called once and closed here; it is a lambda rather than a URL so the
     * transport stays out of this module and the whole path is testable offline.
     */
    fun stage(
        directory: Path,
        asset: ManifestAsset,
        open: () -> InputStream,
    ): Result {
        if (asset.sizeBytes > MAX_ARTIFACT_BYTES) {
            return Result.Failed(
                "declared size ${asset.sizeBytes} exceeds the $MAX_ARTIFACT_BYTES byte limit",
            )
        }
        // Basename only: a manifest filename containing a path separator must not be able
        // to write outside the update directory.
        val filename = Path.of(asset.filename).fileName?.toString()
            ?: return Result.Failed("asset filename is not a file name: ${asset.filename}")
        if (filename != asset.filename) {
            return Result.Failed("asset filename must not contain a path: ${asset.filename}")
        }

        val finalPath = directory.resolve(filename)
        val partialPath = directory.resolve(filename + PARTIAL_SUFFIX)
        return try {
            Files.createDirectories(directory)
            // Truncate rather than append: a leftover partial from an earlier attempt
            // must not be mistaken for a resumable prefix of this one.
            val digest = open().use { source ->
                Files.newOutputStream(
                    partialPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                ).use { sink -> copyVerifying(source, sink, asset.sizeBytes) }
            }
            when (digest) {
                null -> fail(partialPath, "downloaded size does not match the signed manifest")
                asset.sha256 -> publish(partialPath, finalPath, asset.sizeBytes)
                else -> fail(partialPath, "downloaded bytes do not match the signed SHA-256")
            }
        } catch (failure: IOException) {
            fail(partialPath, "could not stage the update: ${failure.message}")
        }
    }

    /**
     * Copies at most [expectedSize] bytes and returns their SHA-256, or null when the
     * source was shorter or longer than expected.
     */
    private fun copyVerifying(source: InputStream, sink: OutputStream, expectedSize: Long): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        var written = 0L
        while (true) {
            val read = source.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            // Stop the moment the stream overruns the declared size, so an endless
            // response cannot fill the disk.
            if (written + read > expectedSize) return null
            sink.write(buffer, 0, read)
            digest.update(buffer, 0, read)
            written += read
        }
        if (written != expectedSize) return null
        return digest.digest().toHexString()
    }

    private fun publish(partialPath: Path, finalPath: Path, sizeBytes: Long): Result {
        // Force the bytes to disk before the rename: a rename that outlives its own
        // contents would publish a file the OS has not finished writing.
        FileChannel.open(partialPath, StandardOpenOption.WRITE).use { it.force(true) }
        return try {
            Files.move(
                partialPath,
                finalPath,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            Result.Published(finalPath, sizeBytes)
        } catch (unsupported: AtomicMoveNotSupportedException) {
            // No non-atomic fallback: a copy-then-delete would recreate exactly the
            // partial-file-at-the-final-path window this exists to prevent.
            fail(partialPath, "atomic publication is unavailable here: ${unsupported.message}")
        }
    }

    private fun fail(partialPath: Path, reason: String): Result.Failed {
        // Discard the bytes rather than leave them to be resumed or opened.
        runCatching { Files.deleteIfExists(partialPath) }
        return Result.Failed(reason)
    }

    private fun ByteArray.toHexString(): String {
        val builder = StringBuilder(size * 2)
        for (byte in this) {
            builder.append(Character.forDigit((byte.toInt() shr 4) and 0xF, 16))
            builder.append(Character.forDigit(byte.toInt() and 0xF, 16))
        }
        return builder.toString()
    }
}
