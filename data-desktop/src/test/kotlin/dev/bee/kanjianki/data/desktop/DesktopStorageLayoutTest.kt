package dev.bee.kanjianki.data.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopStorageLayoutTest {
    private val uuid = "0f9a1b2c-3d4e-5f60-7182-93a4b5c6d7e8"

    @Test
    fun windowsUsesLocalAndRoamingAppData() {
        val env = mapOf(
            "LOCALAPPDATA" to "C:\\Users\\aki\\AppData\\Local",
            "APPDATA" to "C:\\Users\\aki\\AppData\\Roaming",
        )
        val dirs = DesktopStorageLayout.directories(DesktopStorageLayout.Os.WINDOWS, env::get, "C:\\Users\\aki")
        assertEquals("C:\\Users\\aki\\AppData\\Local\\Kani", dirs.dataDir)
        assertEquals("C:\\Users\\aki\\AppData\\Roaming\\Kani", dirs.configDir)
        assertEquals("C:\\Users\\aki\\AppData\\Local\\Kani\\cache", dirs.cacheDir)
        assertEquals('\\', dirs.separator)
        assertEquals(
            "C:\\Users\\aki\\AppData\\Local\\Kani\\profiles\\$uuid\\kanji_anki_simple.db",
            DesktopStorageLayout.databaseFile(dirs, uuid),
        )
    }

    @Test
    fun windowsFallsBackWhenEnvMissing() {
        val dirs = DesktopStorageLayout.directories(DesktopStorageLayout.Os.WINDOWS, { null }, "C:\\Users\\aki")
        assertEquals("C:\\Users\\aki\\AppData\\Local\\Kani", dirs.dataDir)
        assertEquals("C:\\Users\\aki\\AppData\\Roaming\\Kani", dirs.configDir)
    }

    @Test
    fun macUsesLibraryLocations() {
        val dirs = DesktopStorageLayout.directories(DesktopStorageLayout.Os.MACOS, { null }, "/Users/aki")
        assertEquals("/Users/aki/Library/Application Support/Kani", dirs.dataDir)
        assertEquals("/Users/aki/Library/Preferences/Kani", dirs.configDir)
        assertEquals("/Users/aki/Library/Caches/Kani", dirs.cacheDir)
        assertEquals('/', dirs.separator)
    }

    @Test
    fun linuxHonoursXdgAndFallsBack() {
        val withXdg = DesktopStorageLayout.directories(
            DesktopStorageLayout.Os.LINUX,
            mapOf(
                "XDG_DATA_HOME" to "/home/aki/data",
                "XDG_CONFIG_HOME" to "/home/aki/cfg",
                "XDG_CACHE_HOME" to "/home/aki/cache",
            )::get,
            "/home/aki",
        )
        assertEquals("/home/aki/data/Kani", withXdg.dataDir)
        assertEquals("/home/aki/cfg/Kani", withXdg.configDir)
        assertEquals("/home/aki/cache/Kani", withXdg.cacheDir)

        val fallback = DesktopStorageLayout.directories(DesktopStorageLayout.Os.LINUX, { null }, "/home/aki")
        assertEquals("/home/aki/.local/share/Kani", fallback.dataDir)
        assertEquals("/home/aki/.config/Kani", fallback.configDir)
        assertEquals("/home/aki/.cache/Kani", fallback.cacheDir)
    }

    @Test
    fun linuxIgnoresRelativeXdgPaths() {
        // XDG spec: relative paths are invalid and must be ignored.
        val dirs = DesktopStorageLayout.directories(
            DesktopStorageLayout.Os.LINUX,
            mapOf("XDG_DATA_HOME" to "relative/data")::get,
            "/home/aki",
        )
        assertEquals("/home/aki/.local/share/Kani", dirs.dataDir)
    }

    @Test
    fun profilePathsNestUnderData() {
        val dirs = DesktopStorageLayout.directories(DesktopStorageLayout.Os.LINUX, { null }, "/home/aki")
        assertEquals("/home/aki/.local/share/Kani/profiles", DesktopStorageLayout.profilesRoot(dirs))
        assertEquals("/home/aki/.local/share/Kani/profiles/$uuid", DesktopStorageLayout.profileDir(dirs, uuid))
        assertEquals("/home/aki/.local/share/Kani/profiles/$uuid/profile.lock", DesktopStorageLayout.lockFile(dirs, uuid))
        assertEquals("/home/aki/.local/share/Kani/profiles/$uuid/backups", DesktopStorageLayout.backupsDir(dirs, uuid))
    }

    /**
     * User data never lives inside a directory an uninstaller owns (Goal 204).
     *
     * Goal 204's data-retention requirement is that uninstalling removes binaries and
     * keeps user data. Most of that has to be tested on a real Windows or macOS host,
     * but the part that decides the outcome is this layout: an uninstaller deletes what
     * it installed, so retention holds if and only if no data path is nested under an
     * install root. That is checkable on any host, and it is the half that a passing
     * install/uninstall run on one machine would not generalize.
     *
     * The Windows case is the one worth stating. Kani installs per-user, and a per-user
     * MSI installs under `%LOCALAPPDATA%\Programs` — while Kani's data directory is
     * `%LOCALAPPDATA%\Kani`. Those are siblings, which is fine, but they share a parent,
     * so a future change to either (an `installationPath`, or moving data under
     * `Programs`) could nest one inside the other and make uninstall destroy the user's
     * collection. Nothing else in the suite would notice.
     */
    @Test
    fun userDataIsNeverNestedInsideAnUninstallerOwnedDirectory() {
        data class Host(
            val os: DesktopStorageLayout.Os,
            val home: String,
            val env: Map<String, String>,
            val installRoots: List<String>,
        )

        val hosts = listOf(
            Host(
                DesktopStorageLayout.Os.WINDOWS,
                "C:\\Users\\aki",
                mapOf(
                    "LOCALAPPDATA" to "C:\\Users\\aki\\AppData\\Local",
                    "APPDATA" to "C:\\Users\\aki\\AppData\\Roaming",
                ),
                // Per-user MSI default, and the per-machine location Kani deliberately
                // does not use — checked too, so switching scope cannot silently nest.
                listOf(
                    "C:\\Users\\aki\\AppData\\Local\\Programs\\Kani",
                    "C:\\Program Files\\Kani",
                ),
            ),
            Host(
                DesktopStorageLayout.Os.MACOS,
                "/Users/aki",
                emptyMap(),
                listOf("/Applications/Kani.app", "/Users/aki/Applications/Kani.app"),
            ),
            Host(
                DesktopStorageLayout.Os.LINUX,
                "/home/aki",
                emptyMap(),
                listOf("/opt/kani", "/usr/lib/kani"),
            ),
        )

        for (host in hosts) {
            val dirs = DesktopStorageLayout.directories(host.os, host.env::get, host.home)
            val dataPaths = listOf(
                dirs.dataDir,
                dirs.configDir,
                dirs.cacheDir,
                DesktopStorageLayout.profilesRoot(dirs),
                DesktopStorageLayout.databaseFile(dirs, uuid),
                DesktopStorageLayout.backupsDir(dirs, uuid),
            )
            for (installRoot in host.installRoots) {
                val prefix = installRoot + dirs.separator
                for (dataPath in dataPaths) {
                    assertFalse(
                        "${host.os}: uninstalling $installRoot would delete $dataPath",
                        dataPath == installRoot || dataPath.startsWith(prefix),
                    )
                }
            }
        }
    }

    @Test
    fun profileIdValidationRejectsTraversal() {
        assertTrue(DesktopStorageLayout.isValidProfileId(uuid))
        assertFalse(DesktopStorageLayout.isValidProfileId(".."))
        assertFalse(DesktopStorageLayout.isValidProfileId("../escape"))
        assertFalse(DesktopStorageLayout.isValidProfileId("a/b"))
        assertFalse(DesktopStorageLayout.isValidProfileId(""))
        assertFalse(DesktopStorageLayout.isValidProfileId("NOTAUUID"))
        val dirs = DesktopStorageLayout.directories(DesktopStorageLayout.Os.LINUX, { null }, "/home/aki")
        assertThrows(IllegalArgumentException::class.java) { DesktopStorageLayout.profileDir(dirs, "../escape") }
    }
}
