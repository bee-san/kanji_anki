package dev.bee.kanjianki.domain

class GetHealthUseCase(private val repository: KanjiCompanionRepository) {
    suspend operator fun invoke(): HealthSnapshot = repository.getHealth()
}

class GetSettingsUseCase(private val repository: KanjiCompanionRepository) {
    suspend operator fun invoke(): SettingsSnapshot = repository.getSettings()
}

class UpdateSettingsUseCase(private val repository: KanjiCompanionRepository) {
    suspend operator fun invoke(settings: SettingsSnapshot): SettingsSnapshot =
        repository.updateSettings(settings)
}

class SyncUseCase(private val repository: KanjiCompanionRepository) {
    suspend operator fun invoke(): SyncSnapshot = repository.sync()
}

class GetDashboardUseCase(private val repository: KanjiCompanionRepository) {
    suspend operator fun invoke(): DashboardSnapshot = repository.getDashboard()
}

class GetKanjiDetailUseCase(private val repository: KanjiCompanionRepository) {
    suspend operator fun invoke(kanji: String): KanjiDetailSnapshot = repository.getKanjiDetail(kanji)
}

class GetStudyOverviewUseCase(private val repository: KanjiCompanionRepository) {
    suspend operator fun invoke(): StudyOverviewSnapshot = repository.getStudyOverview()
}

class RefreshSeedsUseCase(private val repository: KanjiCompanionRepository) {
    suspend operator fun invoke(): SeedRefreshSnapshot = repository.refreshSeeds()
}

class CreateSessionUseCase(private val repository: KanjiCompanionRepository) {
    suspend operator fun invoke(mode: SessionMode): StudySessionSnapshot? = repository.createSession(mode)
}

class SubmitReviewUseCase(private val repository: KanjiCompanionRepository) {
    suspend operator fun invoke(request: StudyReviewRequest): StudyReviewSnapshot =
        repository.submitReview(request)
}

data class KanjiCompanionUseCases(
    val getHealth: GetHealthUseCase,
    val getSettings: GetSettingsUseCase,
    val updateSettings: UpdateSettingsUseCase,
    val sync: SyncUseCase,
    val getDashboard: GetDashboardUseCase,
    val getKanjiDetail: GetKanjiDetailUseCase,
    val getStudyOverview: GetStudyOverviewUseCase,
    val refreshSeeds: RefreshSeedsUseCase,
    val createSession: CreateSessionUseCase,
    val submitReview: SubmitReviewUseCase,
)

fun buildKanjiCompanionUseCases(repository: KanjiCompanionRepository): KanjiCompanionUseCases =
    KanjiCompanionUseCases(
        getHealth = GetHealthUseCase(repository),
        getSettings = GetSettingsUseCase(repository),
        updateSettings = UpdateSettingsUseCase(repository),
        sync = SyncUseCase(repository),
        getDashboard = GetDashboardUseCase(repository),
        getKanjiDetail = GetKanjiDetailUseCase(repository),
        getStudyOverview = GetStudyOverviewUseCase(repository),
        refreshSeeds = RefreshSeedsUseCase(repository),
        createSession = CreateSessionUseCase(repository),
        submitReview = SubmitReviewUseCase(repository),
    )
