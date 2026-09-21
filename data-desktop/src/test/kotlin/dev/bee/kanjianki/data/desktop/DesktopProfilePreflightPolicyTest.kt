package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.data.desktop.DesktopProfilePreflightPolicy.Decision
import dev.bee.kanjianki.data.desktop.DesktopProfilePreflightPolicy.ProfileDirectoryFacts
import dev.bee.kanjianki.data.desktop.DesktopProfilePreflightPolicy.Refusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopProfilePreflightPolicyTest {
    private fun safe(
        exists: Boolean = true,
        isDirectory: Boolean = true,
        isSymlink: Boolean = false,
        worldWritable: Boolean = false,
        onNetworkShare: Boolean = false,
        supportsAtomicMove: Boolean = true,
        supportsExclusiveLock: Boolean = true,
    ) = ProfileDirectoryFacts(
        exists = exists,
        isDirectory = isDirectory,
        isSymlink = isSymlink,
        worldWritable = worldWritable,
        onNetworkShare = onNetworkShare,
        supportsAtomicMove = supportsAtomicMove,
        supportsExclusiveLock = supportsExclusiveLock,
    )

    @Test
    fun allowsAHardenedExistingDirectory() {
        assertEquals(Decision.Allow, DesktopProfilePreflightPolicy.evaluate(safe()))
        assertTrue(DesktopProfilePreflightPolicy.isAllowed(safe()))
    }

    @Test
    fun allowsAMissingDirectoryThatWillBeCreated() {
        // A not-yet-created directory reports the file-shape flags as false; only
        // the filesystem-capability flags matter until it exists.
        assertTrue(
            DesktopProfilePreflightPolicy.isAllowed(
                safe(exists = false, isDirectory = false),
            ),
        )
    }

    @Test
    fun refusesANonDirectory() {
        assertEquals(
            Decision.Refuse(Refusal.NOT_A_DIRECTORY),
            DesktopProfilePreflightPolicy.evaluate(safe(isDirectory = false)),
        )
    }

    @Test
    fun refusesASymlink() {
        assertEquals(
            Decision.Refuse(Refusal.SYMLINKED),
            DesktopProfilePreflightPolicy.evaluate(safe(isSymlink = true)),
        )
    }

    @Test
    fun refusesAWorldWritableDirectory() {
        assertEquals(
            Decision.Refuse(Refusal.WORLD_WRITABLE),
            DesktopProfilePreflightPolicy.evaluate(safe(worldWritable = true)),
        )
    }

    @Test
    fun refusesANetworkShare() {
        assertEquals(
            Decision.Refuse(Refusal.NETWORK_SHARE),
            DesktopProfilePreflightPolicy.evaluate(safe(onNetworkShare = true)),
        )
    }

    @Test
    fun refusesAFilesystemWithoutAtomicMove() {
        assertEquals(
            Decision.Refuse(Refusal.NO_ATOMIC_MOVE),
            DesktopProfilePreflightPolicy.evaluate(safe(supportsAtomicMove = false)),
        )
        assertFalse(DesktopProfilePreflightPolicy.isAllowed(safe(supportsAtomicMove = false)))
    }

    @Test
    fun refusesAFilesystemWithoutExclusiveLock() {
        assertEquals(
            Decision.Refuse(Refusal.NO_EXCLUSIVE_LOCK),
            DesktopProfilePreflightPolicy.evaluate(safe(supportsExclusiveLock = false)),
        )
    }

    @Test
    fun everyRefusalHasAStableMessage() {
        Refusal.entries.forEach { refusal ->
            val message = DesktopProfilePreflightPolicy.message(refusal)
            assertTrue(refusal.name, message.isNotBlank())
        }
    }
}
