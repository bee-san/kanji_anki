package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.application.DesktopContainerLifecycle
import dev.bee.kanjianki.data.desktop.DesktopHostOs
import dev.bee.kanjianki.data.desktop.DesktopProfileOpener
import dev.bee.kanjianki.data.desktop.DesktopProfilePreflightPolicy
import dev.bee.kanjianki.data.desktop.DesktopProfileRegistryStore
import dev.bee.kanjianki.data.desktop.DesktopProfileRepositories
import dev.bee.kanjianki.data.desktop.DesktopStagedRestoreApplier
import dev.bee.kanjianki.data.desktop.DesktopStorageLayout
import dev.bee.kanjianki.platform.AppLogger
import dev.bee.kanjianki.platform.info
import dev.bee.kanjianki.platform.warning
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import kotlinx.coroutines.runBlocking

/**
 * Brings up one desktop profile and hands the resulting container to a UI.
 *
 * This is [DesktopContainerLifecycle]'s first production caller, and the reason
 * that contract exists rather than a sequence of calls inline: the four stages
 * have to happen in this order and unwind in the opposite one.
 *
 *  1. **Lock.** Preflight the profile directory, provision it, take the
 *     exclusive lock. A second Kani on the same profile is refused here.
 *  2. **Staged restore.** Only now — with the lock held and no connection open —
 *     may a validated restore replace the database file.
 *  3. **Open data.** The migrated database plus the five repositories, wrapped in
 *     [DesktopKaniContainer].
 *  4. **Start services.** Long-running work that needs the container.
 *
 * Startup refusals are values, not exceptions ([Outcome.Blocked]), because every
 * one of them is a thing the user can act on: another Kani is open, the profile
 * is on a network share, a restore needs manual recovery. A stack trace would
 * make each of those look like a crash.
 */
internal object DesktopStartup {
    /** What a launch attempt produced. */
    internal sealed interface Outcome<out T> {
        /** The UI ran and returned [value]; every resource has been released. */
        data class Ran<out T>(val value: T) : Outcome<T>

        /**
         * Startup stopped before any data was opened. [message] is user-facing
         * and names what to fix.
         */
        data class Blocked(val reason: Reason, val message: String) : Outcome<Nothing>

        enum class Reason {
            /** Another Kani process holds this profile. */
            PROFILE_IN_USE,

            /** The profile directory or its filesystem is unsuitable. */
            PROFILE_REFUSED,

            /** A pending restore left the profile needing manual recovery. */
            RESTORE_BLOCKED,

            /** The profile directory could not be read or created. */
            PROFILE_IO,
        }
    }

    /** Where a profile lives, resolved from the host's own directories. */
    internal data class ProfileLocation(
        val profileDir: Path,
        val cacheDir: Path,
    )

    /**
     * Resolves the profile to open for this host, creating a default one on first
     * run.
     *
     * @param dataRoot when non-null, an explicit profile root that replaces the
     *   per-OS layout. The smoke test supplies its temporary directory this way,
     *   so a smoke launch cannot touch the user's real profile.
     */
    @Throws(IOException::class)
    internal fun resolveProfile(
        dataRoot: Path?,
        os: DesktopStorageLayout.Os = DesktopHostOs.current(),
        env: (String) -> String? = System::getenv,
        userHome: String = System.getProperty("user.home").orEmpty(),
    ): ProfileLocation {
        if (dataRoot != null) {
            // A supplied root is used verbatim as the profile, with its cache
            // inside it. No registry: a throwaway root has exactly one profile
            // and writing a selection file for it would be state that outlives
            // nothing.
            return ProfileLocation(profileDir = dataRoot, cacheDir = dataRoot.resolve("cache"))
        }
        val directories = DesktopStorageLayout.directories(os = os, env = env, userHome = userHome)
        val resolved = DesktopProfileRegistryStore.resolveSelected(directories)
        return ProfileLocation(
            profileDir = resolved.profileDir,
            cacheDir = Paths.get(directories.cacheDir),
        )
    }

    /**
     * Runs [buildPresentation] with a live container for [location], releasing
     * every resource before returning.
     *
     * @param nowMillis stamps the pre-restore safety backup, injected so a test
     *   does not depend on the wall clock.
     */
    internal fun <T> run(
        location: ProfileLocation,
        logger: AppLogger,
        nowMillis: () -> Long = System::currentTimeMillis,
        buildPresentation: (DesktopKaniContainer) -> T,
    ): Outcome<T> {
        val locked = when (val result = DesktopProfileOpener.lock(location.profileDir)) {
            is DesktopProfileOpener.LockResult.Locked -> result
            DesktopProfileOpener.LockResult.LockUnavailable -> return Outcome.Blocked(
                Outcome.Reason.PROFILE_IN_USE,
                "Another copy of Kani is already using this profile.",
            )
            is DesktopProfileOpener.LockResult.Refused -> return Outcome.Blocked(
                Outcome.Reason.PROFILE_REFUSED,
                DesktopProfilePreflightPolicy.message(result.reason),
            )
            is DesktopProfileOpener.LockResult.IoFailure -> return Outcome.Blocked(
                Outcome.Reason.PROFILE_IO,
                "Kani could not open its profile directory: ${result.cause.message}",
            )
        }

        // The restore runs inside the lifecycle's `applyStagedRestore` stage, but
        // a BLOCK_STARTUP verdict has to abort startup as a *value*. Throwing
        // from the stage would unwind the lock correctly but arrive at the caller
        // as a crash, so the stage records the verdict and the lifecycle returns
        // it through `buildPresentation` being skipped.
        var restoreBlock: Outcome.Blocked? = null

        val stages = object : DesktopContainerLifecycle.Stages<DesktopKaniContainer> {
            override fun acquireProfileLock(): AutoCloseable = locked

            override fun applyStagedRestore() {
                when (DesktopStagedRestoreApplier.apply(location.profileDir, nowMillis())) {
                    DesktopStagedRestoreApplier.Result.NO_OP -> Unit
                    DesktopStagedRestoreApplier.Result.APPLIED ->
                        logger.info("Applied a staged desktop restore before opening the profile")
                    // Recoverable: the marker is gone, the live database is
                    // intact, and the next launch retries. Starting is better
                    // than blocking on a restore that may just have raced a
                    // full disk.
                    DesktopStagedRestoreApplier.Result.RETRY_NEEDED ->
                        logger.warning("A staged desktop restore did not complete; it will be retried")
                    DesktopStagedRestoreApplier.Result.BLOCK_STARTUP -> {
                        restoreBlock = Outcome.Blocked(
                            Outcome.Reason.RESTORE_BLOCKED,
                            "A Kani restore did not finish and the profile needs manual recovery. " +
                                "Your previous database and its safety backup have been preserved.",
                        )
                    }
                }
            }

            override fun openData(): DesktopKaniContainer {
                // The lock's ownership transfers into the opened profile here, so
                // the lifecycle must not also close `locked` — and it does not:
                // closing the container closes the repositories, which close the
                // database and then the lock, and `Locked.close()` on an already
                // released lock is a no-op.
                val opened = runBlocking { DesktopProfileOpener.openLocked(locked) }
                val repositories = DesktopProfileRepositories.of(opened)
                if (repositories.schema.isDowngrade) {
                    logger.warning(
                        "This profile was written by a newer Kani " +
                            "(v${repositories.schema.fromVersion} → v${repositories.schema.toVersion})",
                    )
                }
                return DesktopKaniContainer(
                    repositories = repositories,
                    profileDir = location.profileDir,
                    cacheDir = location.cacheDir,
                    logger = logger,
                )
            }

            override fun startServices(container: DesktopKaniContainer): AutoCloseable {
                // Goals 195+ install auto-sync, reminders, and the tray here. The
                // stage exists now so those arrive as a registration rather than
                // as a second startup path.
                container.appLifecycle.onWindowFocused()
                return AutoCloseable { }
            }
        }

        val lifecycle = DesktopContainerLifecycle(stages)
        var presented: T? = null
        var produced = false
        lifecycle.run { container ->
            if (restoreBlock == null) {
                presented = buildPresentation(container)
                produced = true
            }
        }
        restoreBlock?.let { return it }
        @Suppress("UNCHECKED_CAST")
        return if (produced) Outcome.Ran(presented as T) else error("presentation was skipped without a reason")
    }
}
