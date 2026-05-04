package dev.bee.kanjianki.data.fixture

import android.content.Context
import dev.bee.kanjianki.domain.DashboardSnapshot
import dev.bee.kanjianki.domain.HandwritingResult
import dev.bee.kanjianki.domain.HealthSnapshot
import dev.bee.kanjianki.domain.KanjiCompanionRepository
import dev.bee.kanjianki.domain.KanjiDetailSnapshot
import dev.bee.kanjianki.domain.SeedRefreshSnapshot
import dev.bee.kanjianki.domain.SessionMode
import dev.bee.kanjianki.domain.SettingsSnapshot
import dev.bee.kanjianki.domain.StudyOverviewSnapshot
import dev.bee.kanjianki.domain.StudyReviewRequest
import dev.bee.kanjianki.domain.StudyReviewSnapshot
import dev.bee.kanjianki.domain.StudySessionSnapshot
import dev.bee.kanjianki.domain.SyncSnapshot
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class ParityFixtureRepository(
    context: Context,
) : KanjiCompanionRepository {
    private val parser = ParityFixtureParser.fromAsset(context)
    private val settingsMutex = Mutex()
    private var currentSettings = parser.settings()

    override suspend fun getHealth(): HealthSnapshot = parser.health()

    override suspend fun getSettings(): SettingsSnapshot = settingsMutex.withLock { currentSettings }

    override suspend fun updateSettings(settings: SettingsSnapshot): SettingsSnapshot =
        settingsMutex.withLock {
            currentSettings = settings
            currentSettings
        }

    override suspend fun sync(): SyncSnapshot =
        SyncSnapshot(
            sourceCounts = parser.health().sourceCounts,
            dashboard = parser.dashboard(),
        )

    override suspend fun getDashboard(): DashboardSnapshot = parser.dashboard()

    override suspend fun getKanjiDetail(kanji: String): KanjiDetailSnapshot {
        return parser.detail(kanji)
    }

    override suspend fun getStudyOverview(): StudyOverviewSnapshot = parser.baselineOverview()

    override suspend fun refreshSeeds(): SeedRefreshSnapshot = parser.baselineRefresh()

    override suspend fun createSession(mode: SessionMode): StudySessionSnapshot? =
        when (mode) {
            SessionMode.NEW -> parser.happyPathNewSession()
            SessionMode.MIXED -> parser.happyPathMixedSession()
            SessionMode.REVIEW -> parser.happyPathReviewSession()
        }

    override suspend fun submitReview(request: StudyReviewRequest): StudyReviewSnapshot {
        val first = parser.happyPathNewSession()
        val second = parser.happyPathMixedSession()
        val third = parser.happyPathReviewSession()

        return when (request.reviewToken) {
            first.reviewToken -> handleFirstSessionReview(request)
            second.reviewToken -> parser.happyPathSecondReview()
            third.reviewToken -> parser.happyPathThirdReview()
            else -> error("No fixture response exists for review token ${request.reviewToken}.")
        }
    }

    private fun handleFirstSessionReview(request: StudyReviewRequest): StudyReviewSnapshot {
        val handwriting = request.handwritingResult
        return if (!handwriting.passed) {
            if (request.rating != "again") {
                throw IllegalArgumentException(parser.enforcementInvalidReviewError())
            }
            parser.enforcementRetryReview()
        } else {
            parser.happyPathFirstReview()
        }
    }
}
