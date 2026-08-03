package dev.bee.kanjianki.host

import dev.bee.kanjianki.AndroidKaniContainer
import dev.bee.kanjianki.GamesRender
import dev.bee.kanjianki.GamesRuntime
import dev.bee.kanjianki.StudyRouteRender
import dev.bee.kanjianki.StudyRuntime
import dev.bee.kanjianki.hostpresentation.DesktopHomeModels
import dev.bee.kanjianki.hostpresentation.HostProviderStatus
import dev.bee.kanjianki.hostpresentation.KaniRouteContent
import dev.bee.kanjianki.hostpresentation.KaniRouteLoader
import dev.bee.kanjianki.presentation.KaniAction
import dev.bee.kanjianki.presentation.KaniDestination
import dev.bee.kanjianki.presentation.KaniLaunchRequest
import dev.bee.kanjianki.presentation.PlatformCapabilities
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.PresentationFailure
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
    val launch: KaniLaunchRequest? = null,
    private val clock: () -> Long = System::currentTimeMillis,
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

    suspend fun persistSettings(action: KaniAction.Settings) {
        val current = container.settingsUseCases.load()
        val command = dev.bee.kanjianki.hostpresentation.DesktopSettingsModel.settingsCommandFor(action, current)
            ?: return
        container.settingsUseCases.save(command)
    }
}
