package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.data.SettingsSaveCommand
import dev.bee.kanjianki.data.StudyQueueWriteCommand
import dev.bee.kanjianki.data.sql.MigrationClock
import dev.bee.kanjianki.data.sql.MigrationContext
import dev.bee.kanjianki.data.sql.SchemaTransition
import dev.bee.kanjianki.data.sql.SchemaTransitionKind
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopProfileRepositoriesTest {
    private val temporaryDirectories = ArrayList<Path>()

    @After
    fun tearDown() {
        temporaryDirectories.asReversed().forEach { directory ->
            if (!Files.exists(directory)) return@forEach
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    @Test
    fun everyRepositoryReadsAndWritesTheOpenedProfile() = runBlocking {
        withProfile { repositories ->
            assertTrue(
                repositories.settingsRepository
                    .save(SettingsSaveCommand.StudyAhead(minutes = 45))
                    .isOk(),
            )
            assertEquals(
                45,
                repositories.settingsRepository.load().valueOrNull()?.studyAheadMinutes,
            )
            assertTrue(repositories.homeRepository.loadHome(FIXED_CLOCK).isOk())
            assertTrue(repositories.studyRepository.loadQueue(FIXED_CLOCK).isOk())
            assertTrue(repositories.syncRepository.loadStoredState().isOk())
            val stats = repositories.statsRepository.loadCached(FIXED_CLOCK)
            assertTrue(stats.isOk())
            // A fresh profile has no cached analytics row yet; the point is that
            // the repository is wired to this profile's database, not that it
            // already has content.
            assertNull(stats.valueOrNull())
        }
    }

    @Test
    fun homeStudyAndSyncShareOneProjectionInvalidation() = runBlocking {
        // The load-bearing detail of the bundle. A study write has to make the
        // Home projection's version move, or Home serves a stale snapshot for
        // the rest of the session.
        withProfile { repositories ->
            val before = repositories.homeRepository
                .loadNewCardSortPreviewVersion()
                .valueOrNull()
            assertTrue(
                repositories.studyRepository
                    .replaceQueue(StudyQueueWriteCommand(items = emptyList()))
                    .isOk(),
            )
            val after = repositories.homeRepository
                .loadNewCardSortPreviewVersion()
                .valueOrNull()

            assertTrue("expected $after to advance past $before", after!! > before!!)
        }
    }

    @Test
    fun aFreshProfileReportsItsCreationAndItsHardening() = runBlocking {
        withProfile { repositories ->
            assertEquals(
                DesktopProfileRepositories.SchemaSummary.Kind.CREATED,
                repositories.schema.kind,
            )
            assertEquals(0, repositories.schema.fromVersion)
            assertTrue(repositories.schema.toVersion > 0)
            assertFalse(repositories.schema.isDowngrade)
            assertTrue(repositories.hardened)
        }
    }

    @Test
    fun closingTheBundleReleasesTheDatabaseAndTheProfileLock() = runBlocking {
        val opened = open(tempRoot().resolve("profile"))
        val repositories = DesktopProfileRepositories.of(opened) { FIXED_CLOCK }
        assertTrue(opened.lock.isHeld)

        repositories.close()

        assertFalse(opened.lock.isHeld)
    }

    @Test
    fun everySchemaMoveIsRestatedWithoutADataSqlType() = runBlocking {
        // Exhaustive over the transition kinds rather than over migrations: an
        // unmapped kind would reach a host as the wrong startup story, and only
        // one of the four is reachable from opening a fresh profile.
        val opened = open(tempRoot().resolve("profile"))
        try {
            assertEquals(
                DesktopProfileRepositories.SchemaSummary(
                    fromVersion = 31,
                    toVersion = 31,
                    kind = DesktopProfileRepositories.SchemaSummary.Kind.UNCHANGED,
                ),
                relabel(opened, 31, 31, SchemaTransitionKind.UNCHANGED).schema,
            )
            assertEquals(
                DesktopProfileRepositories.SchemaSummary.Kind.UPGRADED,
                relabel(opened, 30, 31, SchemaTransitionKind.UPGRADED).schema.kind,
            )
            val downgraded = relabel(opened, 32, 31, SchemaTransitionKind.DOWNGRADED).schema
            assertEquals(
                DesktopProfileRepositories.SchemaSummary.Kind.DOWNGRADED,
                downgraded.kind,
            )
            assertTrue(downgraded.isDowngrade)
        } finally {
            opened.close()
        }
    }

    /**
     * A bundle over the same open profile, relabelled with a synthetic schema
     * transition. It shares [opened]'s database and lock, so it is deliberately
     * never closed; the caller closes [opened] once.
     */
    private fun relabel(
        opened: DesktopProfileOpener.Result.Opened,
        fromVersion: Int,
        toVersion: Int,
        kind: SchemaTransitionKind,
    ): DesktopProfileRepositories = DesktopProfileRepositories.of(
        opened.copy(transition = SchemaTransition(fromVersion, toVersion, kind)),
    ) { FIXED_CLOCK }

    private suspend fun withProfile(block: suspend (DesktopProfileRepositories) -> Unit) {
        val repositories = DesktopProfileRepositories.of(
            open(tempRoot().resolve("profile")),
        ) { FIXED_CLOCK }
        try {
            block(repositories)
        } finally {
            repositories.close()
        }
    }

    private suspend fun open(profile: Path): DesktopProfileOpener.Result.Opened {
        val result = DesktopProfileOpener.open(
            profile,
            MigrationContext(clock = MigrationClock { FIXED_CLOCK }),
        )
        assertTrue("expected Opened but was $result", result is DesktopProfileOpener.Result.Opened)
        return result as DesktopProfileOpener.Result.Opened
    }

    private fun tempRoot(): Path {
        val directory = Files.createTempDirectory("kani-desktop-repositories-")
        temporaryDirectories.add(directory)
        return directory
    }

    private companion object {
        const val FIXED_CLOCK = 1_770_050_000_000L
    }
}
