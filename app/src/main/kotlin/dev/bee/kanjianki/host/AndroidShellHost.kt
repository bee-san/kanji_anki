package dev.bee.kanjianki.host

import android.os.Handler
import android.os.Looper
import dev.bee.kanjianki.AndroidKaniContainer
import dev.bee.kanjianki.GamesRender
import dev.bee.kanjianki.GamesRuntime
import dev.bee.kanjianki.StudyRouteRender
import dev.bee.kanjianki.StudyRuntime
import dev.bee.kanjianki.hostpresentation.DesktopHomeModels
import dev.bee.kanjianki.hostpresentation.HostProviderStatus
import dev.bee.kanjianki.hostpresentation.HostSyncDriver
import dev.bee.kanjianki.hostpresentation.HostSyncEngine
import dev.bee.kanjianki.hostpresentation.KaniRouteContent
import dev.bee.kanjianki.hostpresentation.KaniRouteLoader
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniEffect
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.PresentationFailure
import dev.bee.kanjianki.presentation.SyncConfirmCopy
import dev.bee.kanjianki.data.SaveMnemonicCommand
import dev.bee.kanjianki.data.SetLocalSuspensionCommand
import dev.bee.kanjianki.shell.ShellEffectHandler
import dev.bee.kanjianki.syncapi.CollectionFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * The Android host's non-Compose wiring: the runtimes, the shared route loader, the
 * effect handler, and the write/drive helpers the scaffold calls.
 *
 * Kept out of the composable so the scaffold stays declarative and this stays a plain
 * object a Robolectric test can build against [AndroidKaniContainer]. The desktop twin
 * is the free functions in `DesktopShellScaffold`; the shared parts (the loader, the
 * runtimes, the shell host) are identical, and only the provider probe and the
 * clipboard/URL effects differ, which is the whole point of the split.
 */
internal class AndroidShellHost(
    private val container: AndroidKaniContainer,
    private val providerProbe: AndroidProviderProbe,
    val effectHandler: ShellEffectHandler,
    val capabilities: PlatformCapabilities,
    /**
     * The host-owned state the composition reads and writes: the launch/restored
     * destination it starts from, warm-launch intents, and the current destination it
     * publishes back for saved instance state.
     */
    val hostState: AndroidHostState = AndroidHostState(),
    /** The OS requests only a live Activity can make; see [AndroidHostRequests]. */
    private val requests: AndroidHostRequests = AndroidHostRequests.None,
    private val clock: () -> Long = System::currentTimeMillis,
    /**
     * Runs a block on the main thread.
     *
     * Injectable so a JVM test can make the sync driver synchronous. The default posts to
     * the main looper rather than checking whether it is already there and running inline:
     * an inline run would let a background progress callback mutate driver state on the
     * engine's thread, which is exactly the hop this exists to prevent.
     */
    private val mainThread: (Runnable) -> Unit = { block -> MAIN_HANDLER.post(block) },
) {
    val studyRuntime: StudyRuntime = StudyRuntime(
        container.studyUseCases,
        writingRecognitionAvailable = PlatformCapability.WRITING_RECOGNITION in capabilities.present,
    )
    val gamesRuntime: GamesRuntime = GamesRuntime(container.homeUseCases)

    private val routeLoader = KaniRouteLoader(
        homeUseCases = container.homeUseCases,
        statsUseCases = container.statsUseCases,
        settingsUseCases = container.settingsUseCases,
        deviceSettings = { container.deviceSettingsStore.snapshot() },
        annotateCapabilities = { items -> runBlocking { container.homeUseCases.annotateCapabilities(items) } },
    )

    fun now(): Long = clock()

    /** A provider failure keeps its own kind, so the retry button is honest. */
    fun classifyFailure(failure: Throwable): PresentationFailure.Kind =
        (failure as? CollectionFailure)?.let { DesktopHomeModels.failureKind(it.kind) }
            ?: PresentationFailure.Kind.UNKNOWN

    suspend fun load(
        destination: KaniDestination,
        studyRender: StudyRouteRender?,
        gamesRender: GamesRender?,
    ): KaniRouteContent = withContext(Dispatchers.IO) {
        val status = providerProbe.probe()
        routeLoader.load(
            destination = destination,
            status = HostProviderStatus(
                readiness = status.readiness,
                message = status.message,
                isReady = status.isReady,
                capabilities = status.capabilities,
            ),
            nowMillis = now(),
            studyRender = studyRender,
            gamesRender = gamesRender,
            // Read here rather than captured at construction: this load may be the one a
            // finished sync triggered, and a stale `true` would leave the progress card up
            // over a committed sync.
            syncing = syncDriver.isSyncing,
        )
    }

    suspend fun driveStudy(action: KaniAction.Study, current: StudyRouteRender?): StudyRouteRender =
        withContext(Dispatchers.IO) {
            when (action) {
                is KaniAction.Study.Grade -> studyRuntime.grade(action.rating, now())
                KaniAction.Study.Continue -> studyRuntime.continueCard(now())
                KaniAction.Study.Undo -> studyRuntime.undo(now())
                KaniAction.Study.Reveal -> current ?: studyRuntime.render()
            }
        }

    fun driveGames(action: KaniAction.Game): GamesRender = when (action) {
        is KaniAction.Game.Start -> gamesRuntime.start(action.modeId, now())
        is KaniAction.Game.Answer -> gamesRuntime.answer(action.answer, now())
        KaniAction.Game.Continue -> gamesRuntime.advance(now())
    }

    suspend fun persistMnemonic(action: KaniAction.SaveMnemonic) {
        container.homeUseCases.saveMnemonic(
            SaveMnemonicCommand(kanji = action.kanji, note = action.note, updatedAtMillis = now()),
        )
    }

    suspend fun persistBrowseChoice(action: KaniAction.Browse, listed: List<String>) {
        val (kanji, studied) = when (action) {
            is KaniAction.Browse.SetStudied -> listOf(action.kanji) to action.studied
            is KaniAction.Browse.SetAllStudied -> listed to action.studied
        }
        if (kanji.isEmpty()) return
        container.homeUseCases.setLocalSuspension(
            SetLocalSuspensionCommand(kanji = kanji, suspended = !studied, updatedAtMillis = now()),
        )
    }

    /**
     * The shared sequencer for the three sync actions.
     *
     * Owned by the host rather than the composition so a configuration change cannot
     * abandon a running sync: recomposition rebuilds route content, and a driver living in
     * `remember` would lose track of an engine that is still writing.
     *
     * The engine runs on the container's io executor — the same single thread the old
     * `ManualSyncCoordinator` used, so two syncs still cannot overlap even if the driver's
     * own single-flight guard were bypassed — and reports back through the main-thread
     * handler, because [HostSyncDriver.progress] and `isSyncing` are read during
     * composition.
     */
    val syncDriver = HostSyncDriver(
        launch = { block -> container.userIoExecutor.execute(block) },
        post = { block -> mainThread(block) },
    )

    /** True while a sync is running, for the shell's `HomeDashboard.syncing`. */
    val isSyncing: Boolean
        get() = syncDriver.isSyncing

    /**
     * Performs the provider actions the shared graph cannot: OS requests, and sync.
     *
     * `Connect` and `Authorize` are the two onboarding buttons whose remedy is
     * platform-specific: on Android, "no provider" means AnkiDroid is not installed (open
     * its download page) and "not authorized" means the database permission was never
     * granted (ask for it). Both are named for the *state* in `OnboardingStep`, so the
     * host is the right place to decide the fix — desktop maps the same two actions to
     * starting Anki and to the AnkiConnect key.
     *
     * The three sync actions go to the shared [HostSyncDriver], which is what keeps
     * "a sync is never started without the user answering the dialog" a property of one
     * shared object rather than of two hosts' conventions. `RequestSync` returns the
     * confirmation for the caller to enqueue as an effect; the other two return null,
     * because nothing needs confirming.
     */
    fun driveProvider(action: KaniAction.Provider, copy: SyncConfirmCopy): KaniEffect.Confirm? =
        when (action) {
            KaniAction.Provider.Connect -> {
                effectHandler.openUrl(AndroidHostRequests.PROVIDER_INSTALL_URL)
                null
            }

            KaniAction.Provider.Authorize -> {
                requests.requestProviderPermission()
                null
            }

            KaniAction.Provider.RequestSync -> syncDriver.request(copy)

            KaniAction.Provider.ConfirmSync -> {
                syncDriver.confirm(syncEngine()) { hostState.requestRefresh() }
                null
            }

            KaniAction.Provider.CancelSync -> {
                syncDriver.cancel()
                null
            }
        }

    /**
     * An engine for one run, reading the settings snapshot as it starts.
     *
     * The repaired-note tag write-back is left unauthorized. That is not an omission: the
     * confirmed note ids come from the Home hand-off the shared graph has no surface for
     * yet, and CLAUDE.md forbids performing the write without that manual confirmation —
     * so until the proposal flow is ported, this host syncs without it rather than
     * authorizing a provider write the user was never shown a count for.
     */
    private fun syncEngine(): HostSyncEngine = AndroidSyncEngineAdapter.of(
        context = container.appContext,
        syncUseCases = container.syncUseCases,
        gateway = container.ankiDroidGateway,
        settings = { runBlocking { container.syncUseCases.loadSettings() } },
    )

    private companion object {
        /**
         * One handler for the process, because a `Handler` is cheap but not free and every
         * host instance would otherwise make its own for the same looper.
         */
        val MAIN_HANDLER: Handler = Handler(Looper.getMainLooper())
    }

    suspend fun persistSettings(action: KaniAction.Settings) {
        val current = container.settingsUseCases.load()
        val command = dev.bee.kanjianki.hostpresentation.DesktopSettingsModel.settingsCommandFor(action, current)
            ?: return
        container.settingsUseCases.save(command)
    }
}
