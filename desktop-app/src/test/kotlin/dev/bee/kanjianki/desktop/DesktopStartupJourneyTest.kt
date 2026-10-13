package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.StoreResult
import dev.bee.kanjianki.data.desktop.DesktopProfileOpener
import dev.bee.kanjianki.data.desktop.DesktopProfileRepositories
import dev.bee.kanjianki.data.desktop.DesktopStorageLayout
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The desktop startup journey over a real profile: first launch, configure, restart,
 * and clean exit, with the persisted state proved across the restart.
 *
 * This is Goal 200's journey gate reduced to the part that can be *proved* rather than
 * observed. It drives the real SQLite profile, the real migration, the real repositories
 * and the real use cases — no fakes below the window — and stops short of Compose,
 * because a window needs a display and the installed-image smoke gate already covers
 * "does the packaged UI render".
 *
 * The restart is the point. Every assertion that matters here is of the form "close
 * everything, reopen from disk, and find it still true": an in-process test that only
 * wrote and read back through one open connection would pass even if nothing were
 * durable, which is precisely the failure a journey test exists to catch.
 */
class DesktopStartupJourneyTest {
    private val roots = ArrayList<Path>()

    @After
    fun tearDown() {
        roots.asReversed().forEach { root ->
            if (!Files.exists(root)) return@forEach
            Files.walk(root).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    private fun profile(): Path =
        Files.createTempDirectory("kani-desktop-journey-").also { roots.add(it) }

    /** Opens the profile, runs [block] against the container, and closes everything. */
    private fun <T> session(
        profileDir: Path,
        nowMillis: Long,
        block: (DesktopKaniContainer, DesktopProfileRepositories) -> T,
    ): T =
        runBlocking {
            val opened = DesktopProfileOpener.open(profileDir)
            assertTrue(
                "the journey needs a real opened profile, got ${opened::class.simpleName}",
                opened is DesktopProfileOpener.Result.Opened,
            )
            val repositories = DesktopProfileRepositories.of(
                opened as DesktopProfileOpener.Result.Opened,
                clock = { nowMillis },
            )
            // `use`, so the lock and the database are released the way a clean exit
            // releases them. A leaked lock would make the next open in this same test
            // fail, which is exactly the signal we want if shutdown regresses.
            repositories.use { bundle ->
                val container = DesktopKaniContainer(
                    repositories = bundle,
                    profileDir = profileDir,
                    cacheDir = profileDir.resolve("cache"),
                    logger = dev.bee.kanjianki.platform.AppLogger.NONE,
                )
                block(container, bundle)
            }
        }

    @Test
    fun firstLaunchCreatesTheProfileAndASecondLaunchFindsItUnchanged() {
        val profileDir = profile()

        val created = session(profileDir, NOW) { _, bundle -> bundle.schema }
        // A fresh directory must report CREATED, not UNCHANGED: the difference is how
        // the host decides whether to show onboarding.
        assertEquals(DesktopProfileRepositories.SchemaSummary.Kind.CREATED, created.kind)
        assertTrue(Files.isRegularFile(profileDir.resolve(DesktopStorageLayout.DATABASE_FILE_NAME)))

        val reopened = session(profileDir, NOW) { _, bundle -> bundle.schema }
        // Reopening must not migrate again. A CREATED here would mean the first launch
        // did not persist the schema version, and every start would look like a first.
        assertEquals(DesktopProfileRepositories.SchemaSummary.Kind.UNCHANGED, reopened.kind)
        assertEquals(created.toVersion, reopened.toVersion)
    }

    @Test
    fun aSettingSurvivesACleanExitAndRestart() {
        val profileDir = profile()

        // A theme the default is not, so "it persisted" cannot be confused with
        // "it was never written and the default happens to match".
        val chosen = KaniThemeChoice.entries.first { it != KaniThemeChoice.SYSTEM }

        session(profileDir, NOW) { container, _ ->
            runBlocking {
                container.settingsRepository.save(SettingsSaveCommand.Theme(chosen))
            }
        }

        // Reopened from disk, in a new session with a new connection.
        val after = session(profileDir, NOW + 1) { container, _ ->
            runBlocking { container.settingsRepository.load() }
        }
        assertTrue(after is StoreResult.Ok)
        assertEquals(chosen, (after as StoreResult.Ok).value.themeChoice)
    }

    @Test
    fun theProfileLockIsReleasedOnCleanExitSoTheNextLaunchSucceeds() {
        val profileDir = profile()

        repeat(3) { attempt ->
            val summary = session(profileDir, NOW + attempt) { _, bundle -> bundle.schema }
            assertNotNull("launch $attempt must open the profile", summary)
        }

        // Three sequential launches only work if each released the lock. A held lock
        // would fail the second open, and a test that opened once could not tell.
        assertTrue(Files.isRegularFile(profileDir.resolve(DesktopStorageLayout.DATABASE_FILE_NAME)))
    }

    @Test
    fun aSecondConcurrentLaunchIsRefusedRatherThanCorruptingTheProfile() {
        val profileDir = profile()

        runBlocking {
            val first = DesktopProfileOpener.open(profileDir)
            assertTrue(first is DesktopProfileOpener.Result.Opened)
            (first as DesktopProfileOpener.Result.Opened).use {
                // Two processes writing one SQLite profile is how a collection gets
                // corrupted, so the second must be refused while the first holds it.
                val second = DesktopProfileOpener.open(profileDir)
                // Named, not merely "not Opened": a refusal and an IO failure are both
                // "not opened", and only the first means the lock did its job. Asserting
                // the weaker form would pass if concurrent opens started failing for an
                // unrelated reason.
                assertTrue(
                    "a concurrent open must be refused by the lock, got " +
                        "${second::class.simpleName}",
                    second is DesktopProfileOpener.Result.Refused ||
                        second == DesktopProfileOpener.Result.LockUnavailable,
                )
            }
        }
    }

    @Test
    fun anEmptyProfilePresentsAnEmptyQueueRatherThanFailing() {
        val profileDir = profile()

        val snapshot = session(profileDir, NOW) { container, _ ->
            runBlocking { container.studyUseCases.loadQueue(NOW) }
        }

        // A first launch has synced nothing, and the honest answer is an empty queue.
        // Throwing here would mean the app cannot start before its first sync.
        assertNotNull(snapshot)
        assertTrue(snapshot.studyItems.isEmpty())
        assertTrue(snapshot.activeRows.isEmpty())
    }

    @Test
    fun homeLoadsOnAFreshProfileWithoutASync() {
        val profileDir = profile()

        val route = session(profileDir, NOW) { container, _ ->
            runBlocking { container.homeUseCases.loadRoute(NOW) }
        }

        // The route that renders immediately after install, before any provider exists.
        assertNotNull(route)
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
