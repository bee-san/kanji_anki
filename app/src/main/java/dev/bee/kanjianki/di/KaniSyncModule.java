package dev.bee.kanjianki.di;

import android.content.Context;

import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import dev.bee.kanjianki.ankidroid.AnkiDroidCollectionGateway;
import dev.bee.kanjianki.domain.importing.ImportCandidateSelector;
import dev.bee.kanjianki.domain.importing.KanjiRankLookup;
import dev.bee.kanjianki.domain.repository.SourceMirrorSyncRepository;
import dev.bee.kanjianki.domain.repository.StudyQueueRepository;
import dev.bee.kanjianki.domain.repository.SyncRunRepository;
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlanner;
import dev.bee.kanjianki.domain.scheduler.StudyQueueSeeder;
import dev.bee.kanjianki.domain.sync.CollectionGateway;
import dev.bee.kanjianki.domain.sync.RunSourceMirrorSyncUseCase;
import dev.bee.kanjianki.domain.sync.SyncExecutionGate;
import dev.bee.kanjianki.domain.sync.SyncDashboardBuilder;
import dev.bee.kanjianki.sync.AndroidKanjiRankLookup;
import javax.inject.Singleton;

@Module
@InstallIn(SingletonComponent.class)
public final class KaniSyncModule {
    private KaniSyncModule() {
    }

    @Provides
    @Singleton
    static CollectionGateway provideCollectionGateway(@ApplicationContext Context context) {
        return new AnkiDroidCollectionGateway(context);
    }

    @Provides
    @Singleton
    static dev.bee.kanjianki.domain.common.AppClock provideDomainClock() {
        return System::currentTimeMillis;
    }

    @Provides
    @Singleton
    static KanjiRankLookup provideKanjiRankLookup(@ApplicationContext Context context) {
        return new AndroidKanjiRankLookup(context);
    }

    @Provides
    static ImportCandidateSelector provideImportCandidateSelector(KanjiRankLookup ranks) {
        return new ImportCandidateSelector(ranks);
    }

    @Provides
    static SyncDashboardBuilder provideSyncDashboardBuilder(KanjiRankLookup ranks) {
        return new SyncDashboardBuilder(ranks);
    }

    @Provides
    @Singleton
    static SyncExecutionGate provideSyncExecutionGate() {
        return new SyncExecutionGate();
    }

    @Provides
    static RunSourceMirrorSyncUseCase provideRunSourceMirrorSyncUseCase(
            CollectionGateway gateway,
            SyncRunRepository syncRunRepository,
            SourceMirrorSyncRepository sourceMirrorSyncRepository,
            ImportCandidateSelector importCandidateSelector,
            SyncDashboardBuilder syncDashboardBuilder,
            dev.bee.kanjianki.domain.common.AppClock clock,
            StudyQueueRepository studyQueueRepository,
            SyncExecutionGate syncExecutionGate
    ) {
        return new RunSourceMirrorSyncUseCase(
                gateway,
                syncRunRepository,
                sourceMirrorSyncRepository,
                importCandidateSelector,
                syncDashboardBuilder,
                clock,
                studyQueueRepository,
                new StudyQueueSeeder(),
                new AdaptiveStudyPlanner(),
                syncExecutionGate
        );
    }
}
