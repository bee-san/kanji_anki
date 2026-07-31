package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.application.HomeUseCases
import dev.bee.kanjianki.application.KaniContainer
import dev.bee.kanjianki.application.SettingsUseCases
import dev.bee.kanjianki.application.StatsUseCases
import dev.bee.kanjianki.application.StudyUseCases
import dev.bee.kanjianki.application.SyncUseCases
import dev.bee.kanjianki.data.desktop.DesktopProfileRepositories
import dev.bee.kanjianki.data.desktop.DesktopStorageLayout
import dev.bee.kanjianki.platform.AppLogger
import dev.bee.kanjianki.platform.DeviceSettingsStore
import dev.bee.kanjianki.platform.SecretPersistence
import dev.bee.kanjianki.platform.info
import dev.bee.kanjianki.platform.desktop.DesktopAppDirectories
import dev.bee.kanjianki.platform.desktop.DesktopAppLifecycle
import dev.bee.kanjianki.platform.desktop.DesktopDeviceSettingsStore
import dev.bee.kanjianki.platform.desktop.DesktopFileAccess
import dev.bee.kanjianki.platform.desktop.DesktopSecretStore
import dev.bee.kanjianki.platform.desktop.DesktopShutdownCoordinator
import java.nio.file.Path
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * The desktop half of Kani's object graph: one open profile's repositories, the
 * shared application use cases over them, and the desktop platform adapters.
 *
 * The Android counterpart is `AndroidKaniContainer`, and the field list is
 * deliberately the same one in the same order. Both hosts satisfy the same
 * [KaniContainer], so a feature reaching for a repository, a device setting, an
 * executor, or the lifecycle gets an identical shape on either — which is what
 * makes "the same shell states render on both hosts" a checkable claim rather
 * than a hope.
 *
 * Two differences from Android are structural, not incidental:
 *
 *  - The repositories arrive pre-assembled as [DesktopProfileRepositories]. This
 *    module cannot name `SqlDatabase`, so it cannot build them itself; that edge
 *    stays inside `:data-desktop`.
 *  - Teardown runs through [DesktopShutdownCoordinator] rather than Android's
 *    `ProcessResourceShutdown` (which is internal to `:app`). The coordinator
 *    runs every step in reverse registration order, catches each failure, and
 *    runs once — so a throwing secret wipe cannot leave the profile lock held,
 *    which on desktop means the next launch reports "profile in use" for a
 *    process that no longer exists.
 */
internal class DesktopKaniContainer(
    private val repositories: DesktopProfileRepositories,
    val profileDir: Path,
    cacheDir: Path,
    private val logger: AppLogger,
) : KaniContainer {
    override val homeRepository = repositories.homeRepository
    override val studyRepository = repositories.studyRepository
    override val statsRepository = repositories.statsRepository
    override val settingsRepository = repositories.settingsRepository
    override val syncRepository = repositories.syncRepository

    override val deviceSettingsStore: DeviceSettingsStore =
        DesktopDeviceSettingsStore.open(
            profileDir.resolve(DesktopDeviceSettingsStore.FILE_NAME),
        )

    /**
     * Session-only unless a qualified OS vault is wired.
     *
     * No vault adapter is tested and wired yet, so this is constructed without
     * one and its persistence reports `SESSION_ONLY`. Passing nothing is the
     * point: the alternative a host is tempted by is a plaintext file, and an
     * AnkiConnect API key sitting on disk in the clear is worse than asking for
     * it again next launch.
     */
    val secretStore = DesktopSecretStore()

    /**
     * Whether a stored AnkiConnect key survives a relaunch.
     *
     * Read from the store rather than hardcoded, so wiring a vault adapter later
     * turns `SECRET_PERSISTENCE` on for the shell without a second edit here.
     */
    val persistsSecrets: Boolean
        get() = secretStore.persistence == SecretPersistence.OS_CREDENTIAL_STORE

    val fileAccess = DesktopFileAccess()

    override val appLifecycle = DesktopAppLifecycle()

    val appDirectories = DesktopAppDirectories.forProfile(
        profileDirectory = profileDir,
        cacheDirectory = cacheDir,
        backupsDirectoryName = DesktopStorageLayout.BACKUPS_DIR_NAME,
    )

    val homeUseCases = HomeUseCases(homeRepository, studyRepository, settingsRepository, syncRepository)
    val settingsUseCases = SettingsUseCases(settingsRepository)
    val statsUseCases = StatsUseCases(statsRepository)
    val studyUseCases = StudyUseCases(studyRepository)
    val syncUseCases = SyncUseCases(syncRepository, studyRepository, settingsRepository)

    override val userIoExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "kani-user-io") }

    override val maintenanceExecutor: ExecutorService =
        Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "kani-maintenance").apply { isDaemon = true }
        }

    /**
     * Reverse-order teardown. The profile database and lock are registered
     * first, so they are released last — after everything that might still be
     * touching them has been told to stop.
     */
    private val shutdown = DesktopShutdownCoordinator(appLifecycle, logger)
        .register("profile") { repositories.close() }
        .register("file-access") { fileAccess.revokeAll() }
        .register("secrets") { secretStore.clearSession() }
        .register("maintenance-executor") { maintenanceExecutor.shutdownNow() }
        .register("user-io-executor") { userIoExecutor.shutdownNow() }

    override fun close() {
        val outcome = shutdown.shutDown()
        if (!outcome.isClean) {
            // Reported, not thrown: the process is exiting either way, and a
            // teardown failure that masks the reason for the teardown is how a
            // crash becomes unexplainable. The coordinator already logged each
            // failure with its cause; this names the set.
            logger.info(
                "Desktop shutdown completed with failures: " +
                    outcome.failures.joinToString { it.first },
            )
        }
    }
}
