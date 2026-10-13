package dev.bee.kanjianki.backup.core

/**
 * The pure cap/reserve arithmetic for streaming a backup restore. The Android
 * validator (and the desktop equivalent) copy a decompressed gzip stream into a
 * staging file while enforcing two bounds: a hard [MAX_DECOMPRESSED_BYTES] cap
 * on total output, and a running free-space budget that must always leave
 * [FREE_SPACE_RESERVE_BYTES] headroom. This holds that logic with no file or
 * SQLite I/O so it is testable without a device.
 */
class BackupSpaceBudget(
    allocatableBytes: Long,
    val reserveBytes: Long = FREE_SPACE_RESERVE_BYTES,
) {
    private var remainingBytes = allocatableBytes.coerceAtLeast(0L)

    /** Bytes still writable before hitting the reserve floor. */
    fun remainingWritableBytes(): Long = (remainingBytes - reserveBytes).coerceAtLeast(0L)

    /** True when [bytesToWrite] fits without breaching the reserve. */
    fun canWrite(bytesToWrite: Int): Boolean =
        remainingBytes >= reserveBytes && remainingBytes - reserveBytes >= bytesToWrite.toLong()

    /** @throws InsufficientBackupStorageException when the write would breach the reserve. */
    fun requireWrite(bytesToWrite: Int) {
        if (!canWrite(bytesToWrite)) {
            throw InsufficientBackupStorageException()
        }
    }

    fun recordWrite(bytesWritten: Int) {
        remainingBytes = (remainingBytes - bytesWritten.toLong()).coerceAtLeast(0L)
    }

    companion object {
        /** Reject a decompressed backup larger than this (matches Android). */
        const val MAX_DECOMPRESSED_BYTES: Long = 512L * 1024L * 1024L

        /** Always leave this much free space on the target filesystem. */
        const val FREE_SPACE_RESERVE_BYTES: Long = 64L * 1024L * 1024L

        /**
         * True when appending [nextChunkBytes] would exceed [MAX_DECOMPRESSED_BYTES].
         * Mirrors the validator's `totalBytes > max - read` guard exactly.
         */
        fun exceedsDecompressedCap(alreadyWritten: Long, nextChunkBytes: Int): Boolean =
            alreadyWritten > MAX_DECOMPRESSED_BYTES - nextChunkBytes.toLong()

        /**
         * Post-write storage-exhaustion re-check: given a probed [allocatableBytes]
         * after an `IOException`, decide whether the failure was genuine storage
         * exhaustion (true) versus an unrelated I/O error (false).
         */
        fun isStorageExhausted(
            allocatableBytes: Long,
            reserveBytes: Long,
            bytesNeeded: Int,
        ): Boolean {
            val allocatable = allocatableBytes.coerceAtLeast(0L)
            return allocatable < reserveBytes || allocatable - reserveBytes < bytesNeeded.toLong()
        }
    }
}

/** Raised when a restore write would leave less than the required free-space reserve. */
class InsufficientBackupStorageException : RuntimeException("Insufficient storage for backup restore")
