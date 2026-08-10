package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.PlatformFileAccess
import dev.bee.kanjianki.platform.PlatformFileReference
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Desktop's [PlatformFileAccess]: resolves a [PlatformFileReference] back to a
 * path and opens a stream on it.
 *
 * On Android the reference's `opaqueId` is a content URI whose permission grant is
 * the access control; there is no such indirection on desktop, where the reference
 * holds a filesystem path. That difference is the entire security problem this
 * class exists to contain, and it is contained by *registration*: a path becomes
 * readable only by being handed to [register], which happens when the user picks it
 * in a file dialog or when Kani itself created it. An arbitrary
 * `PlatformFileReference` fabricated elsewhere — from a restore manifest, a
 * malformed backup, a future sync payload — resolves to nothing.
 *
 * Without that, shared code holding a reference could read or overwrite any file
 * the user can, because the path *is* the capability. With it, the reference is a
 * handle to something the user already chose, which is what the Android contract
 * means too.
 */
class DesktopFileAccess : PlatformFileAccess {
    private val registered = HashMap<String, Path>()

    /**
     * Grants access to [path] and returns the reference naming it.
     *
     * The display name comes from the file name rather than the caller so it
     * cannot disagree with what the user picked, and `PlatformFileReference`
     * rejects a name containing a separator, so a crafted name cannot smuggle a
     * second path component into the UI.
     */
    fun register(path: Path): PlatformFileReference {
        val absolute = path.toAbsolutePath().normalize()
        val name = absolute.fileName?.toString()
        require(!name.isNullOrBlank()) { "cannot register a path with no file name" }
        val reference = PlatformFileReference.create(
            opaqueId = absolute.toString(),
            displayName = name,
        )
        synchronized(registered) { registered[reference.opaqueId] = absolute }
        return reference
    }

    /** Revokes access previously granted by [register]. */
    fun revoke(file: PlatformFileReference) {
        synchronized(registered) { registered.remove(file.opaqueId) }
    }

    /** Drops every grant. Called on shutdown and on profile switch. */
    fun revokeAll() {
        synchronized(registered) { registered.clear() }
    }

    override fun openInput(file: PlatformFileReference): InputStream? {
        val path = resolve(file) ?: return null
        return try {
            Files.newInputStream(path, StandardOpenOption.READ)
        } catch (_: IOException) {
            null
        }
    }

    override fun openOutput(file: PlatformFileReference): OutputStream? {
        val path = resolve(file) ?: return null
        return try {
            Files.newOutputStream(
                path,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            )
        } catch (_: IOException) {
            null
        }
    }

    /**
     * The registered path for [file], or null if it was never granted.
     *
     * Public so a flow that needs the concrete path — a backup snapshot writing through
     * SQLite `VACUUM INTO`, which takes a filesystem path rather than a stream — can get
     * it while still going through the registration guard. A reference fabricated
     * elsewhere resolves to null here exactly as it does for [openInput]/[openOutput].
     */
    fun resolve(file: PlatformFileReference): Path? =
        synchronized(registered) { registered[file.opaqueId] }
}
