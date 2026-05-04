package dev.bee.kanjianki.domain

interface KanjiCompanionRepository {
    suspend fun getHealth(): HealthSnapshot

    suspend fun getSettings(): SettingsSnapshot

    suspend fun updateSettings(settings: SettingsSnapshot): SettingsSnapshot

    suspend fun sync(): SyncSnapshot

    suspend fun getDashboard(): DashboardSnapshot

    suspend fun getKanjiDetail(kanji: String): KanjiDetailSnapshot

    suspend fun getStudyOverview(): StudyOverviewSnapshot

    suspend fun refreshSeeds(): SeedRefreshSnapshot

    suspend fun createSession(mode: SessionMode): StudySessionSnapshot?

    suspend fun submitReview(request: StudyReviewRequest): StudyReviewSnapshot
}
