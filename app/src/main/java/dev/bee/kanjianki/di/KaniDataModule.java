package dev.bee.kanjianki.di;

import android.content.Context;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import dev.bee.kanjianki.data.KaniRoomDatabase;
import dev.bee.kanjianki.data.KaniRoomDatabaseFactory;
import dev.bee.kanjianki.data.KaniRoomDatabaseResetPolicy;
import dev.bee.kanjianki.data.RoomStudyQueueMutationGate;
import dev.bee.kanjianki.data.RoomStudyRuntimeOwnershipPolicy;
import dev.bee.kanjianki.data.StudyQueueMutationGate;
import dev.bee.kanjianki.data.repository.RoomSourceMirrorRepository;
import dev.bee.kanjianki.data.repository.RoomSourceMirrorSyncRepository;
import dev.bee.kanjianki.data.repository.RoomStudyDashboardRepository;
import dev.bee.kanjianki.data.repository.RoomStudyKanjiDetailRepository;
import dev.bee.kanjianki.data.repository.RoomStudyKanjiInventoryRepository;
import dev.bee.kanjianki.data.repository.RoomStudyLocalSuspensionRepository;
import dev.bee.kanjianki.data.repository.RoomStudyQueueRepository;
import dev.bee.kanjianki.data.repository.RoomStudySchedulerSettingsRepository;
import dev.bee.kanjianki.data.repository.RoomStudyReviewStatsRepository;
import dev.bee.kanjianki.data.repository.RoomStudyRuntimeSnapshotRepository;
import dev.bee.kanjianki.data.repository.RoomStudyReviewPersistenceRepository;
import dev.bee.kanjianki.data.repository.RoomSyncRunRepository;
import dev.bee.kanjianki.domain.repository.SourceMirrorRepository;
import dev.bee.kanjianki.domain.repository.SourceMirrorSyncRepository;
import dev.bee.kanjianki.domain.repository.StudyDashboardRepository;
import dev.bee.kanjianki.domain.repository.StudyKanjiDetailRepository;
import dev.bee.kanjianki.domain.repository.StudyKanjiInventoryRepository;
import dev.bee.kanjianki.domain.repository.StudyLocalSuspensionRepository;
import dev.bee.kanjianki.domain.repository.StudyQueueRepository;
import dev.bee.kanjianki.domain.repository.StudyReviewPersistenceRepository;
import dev.bee.kanjianki.domain.repository.StudyReviewStatsRepository;
import dev.bee.kanjianki.domain.repository.StudySchedulerSettingsRepository;
import dev.bee.kanjianki.domain.repository.StudyRuntimeSnapshotRepository;
import dev.bee.kanjianki.domain.repository.SyncRunRepository;
import dev.bee.kanjianki.domain.scheduler.ApplyStudyReviewUseCase;
import dev.bee.kanjianki.domain.scheduler.LoadNextStudySessionUseCase;
import dev.bee.kanjianki.domain.scheduler.StudyReviewTransitionEngine;
import dev.bee.kanjianki.domain.scheduler.StudySessionSelector;
import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public final class KaniDataModule {
    private KaniDataModule() {
    }

    @Provides
    @Singleton
    static KaniRoomDatabase provideKaniRoomDatabase(@ApplicationContext Context context) {
        return new KaniRoomDatabaseFactory(KaniRoomDatabaseResetPolicy.ROOM_SANDBOX_DURING_LEGACY_RUNTIME).create(context);
    }

    @Provides
    @Singleton
    static SourceMirrorRepository provideSourceMirrorRepository(KaniRoomDatabase database) {
        return new RoomSourceMirrorRepository(database);
    }

    @Provides
    @Singleton
    static SourceMirrorSyncRepository provideSourceMirrorSyncRepository(
            KaniRoomDatabase database,
            StudyQueueMutationGate studyQueueMutationGate,
            RoomStudyRuntimeOwnershipPolicy ownershipPolicy
    ) {
        return new RoomSourceMirrorSyncRepository(database, studyQueueMutationGate, ownershipPolicy);
    }

    @Provides
    @Singleton
    static SyncRunRepository provideSyncRunRepository(KaniRoomDatabase database) {
        return new RoomSyncRunRepository(database.syncRunDao());
    }

    @Provides
    @Singleton
    static StudyQueueRepository provideStudyQueueRepository(
            KaniRoomDatabase database,
            StudyQueueMutationGate studyQueueMutationGate,
            RoomStudyRuntimeOwnershipPolicy ownershipPolicy
    ) {
        return new RoomStudyQueueRepository(database, studyQueueMutationGate, ownershipPolicy);
    }

    @Provides
    @Singleton
    static StudyDashboardRepository provideStudyDashboardRepository(KaniRoomDatabase database) {
        return new RoomStudyDashboardRepository(database, RoomStudyDashboardRepository.DEFAULT_EXAMPLE_LIMIT);
    }

    @Provides
    @Singleton
    static StudyKanjiInventoryRepository provideStudyKanjiInventoryRepository(KaniRoomDatabase database) {
        return new RoomStudyKanjiInventoryRepository(database);
    }

    @Provides
    @Singleton
    static StudyKanjiDetailRepository provideStudyKanjiDetailRepository(KaniRoomDatabase database) {
        return new RoomStudyKanjiDetailRepository(database, RoomStudyDashboardRepository.DEFAULT_EXAMPLE_LIMIT);
    }

    @Provides
    @Singleton
    static StudyLocalSuspensionRepository provideStudyLocalSuspensionRepository(KaniRoomDatabase database) {
        return new RoomStudyLocalSuspensionRepository(database);
    }

    @Provides
    @Singleton
    static StudyRuntimeSnapshotRepository provideStudyRuntimeSnapshotRepository(KaniRoomDatabase database) {
        return new RoomStudyRuntimeSnapshotRepository(database, RoomStudyDashboardRepository.DEFAULT_EXAMPLE_LIMIT);
    }

    @Provides
    @Singleton
    static StudyReviewStatsRepository provideStudyReviewStatsRepository(KaniRoomDatabase database) {
        return new RoomStudyReviewStatsRepository(database.reviewLogDao());
    }

    @Provides
    @Singleton
    static StudySchedulerSettingsRepository provideStudySchedulerSettingsRepository(KaniRoomDatabase database) {
        return new RoomStudySchedulerSettingsRepository(database.settingsDao());
    }

    @Provides
    @Singleton
    static RoomStudyRuntimeOwnershipPolicy provideRoomStudyRuntimeOwnershipPolicy() {
        return RoomStudyRuntimeOwnershipPolicy.DISABLED;
    }

    @Provides
    @Singleton
    static StudyQueueMutationGate provideStudyQueueMutationGate() {
        return new RoomStudyQueueMutationGate();
    }

    @Provides
    @Singleton
    static StudyReviewPersistenceRepository provideStudyReviewPersistenceRepository(
            KaniRoomDatabase database,
            RoomStudyRuntimeOwnershipPolicy ownershipPolicy,
            StudyQueueMutationGate studyQueueMutationGate
    ) {
        return new RoomStudyReviewPersistenceRepository(database, ownershipPolicy, studyQueueMutationGate);
    }

    @Provides
    static LoadNextStudySessionUseCase provideLoadNextStudySessionUseCase(
            StudyQueueRepository studyQueueRepository,
            StudyDashboardRepository studyDashboardRepository
    ) {
        return new LoadNextStudySessionUseCase(
                studyQueueRepository,
                studyDashboardRepository,
                new StudySessionSelector()
        );
    }

    @Provides
    static ApplyStudyReviewUseCase provideApplyStudyReviewUseCase(
            StudyReviewPersistenceRepository studyReviewPersistenceRepository
    ) {
        return new ApplyStudyReviewUseCase(
                studyReviewPersistenceRepository,
                new StudyReviewTransitionEngine()
        );
    }
}
