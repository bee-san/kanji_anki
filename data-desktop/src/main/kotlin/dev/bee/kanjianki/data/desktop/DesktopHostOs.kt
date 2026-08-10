package dev.bee.kanjianki.data.desktop

/**
 * Maps a JVM `os.name` to the [DesktopStorageLayout.Os] whose directory layout
 * applies.
 *
 * `os.name` is free-form vendor text — "Mac OS X", "macOS", "Windows 11",
 * "Linux", "FreeBSD", "SunOS" — so the match is by prefix and the default is
 * Linux. Defaulting matters more than it looks: on an unrecognized Unix, Linux's
 * XDG layout is right, whereas refusing to start or guessing Windows would put
 * the profile somewhere the user cannot find or cannot write.
 *
 * This lives in `:data-desktop` next to the layout it selects, and is one
 * function rather than an inline `startsWith` at each call site, because the
 * composition root, the backup paths, and the diagnostics report all have to
 * agree on which OS this is — three copies of the prefix list is three chances
 * to disagree about macOS.
 */
object DesktopHostOs {
    /** The layout for [osName], defaulting to [DesktopStorageLayout.Os.LINUX]. */
    fun of(osName: String?): DesktopStorageLayout.Os {
        val name = osName.orEmpty().trim()
        return when {
            name.startsWith("Windows", ignoreCase = true) -> DesktopStorageLayout.Os.WINDOWS
            name.startsWith("Mac", ignoreCase = true) -> DesktopStorageLayout.Os.MACOS
            name.startsWith("Darwin", ignoreCase = true) -> DesktopStorageLayout.Os.MACOS
            else -> DesktopStorageLayout.Os.LINUX
        }
    }

    /** The layout for the running JVM. */
    fun current(): DesktopStorageLayout.Os = of(System.getProperty("os.name"))
}
