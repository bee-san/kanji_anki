package dev.bee.kanjianki.data.desktop

/**
 * Decides whether a desktop profile directory is safe to open, given probed
 * filesystem facts. Pure policy: the caller supplies the [ProfileDirectoryFacts]
 * (gathered by platform I/O), and this returns an allow/refuse decision with a
 * stable reason. Kani refuses to open an internal profile through an unsafe
 * symlink, a world-writable directory, or a filesystem that cannot satisfy the
 * required lock / atomic-move preflight; network-share profiles are unsupported.
 */
object DesktopProfilePreflightPolicy {
    /** Observed facts about a profile directory and its filesystem. */
    data class ProfileDirectoryFacts(
        val exists: Boolean,
        val isDirectory: Boolean,
        val isSymlink: Boolean,
        val worldWritable: Boolean,
        val onNetworkShare: Boolean,
        val supportsAtomicMove: Boolean,
        val supportsExclusiveLock: Boolean,
    )

    enum class Refusal {
        NOT_A_DIRECTORY,
        SYMLINKED,
        WORLD_WRITABLE,
        NETWORK_SHARE,
        NO_ATOMIC_MOVE,
        NO_EXCLUSIVE_LOCK,
    }

    sealed interface Decision {
        /** The directory is safe to create (if absent) and open. */
        data object Allow : Decision

        data class Refuse(val reason: Refusal) : Decision
    }

    /**
     * A missing directory is allowed (it will be created with hardened
     * permissions before use). An existing directory must be a real directory,
     * not a symlink, not world-writable, not on a network share, and its
     * filesystem must support atomic move and exclusive locking. Reasons are
     * checked in a fixed order so the message is deterministic.
     */
    fun evaluate(facts: ProfileDirectoryFacts): Decision {
        if (facts.exists) {
            if (!facts.isDirectory) return Decision.Refuse(Refusal.NOT_A_DIRECTORY)
            if (facts.isSymlink) return Decision.Refuse(Refusal.SYMLINKED)
            if (facts.worldWritable) return Decision.Refuse(Refusal.WORLD_WRITABLE)
        }
        if (facts.onNetworkShare) return Decision.Refuse(Refusal.NETWORK_SHARE)
        if (!facts.supportsAtomicMove) return Decision.Refuse(Refusal.NO_ATOMIC_MOVE)
        if (!facts.supportsExclusiveLock) return Decision.Refuse(Refusal.NO_EXCLUSIVE_LOCK)
        return Decision.Allow
    }

    fun isAllowed(facts: ProfileDirectoryFacts): Boolean = evaluate(facts) is Decision.Allow

    /** A stable, user-facing reason string for a refusal. */
    fun message(refusal: Refusal): String = when (refusal) {
        Refusal.NOT_A_DIRECTORY -> "The Kani profile location is not a directory."
        Refusal.SYMLINKED -> "The Kani profile location is a symbolic link, which is not allowed."
        Refusal.WORLD_WRITABLE -> "The Kani profile directory is world-writable, which is not allowed."
        Refusal.NETWORK_SHARE -> "Kani profiles on network shares are not supported."
        Refusal.NO_ATOMIC_MOVE -> "The Kani profile filesystem does not support atomic file replacement."
        Refusal.NO_EXCLUSIVE_LOCK -> "The Kani profile filesystem does not support the required exclusive lock."
    }
}
