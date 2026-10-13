package dev.bee.kanjianki.data.desktop

/**
 * Resolves Kani's per-OS data / config / cache directories, plus the profile
 * root that holds each profile's UUID directory, database, lock, and backups.
 *
 * Pure path arithmetic: the caller supplies the host OS and an environment
 * lookup, and this returns string paths. Actual directory creation, permission
 * hardening, and locking live in platform code so this stays unit-testable on
 * any host. Layouts follow the platform conventions:
 *   - Windows: `%LOCALAPPDATA%\Kani` (data + cache), `%APPDATA%\Kani` (config)
 *   - macOS:   `~/Library/Application Support/Kani`, `~/Library/Caches/Kani`,
 *              `~/Library/Preferences/Kani`
 *   - Linux:   XDG (`$XDG_DATA_HOME`, `$XDG_CONFIG_HOME`, `$XDG_CACHE_HOME`)
 *              with the standard `~/.local/share`, `~/.config`, `~/.cache`
 *              fallbacks.
 */
object DesktopStorageLayout {
    const val APP_DIR_NAME = "Kani"
    const val PROFILES_DIR_NAME = "profiles"
    const val BACKUPS_DIR_NAME = "backups"
    const val DATABASE_FILE_NAME = "kanji_anki_simple.db"
    const val LOCK_FILE_NAME = "profile.lock"

    enum class Os { WINDOWS, MACOS, LINUX }

    /** Resolved top-level directories for one host. Paths use [separator]. */
    data class Directories(
        val dataDir: String,
        val configDir: String,
        val cacheDir: String,
        val separator: Char,
    )

    /**
     * @param os host OS.
     * @param env environment lookup (e.g. `System::getenv`).
     * @param userHome the user home directory (e.g. `System.getProperty("user.home")`).
     */
    fun directories(
        os: Os,
        env: (String) -> String?,
        userHome: String,
    ): Directories {
        val home = userHome.trimEnd('/', '\\').ifEmpty { defaultHome(os) }
        return when (os) {
            Os.WINDOWS -> {
                val local = env("LOCALAPPDATA")?.trimEnd('\\', '/')?.ifEmpty { null }
                    ?: join('\\', home, "AppData", "Local")
                val roaming = env("APPDATA")?.trimEnd('\\', '/')?.ifEmpty { null }
                    ?: join('\\', home, "AppData", "Roaming")
                Directories(
                    dataDir = join('\\', local, APP_DIR_NAME),
                    configDir = join('\\', roaming, APP_DIR_NAME),
                    cacheDir = join('\\', local, APP_DIR_NAME, "cache"),
                    separator = '\\',
                )
            }
            Os.MACOS -> Directories(
                dataDir = join('/', home, "Library", "Application Support", APP_DIR_NAME),
                configDir = join('/', home, "Library", "Preferences", APP_DIR_NAME),
                cacheDir = join('/', home, "Library", "Caches", APP_DIR_NAME),
                separator = '/',
            )
            Os.LINUX -> Directories(
                dataDir = join('/', xdg(env, "XDG_DATA_HOME", home, ".local", "share"), APP_DIR_NAME),
                configDir = join('/', xdg(env, "XDG_CONFIG_HOME", home, ".config"), APP_DIR_NAME),
                cacheDir = join('/', xdg(env, "XDG_CACHE_HOME", home, ".cache"), APP_DIR_NAME),
                separator = '/',
            )
        }
    }

    /** The directory that holds all profile UUID directories, under data. */
    fun profilesRoot(directories: Directories): String =
        join(directories.separator, directories.dataDir, PROFILES_DIR_NAME)

    /** One profile's directory, `<profilesRoot>/<uuid>`. */
    fun profileDir(directories: Directories, profileId: String): String {
        require(isValidProfileId(profileId)) { "invalid profile id: $profileId" }
        return join(directories.separator, profilesRoot(directories), profileId)
    }

    fun databaseFile(directories: Directories, profileId: String): String =
        join(directories.separator, profileDir(directories, profileId), DATABASE_FILE_NAME)

    fun lockFile(directories: Directories, profileId: String): String =
        join(directories.separator, profileDir(directories, profileId), LOCK_FILE_NAME)

    fun backupsDir(directories: Directories, profileId: String): String =
        join(directories.separator, profileDir(directories, profileId), BACKUPS_DIR_NAME)

    /**
     * Profile ids are opaque UUID directory names. Accept canonical
     * lowercase-hex 8-4-4-4-12 UUIDs; reject anything that could escape the
     * profiles root (path separators, `..`, empty).
     */
    fun isValidProfileId(profileId: String): Boolean = UUID_PATTERN.matches(profileId)

    private fun xdg(env: (String) -> String?, key: String, home: String, vararg fallback: String): String {
        val value = env(key)?.trimEnd('/')?.ifEmpty { null }
        // XDG requires an absolute path; a relative value is ignored per spec.
        return if (value != null && value.startsWith('/')) value else join('/', home, *fallback)
    }

    private fun defaultHome(os: Os): String = if (os == Os.WINDOWS) "C:\\Users\\Default" else "/root"

    private fun join(separator: Char, first: String, vararg rest: String): String =
        buildString {
            append(first.trimEnd(separator))
            for (segment in rest) {
                append(separator)
                append(segment.trim(separator))
            }
        }

    private val UUID_PATTERN =
        Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
}
