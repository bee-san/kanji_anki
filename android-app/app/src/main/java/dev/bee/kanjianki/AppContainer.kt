package dev.bee.kanjianki

import android.content.Context
import androidx.room.Room
import androidx.work.WorkManager
import dev.bee.kanjianki.data.ankidroid.AnkiDroidGateway
import dev.bee.kanjianki.data.ankidroid.ContentProviderAnkiDroidGateway
import dev.bee.kanjianki.data.fixture.ParityFixtureRepository
import dev.bee.kanjianki.data.local.AppDatabase
import dev.bee.kanjianki.data.local.RoomBackedKanjiCompanionRepository
import dev.bee.kanjianki.data.sync.SyncScheduler
import dev.bee.kanjianki.data.update.GitHubReleaseUpdater
import dev.bee.kanjianki.domain.KanjiCompanionRepository
import dev.bee.kanjianki.domain.buildKanjiCompanionUseCases

class AppContainer(context: Context) {
    val database: AppDatabase = Room.databaseBuilder(
        context.applicationContext,
        AppDatabase::class.java,
        "kanji_anki_android.db",
    ).fallbackToDestructiveMigration().build()

    val ankiDroidGateway: AnkiDroidGateway = ContentProviderAnkiDroidGateway(
        context = context.applicationContext,
    )

    val repository: KanjiCompanionRepository = ParityFixtureRepository(
        context = context.applicationContext,
    )

    val cachedRepository: KanjiCompanionRepository = RoomBackedKanjiCompanionRepository(
        context = context.applicationContext,
        database = database,
        gateway = ankiDroidGateway,
        upstream = repository,
    )

    val releaseUpdater = GitHubReleaseUpdater(
        context = context.applicationContext,
    )

    val useCases = buildKanjiCompanionUseCases(cachedRepository)

    val syncScheduler = SyncScheduler(
        workManager = WorkManager.getInstance(context.applicationContext),
    )
}
