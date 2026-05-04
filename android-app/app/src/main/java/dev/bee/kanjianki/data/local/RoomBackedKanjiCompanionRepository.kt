package dev.bee.kanjianki.data.local

import android.content.Context
import androidx.room.withTransaction
import dev.bee.kanjianki.BuildConfig
import dev.bee.kanjianki.data.ankidroid.AnkiDroidCollectionSnapshot
import dev.bee.kanjianki.data.ankidroid.AnkiDroidGateway
import dev.bee.kanjianki.data.ankidroid.ContentProviderAnkiDroidGateway
import dev.bee.kanjianki.data.sync.PermanentCollectionSyncException
import dev.bee.kanjianki.domain.DashboardSnapshot
import dev.bee.kanjianki.domain.HealthSnapshot
import dev.bee.kanjianki.domain.KanjiCompanionRepository
import dev.bee.kanjianki.domain.KanjiDetailSnapshot
import dev.bee.kanjianki.domain.LatestSyncSnapshot
import dev.bee.kanjianki.domain.SeedRefreshSnapshot
import dev.bee.kanjianki.domain.SessionMode
import dev.bee.kanjianki.domain.SettingsSnapshot
import dev.bee.kanjianki.domain.SourceCounts
import dev.bee.kanjianki.domain.StudyOverviewSnapshot
import dev.bee.kanjianki.domain.StudyReviewRequest
import dev.bee.kanjianki.domain.StudyReviewSnapshot
import dev.bee.kanjianki.domain.StudySessionSnapshot
import dev.bee.kanjianki.domain.SyncSnapshot
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

private const val DEFAULT_PROFILE = "default"
private const val SETTINGS_KEY = "settings"
private const val DASHBOARD_KEY = "dashboard_snapshot"
private const val STUDY_OVERVIEW_KEY = "study_overview_snapshot"
private const val SEED_REFRESH_KEY = "seed_refresh_snapshot"
private const val SESSION_KEY_PREFIX = "study_session:"
private const val REVIEW_KEY = "study_review:last"
private const val SYNC_SOURCE_ANKIDROID = "ankidroid-content-provider"

class RoomBackedKanjiCompanionRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val gateway: AnkiDroidGateway = ContentProviderAnkiDroidGateway(context),
) : KanjiCompanionRepository {
    private val cacheLock = Mutex()

    override suspend fun getHealth(): HealthSnapshot {
        val sourceCounts = loadDashboardFromCache()?.sourceCounts ?: SourceCounts(0, 0)
        return HealthSnapshot(
            version = BuildConfig.VERSION_NAME,
            databasePath = context.getDatabasePath("kanji_anki_android.db").absolutePath,
            webAppPath = "embedded-compose-ui",
            sourceCounts = sourceCounts,
            latestSync = database.syncRunDao().latest()?.toSnapshot(),
        )
    }

    override suspend fun getSettings(): SettingsSnapshot =
        cacheLock.withLock {
            loadJson(SETTINGS_KEY)?.let(RoomCacheCodec::decodeSettings)
                ?: bootstrapSettingsLocked()
        }

    override suspend fun updateSettings(settings: SettingsSnapshot): SettingsSnapshot {
        cacheLock.withLock {
            storeJson(SETTINGS_KEY, RoomCacheCodec.encodeSettings(settings))
        }
        return settings
    }

    override suspend fun sync(): SyncSnapshot =
        cacheLock.withLock {
            runCatching { syncLocked() }
                .onFailure { error ->
                    runCatching {
                        recordSyncFailureLocked(
                            error = error,
                            source = SYNC_SOURCE_ANKIDROID,
                        )
                    }
                }
                .getOrThrow()
        }

    override suspend fun getDashboard(): DashboardSnapshot =
        cacheLock.withLock {
            loadDashboardFromCache() ?: emptyDashboardLocked()
        }

    override suspend fun getKanjiDetail(kanji: String): KanjiDetailSnapshot =
        cacheLock.withLock {
            loadStudyDetailLocked(kanji)
        }

    override suspend fun getStudyOverview(): StudyOverviewSnapshot =
        cacheLock.withLock {
            ensureStudyItemsLocked()
            buildStudyOverviewLocked()
        }

    override suspend fun refreshSeeds(): SeedRefreshSnapshot =
        cacheLock.withLock {
            if (!hasCachedDashboardLocked()) {
                val refresh = AndroidDefaults.emptySeedRefresh()
                val overview = AndroidDefaults.emptyOverview()
                storeJson(SEED_REFRESH_KEY, RoomCacheCodec.encodeSeedRefresh(refresh))
                storeJson(STUDY_OVERVIEW_KEY, RoomCacheCodec.encodeStudyOverview(overview))
                refresh
            } else {
                reseedStudyFromDashboardLocked().first
            }
        }

    override suspend fun createSession(mode: SessionMode): StudySessionSnapshot? =
        cacheLock.withLock {
            ensureStudyItemsLocked()
            val nowTs = nowTs()
            val items = database.studyDao().itemsForProfile(DEFAULT_PROFILE)
            val currentItem = LocalStudyState.selectSessionItem(items, mode, nowTs) ?: return@withLock null
            val task = LocalStudyState.describeTask(currentItem)
            val expectedPromptType = task.promptType
            val sessionItem = if (
                currentItem.activeReviewToken.isNullOrBlank() ||
                currentItem.activePromptType != expectedPromptType
            ) {
                val updatedItem = currentItem.copy(
                    activeReviewToken = UUID.randomUUID().toString(),
                    activePromptType = expectedPromptType,
                    activeSessionIssuedTs = nowTs,
                    firstIntroducedTs = currentItem.firstIntroducedTs
                        ?: if (currentItem.itemStatus == "new") nowTs else null,
                    updatedTs = nowTs,
                )
                database.studyDao().upsertItem(updatedItem)
                updatedItem
            } else {
                currentItem
            }
            val detail = loadStudyDetailLocked(sessionItem.kanji)
            val session = LocalStudyState.buildSession(
                item = sessionItem,
                detail = detail,
                reviewToken = sessionItem.activeReviewToken.orEmpty(),
            )
            storeJson(
                SESSION_KEY_PREFIX + mode.wireValue,
                RoomCacheCodec.encodeStudySession(session),
            )
            session
        }

    override suspend fun submitReview(request: StudyReviewRequest): StudyReviewSnapshot =
        cacheLock.withLock {
            ensureStudyItemsLocked()

            database.studyDao().reviewLog(DEFAULT_PROFILE, request.reviewToken)?.let { existingReview ->
                val currentItem = database.studyDao().item(DEFAULT_PROFILE, request.kanji)
                    ?: error("No study item exists for kanji ${request.kanji}.")
                val overview = buildStudyOverviewLocked()
                val duplicateResponse = LocalStudyState.buildReviewSnapshot(
                    item = currentItem,
                    binaryOutcome = if (LocalStudyState.isPassRating(existingReview.srsRating)) {
                        "pass"
                    } else {
                        "fail"
                    },
                    reviewedAt = LocalStudyState.toIso(existingReview.reviewedTs),
                    overviewDueCount = overview.dueCount,
                )
                storeJson(REVIEW_KEY, RoomCacheCodec.encodeStudyReview(duplicateResponse))
                return@withLock duplicateResponse
            }

            val currentItem = database.studyDao().item(DEFAULT_PROFILE, request.kanji)
                ?: error("No study item exists for kanji ${request.kanji}.")
            require(currentItem.activeReviewToken == request.reviewToken) {
                "reviewToken does not match the active Study session for this kanji."
            }
            val expectedPromptType = currentItem.activePromptType
                ?: LocalStudyState.describeTask(currentItem).promptType
            require(expectedPromptType == request.promptType.trim().lowercase()) {
                "promptType does not match the active Study session for this kanji."
            }

            val reviewResult = LocalStudyState.applyReview(currentItem, request, nowTs())
            database.studyDao().upsertItem(reviewResult.updatedItem)
            val overview = buildStudyOverviewLocked()
            val response = LocalStudyState.buildReviewSnapshot(
                item = reviewResult.updatedItem,
                binaryOutcome = reviewResult.binaryOutcome,
                reviewedAt = reviewResult.reviewedAt,
                overviewDueCount = overview.dueCount,
            )
            storeJson(REVIEW_KEY, RoomCacheCodec.encodeStudyReview(response))
            cacheReviewLogLocked(request, reviewResult)
            response
        }

    private suspend fun bootstrapSettingsLocked(): SettingsSnapshot {
        val settings = AndroidDefaults.settings()
        storeJson(SETTINGS_KEY, RoomCacheCodec.encodeSettings(settings))
        return settings
    }

    private suspend fun syncLocked(): SyncSnapshot {
        val settings = ensureSettingsLocked()
        val gatewayStatus = gateway.getStatus()
        if (!gatewayStatus.installed) {
            throw PermanentCollectionSyncException(
                "${gatewayStatus.message} Install AnkiDroid before syncing this app.",
            )
        }
        if (!gatewayStatus.permissionGranted) {
            throw PermanentCollectionSyncException(
                "${gatewayStatus.message} Grant the AnkiDroid runtime permission, then sync again.",
            )
        }

        val gatewaySnapshot = gateway.readCollectionSnapshot(settings)
        val payload = syncFromGatewayLocked(settings, gatewaySnapshot)
        reseedStudyFromDashboardLocked()
        return payload
    }

    private suspend fun ensureSettingsLocked(): SettingsSnapshot =
        loadJson(SETTINGS_KEY)?.let(RoomCacheCodec::decodeSettings) ?: bootstrapSettingsLocked()

    private suspend fun cacheReviewLogLocked(
        request: StudyReviewRequest,
        review: LocalStudyReviewResult,
    ) {
        database.studyDao().insertReviewLog(
            StudyReviewLogEntity(
                profile = DEFAULT_PROFILE,
                kanji = request.kanji,
                reviewToken = request.reviewToken,
                reviewedTs = review.updatedItem.lastReviewedTs ?: nowTs(),
                promptType = request.promptType,
                srsRating = review.normalizedRating,
                handwritingPassed = request.handwritingResult.passed,
                handwritingScore = request.handwritingResult.score,
                guideLevelBefore = review.guideLevelBefore,
                guideLevelAfter = review.guideLevelAfter,
                hintsUsed = request.hintsUsed,
                reviewPayloadJson = JSONObject()
                    .put("binaryOutcome", review.binaryOutcome)
                    .put("promptType", request.promptType)
                    .put("rating", review.normalizedRating)
                    .put("taskKind", review.task.taskKind)
                    .put("schedulerPhase", review.task.schedulerPhase)
                    .toString(),
            ),
        )
    }

    private suspend fun recordSyncRunLocked(
        sourceCounts: SourceCounts,
        source: String,
    ) {
        val nowTs = nowTs()
        database.syncRunDao().insert(
            SyncRunEntity(
                source = source,
                status = "success",
                startedTs = nowTs,
                finishedTs = nowTs,
                noteCount = sourceCounts.noteCount,
                cardCount = sourceCounts.cardCount,
                errorMessage = null,
            ),
        )
    }

    private suspend fun recordSyncFailureLocked(
        error: Throwable,
        source: String,
    ) {
        val nowTs = nowTs()
        database.syncRunDao().insert(
            SyncRunEntity(
                source = source,
                status = "error",
                startedTs = nowTs,
                finishedTs = nowTs,
                noteCount = 0,
                cardCount = 0,
                errorMessage = error.message ?: error::class.simpleName ?: "sync failed",
            ),
        )
    }

    private suspend fun loadDashboardFromCache(): DashboardSnapshot? =
        loadJson(DASHBOARD_KEY)?.let(RoomCacheCodec::decodeDashboard)

    private suspend fun buildStudyOverviewLocked(): StudyOverviewSnapshot {
        val items = database.studyDao().itemsForProfile(DEFAULT_PROFILE)
        val overview = if (items.isEmpty()) {
            AndroidDefaults.emptyOverview()
        } else {
            LocalStudyState.buildOverview(
                items = items,
                nowTs = nowTs(),
            )
        }
        storeJson(STUDY_OVERVIEW_KEY, RoomCacheCodec.encodeStudyOverview(overview))
        return overview
    }

    private suspend fun ensureStudyItemsLocked() {
        if (database.studyDao().itemsForProfile(DEFAULT_PROFILE).isEmpty()) {
            if (hasCachedDashboardLocked()) {
                reseedStudyFromDashboardLocked()
            } else {
                storeJson(STUDY_OVERVIEW_KEY, RoomCacheCodec.encodeStudyOverview(AndroidDefaults.emptyOverview()))
            }
        }
    }

    private suspend fun loadStudyDetailLocked(kanji: String): KanjiDetailSnapshot {
        database.snapshotDao().problemRow(kanji)?.let { row ->
            if (row.detailJson.isNotBlank()) {
                return RoomCacheCodec.decodeKanjiDetail(row.detailJson)
            }
        }
        throw IllegalStateException(
            "No cached detail exists for kanji $kanji. Sync the AnkiDroid collection first.",
        )
    }

    private suspend fun syncFromGatewayLocked(
        settings: SettingsSnapshot,
        snapshot: AnkiDroidCollectionSnapshot,
    ): SyncSnapshot {
        val nowTs = nowTs()
        val detailLookup = CollectionDetailDerivation.derive(
            settings = settings,
            notes = snapshot.notes,
        )
        val derived = LocalDashboardState.derive(
            snapshot = snapshot,
            settings = settings,
            detailLookup = detailLookup,
            nowTs = nowTs,
        )
        database.withTransaction {
            replaceSourceSnapshotLocked(derived)
            cacheDashboardLocked(derived.dashboard, derived.problemRows)
            recordSyncRunLocked(derived.dashboard.sourceCounts, source = SYNC_SOURCE_ANKIDROID)
        }
        return SyncSnapshot(
            sourceCounts = derived.dashboard.sourceCounts,
            dashboard = derived.dashboard,
        )
    }

    private suspend fun reseedStudyFromDashboardLocked(): Pair<SeedRefreshSnapshot, StudyOverviewSnapshot> {
        val dashboardRows = database.snapshotDao().dashboardRows()
        if (dashboardRows.isEmpty() && !hasCachedDashboardLocked()) {
            val refresh = AndroidDefaults.emptySeedRefresh()
            val overview = AndroidDefaults.emptyOverview()
            storeJson(SEED_REFRESH_KEY, RoomCacheCodec.encodeSeedRefresh(refresh))
            storeJson(STUDY_OVERVIEW_KEY, RoomCacheCodec.encodeStudyOverview(overview))
            return refresh to overview
        }
        val result = LocalStudyState.syncProblemSeeds(
            existingItems = database.studyDao().itemsForProfile(DEFAULT_PROFILE),
            dashboardRows = dashboardRows,
            profile = DEFAULT_PROFILE,
            nowTs = nowTs(),
        )
        database.studyDao().clearProfile(DEFAULT_PROFILE)
        if (result.items.isNotEmpty()) {
            database.studyDao().upsertItems(result.items)
        }
        storeJson(SEED_REFRESH_KEY, RoomCacheCodec.encodeSeedRefresh(result.refresh))
        storeJson(STUDY_OVERVIEW_KEY, RoomCacheCodec.encodeStudyOverview(result.overview))
        return result.refresh to result.overview
    }

    private suspend fun replaceSourceSnapshotLocked(
        derived: LocalDashboardDerivationResult,
    ) {
        database.sourceSnapshotDao().clearCards()
        database.sourceSnapshotDao().clearNotes()
        database.sourceSnapshotDao().clearExpressions()
        if (derived.sourceNotes.isNotEmpty()) {
            database.sourceSnapshotDao().upsertNotes(derived.sourceNotes)
        }
        if (derived.sourceCards.isNotEmpty()) {
            database.sourceSnapshotDao().upsertCards(derived.sourceCards)
        }
        if (derived.expressionSnapshots.isNotEmpty()) {
            database.sourceSnapshotDao().upsertExpressions(derived.expressionSnapshots)
        }
    }

    private suspend fun emptyDashboardLocked(): DashboardSnapshot {
        val settings = ensureSettingsLocked()
        val latestSync = database.syncRunDao().latest()
        val gatewayStatus = runCatching { gateway.getStatus() }.getOrNull()
        val warnings = buildList {
            latestSync?.takeIf { it.status == "error" }
                ?.errorMessage
                ?.takeIf(String::isNotBlank)
                ?.let(::add)
            when {
                gatewayStatus == null ->
                    add("No synced collection snapshot yet. Open Settings, verify AnkiDroid, then run Sync collection now.")

                !gatewayStatus.installed ->
                    add("Install AnkiDroid, then run Sync collection now to populate the dashboard.")

                !gatewayStatus.permissionGranted ->
                    add("Grant the AnkiDroid runtime permission, then run Sync collection now to populate the dashboard.")

                else ->
                    add("No synced collection snapshot yet. Run Sync collection now to populate the dashboard.")
            }
        }
        return AndroidDefaults.emptyDashboard(settings = settings, warnings = warnings)
    }

    private suspend fun hasCachedDashboardLocked(): Boolean =
        database.snapshotDao().dashboardRows().isNotEmpty() || loadDashboardFromCache() != null

    private suspend fun loadJson(key: String): String? =
        database.settingsDao().load(key)?.valueJson

    private suspend fun storeJson(key: String, value: String) {
        database.settingsDao().upsert(
            AppSettingEntity(
                key = key,
                valueJson = value,
                updatedTs = nowTs(),
            ),
        )
    }

    private fun nowTs(): Long = System.currentTimeMillis() / 1000L

    private suspend fun cacheDashboardLocked(
        snapshot: DashboardSnapshot,
        cachedRows: List<ProblemKanjiSnapshotEntity>,
    ) {
        storeJson(DASHBOARD_KEY, RoomCacheCodec.encodeDashboard(snapshot))
        database.snapshotDao().clearProblemRows()
        if (cachedRows.isNotEmpty()) {
            database.snapshotDao().upsertProblemRows(cachedRows)
        }
    }

    private fun SyncRunEntity.toSnapshot(): LatestSyncSnapshot =
        LatestSyncSnapshot(
            source = source,
            status = status,
            startedAt = Instant.ofEpochSecond(startedTs).toString(),
            finishedAt = finishedTs?.let { Instant.ofEpochSecond(it).toString() },
            noteCount = noteCount,
            cardCount = cardCount,
            errorMessage = errorMessage,
        )
}
