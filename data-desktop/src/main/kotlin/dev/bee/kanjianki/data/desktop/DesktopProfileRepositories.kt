package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.data.HomeRepository
import dev.bee.kanjianki.data.SettingsRepository
import dev.bee.kanjianki.data.StatsRepository
import dev.bee.kanjianki.data.StudyRepository
import dev.bee.kanjianki.data.SyncRepository
import dev.bee.kanjianki.data.sql.SchemaTransitionKind
import dev.bee.kanjianki.data.sql.SqlHomeRepository
import dev.bee.kanjianki.data.sql.SqlProjectionInvalidation
import dev.bee.kanjianki.data.sql.SqlSettingsRepository
import dev.bee.kanjianki.data.sql.SqlStatsRepository
import dev.bee.kanjianki.data.sql.SqlStudyRepository
import dev.bee.kanjianki.data.sql.SqlSyncRepository

/**
 * The five repositories of an open desktop profile, plus the schema move that
 * opening it performed.
 *
 * This type exists so the desktop composition root can hold a profile's
 * persistence without naming a `:data-sql` type. `SqlDatabase` and
 * `SchemaTransition` are the two members of
 * [DesktopProfileOpener.Result.Opened] whose types come from `:data-sql`, which
 * the host does not (and by the module boundary must not) see; assembling the
 * repositories here keeps that edge inside `:data-desktop` and leaves the host
 * depending only on the `:data-api` interfaces.
 *
 * Home, Study, and Sync deliberately share one [SqlProjectionInvalidation]:
 * that object is how a study commit or a sync publication tells the Home
 * projection its cached version is stale, so a per-repository instance would
 * silently serve a stale Home forever.
 */
class DesktopProfileRepositories private constructor(
    private val opened: DesktopProfileOpener.Result.Opened,
    val schema: SchemaSummary,
    val homeRepository: HomeRepository,
    val studyRepository: StudyRepository,
    val statsRepository: StatsRepository,
    val settingsRepository: SettingsRepository,
    val syncRepository: SyncRepository,
) : AutoCloseable {
    /** True when the profile directory was hardened to owner-only on open. */
    val hardened: Boolean
        get() = opened.hardened

    /** Releases the profile database and then the exclusive profile lock. */
    override fun close() = opened.close()

    /**
     * The schema move performed while opening, restated without a `:data-sql`
     * type so a host can log or surface it.
     */
    data class SchemaSummary(
        val fromVersion: Int,
        val toVersion: Int,
        val kind: Kind,
    ) {
        /** Whether the profile was written by a newer Kani than this one. */
        val isDowngrade: Boolean
            get() = kind == Kind.DOWNGRADED

        enum class Kind { CREATED, UPGRADED, DOWNGRADED, UNCHANGED }
    }

    companion object {
        /**
         * Assembles the repositories over an already-opened profile. The
         * returned bundle takes ownership of [opened]: closing the bundle
         * closes the database and the lock, and the caller must not close
         * [opened] separately.
         *
         * @param clock supplies "now" to the two repositories that stamp rows
         *   (settings and the stats cache) so tests can pin their timestamps.
         */
        fun of(
            opened: DesktopProfileOpener.Result.Opened,
            clock: () -> Long = System::currentTimeMillis,
        ): DesktopProfileRepositories {
            val database = opened.database
            val invalidation = SqlProjectionInvalidation()
            return DesktopProfileRepositories(
                opened = opened,
                schema = summarize(opened),
                homeRepository = SqlHomeRepository(database, invalidation),
                studyRepository = SqlStudyRepository(database, invalidation),
                statsRepository = SqlStatsRepository(database, clock),
                settingsRepository = SqlSettingsRepository(database, clock),
                syncRepository = SqlSyncRepository(database, invalidation),
            )
        }

        private fun summarize(
            opened: DesktopProfileOpener.Result.Opened,
        ): SchemaSummary = SchemaSummary(
            fromVersion = opened.transition.fromVersion,
            toVersion = opened.transition.toVersion,
            kind = when (opened.transition.kind) {
                SchemaTransitionKind.CREATED -> SchemaSummary.Kind.CREATED
                SchemaTransitionKind.UPGRADED -> SchemaSummary.Kind.UPGRADED
                SchemaTransitionKind.DOWNGRADED -> SchemaSummary.Kind.DOWNGRADED
                SchemaTransitionKind.UNCHANGED -> SchemaSummary.Kind.UNCHANGED
            },
        )
    }
}
