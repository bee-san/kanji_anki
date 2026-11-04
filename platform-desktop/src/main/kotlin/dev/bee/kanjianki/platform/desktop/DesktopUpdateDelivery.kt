package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.UpdateDelivery
import dev.bee.kanjianki.platform.UpdateDeliveryResult
import dev.bee.kanjianki.platform.UpdatePackageKind
import dev.bee.kanjianki.platform.VerifiedUpdatePackage
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Desktop's [UpdateDelivery]: hands a verified installer to the OS to open.
 *
 * This is the most dangerous call Kani makes. Everything else the platform layer does
 * either reads data or shows a window; this asks the operating system to execute a
 * file. So the type carrying the file is not trusted on its own: [VerifiedUpdatePackage]
 * records a digest, and a digest recorded by whoever built the value proves nothing at
 * the moment of launch. **The bytes on disk are re-hashed here, immediately before the
 * handoff**, because the interesting window is between download-verification and
 * execution — a writable temporary directory is exactly where another process can
 * substitute a file after it passed a check.
 *
 * Four more properties, each closing a specific way this could execute the wrong thing:
 *
 * - **The extension must match the declared [UpdatePackageKind], and the kind must be
 *   this platform's.** Opening a `.deb` on Windows does nothing useful, but opening a
 *   file whose *name* says `.msi` while its kind says `DEB` means one of the two is
 *   lying about what will run.
 * - **A symbolic link is refused.** A link that passed verification can be repointed
 *   afterwards, so the check would apply to one file and the launch to another.
 * - **The path must be a regular file, not a directory or device.** On macOS a `.dmg`
 *   is a file; a directory named `Kani.dmg` is something else pretending.
 * - **Launch failures are a result, never an exception.** An update that cannot be
 *   opened must leave the user where they were, with the installed app still working;
 *   throwing out of a delivery call would take down the window that offered it.
 *
 * The open itself is injected. `Desktop.getDesktop().open` needs a real display and a
 * registered handler, and a test must never actually run an installer — so the seam is
 * the boundary of what this class decides versus what the OS does.
 *
 * Kani does not run the installer with elevated privileges or silently: [openFile]
 * receives the file and the *user* completes the install in their own installer UI.
 * A silent-elevated path would be a way to run arbitrary code as administrator on the
 * strength of a signature check happening somewhere else in the process.
 */
class DesktopUpdateDelivery(
    private val openFile: (Path) -> Boolean,
    private val hostKind: UpdatePackageKind? = currentHostKind(),
    private val digestOf: (Path) -> String = ::sha256Of,
) : UpdateDelivery {
    override fun deliver(update: VerifiedUpdatePackage): UpdateDeliveryResult {
        // Unsupported before failed: a `.deb` on Windows is not a broken update, it is
        // an update for a different platform, and telling the user it "failed" would
        // send them looking for a fault in their own install.
        if (hostKind == null || update.kind != hostKind) return UpdateDeliveryResult.UNSUPPORTED
        if (!hasMatchingExtension(update)) return UpdateDeliveryResult.FAILED

        val file = update.file
        return runCatching {
            if (Files.isSymbolicLink(file)) return UpdateDeliveryResult.FAILED
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                return UpdateDeliveryResult.FAILED
            }
            // Re-hash now, not earlier. Between the download's verification and this
            // call the file sat in a writable directory.
            if (!digestOf(file).equals(update.sha256, ignoreCase = true)) {
                return UpdateDeliveryResult.FAILED
            }
            if (openFile(file)) UpdateDeliveryResult.OPENED else UpdateDeliveryResult.FAILED
        }.getOrDefault(UpdateDeliveryResult.FAILED)
    }

    private fun hasMatchingExtension(update: VerifiedUpdatePackage): Boolean {
        val name = update.file.fileName?.toString()?.lowercase() ?: return false
        return name.endsWith(EXTENSIONS.getValue(update.kind))
    }

    companion object {
        private val EXTENSIONS = mapOf(
            UpdatePackageKind.APK to ".apk",
            UpdatePackageKind.MSI to ".msi",
            UpdatePackageKind.DEB to ".deb",
            UpdatePackageKind.DMG to ".dmg",
        )

        /**
         * The package kind this host can install, or null when it is none of them.
         *
         * Null rather than a guess: an unrecognized `os.name` means Kani does not know
         * which installer format would run, and handing the OS a file chosen by a
         * fallback is how the wrong thing gets executed. `UNSUPPORTED` is honest.
         *
         * `APK` is deliberately absent — no desktop host installs one, and Android does
         * not reach this class.
         */
        fun currentHostKind(
            osName: String = System.getProperty("os.name").orEmpty(),
        ): UpdatePackageKind? {
            val normalized = osName.lowercase()
            return when {
                normalized.startsWith("windows") -> UpdatePackageKind.MSI
                normalized.startsWith("mac") || normalized.startsWith("darwin") ->
                    UpdatePackageKind.DMG
                normalized.startsWith("linux") -> UpdatePackageKind.DEB
                else -> null
            }
        }

        /** The file's lowercase SHA-256, streamed so a large installer is not buffered. */
        fun sha256Of(file: Path): String {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(file).use { stream ->
                val buffer = ByteArray(DIGEST_BUFFER_BYTES)
                while (true) {
                    val read = stream.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { byte ->
                "%02x".format(byte)
            }
        }

        private const val DIGEST_BUFFER_BYTES = 64 * 1024
    }
}
