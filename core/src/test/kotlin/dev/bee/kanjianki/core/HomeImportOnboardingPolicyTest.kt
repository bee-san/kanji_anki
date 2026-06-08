package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections

class HomeImportOnboardingPolicyTest {
    @Test
    fun firstRunAdvancesFromInstallToPermissionExplanationToReadyImport() {
        val missingApp = HomeImportOnboardingPolicy.plan(
            false,
            false,
            false,
            null,
            null,
            settings(true, true, false, emptyList(), false, false, "")
        )
        assertEquals(HomeImportOnboardingPolicy.State.INSTALL_ANKIDROID, missingApp.state())
        assertEquals("Install AnkiDroid", missingApp.primaryActionLabel())
        assertTrue(missingApp.body().contains("Install AnkiDroid"))

        val needsPermission = HomeImportOnboardingPolicy.plan(
            true,
            false,
            false,
            null,
            "com.ichi2.anki.permission.READ_WRITE_DATABASE",
            settings(true, true, false, emptyList(), false, false, "")
        )
        assertEquals(HomeImportOnboardingPolicy.State.GRANT_PERMISSION, needsPermission.state())
        assertEquals("Grant permission", needsPermission.primaryActionLabel())
        assertTrue(needsPermission.body().contains("read your local AnkiDroid decks"))
        assertTrue(needsPermission.body().contains("Data stays on your device"))

        val ready = HomeImportOnboardingPolicy.plan(
            true,
            true,
            true,
            null,
            "com.ichi2.anki.permission.READ_WRITE_DATABASE",
            settings(true, true, false, emptyList(), false, false, "")
        )
        assertEquals(HomeImportOnboardingPolicy.State.READY_FIRST_SYNC, ready.state())
        assertEquals("Sync cards", ready.primaryActionLabel())
        assertEquals(
            "Kani keeps suspended Basic cards on device. Enable active cards to include them.",
            ready.body()
        )
    }

    @Test
    fun firstRunStopsForMissingSourceSelectionBeforeSyncing() {
        val noSources = HomeImportOnboardingPolicy.plan(
            true,
            true,
            true,
            null,
            null,
            settings(false, false, false, emptyList(), false, false, "Japanese::Core")
        )
        assertEquals(HomeImportOnboardingPolicy.State.CHOOSE_SOURCE, noSources.state())
        assertEquals("Review import settings", noSources.primaryActionLabel())
        assertTrue(noSources.body().contains("Choose import sources before the first sync."))
    }

    @Test
    fun syncStatusTransitionsRecoverFailuresAndSummarizeSuccess() {
        val permissionFailure = HomeImportOnboardingPolicy.plan(
            true,
            true,
            true,
            HomeImportOnboardingPolicy.LastSync("failed", 0, "Permission denied"),
            null,
            settings(true, true, false, emptyList(), false, false, "")
        )
        assertEquals(HomeImportOnboardingPolicy.State.RECOVER_PERMISSION, permissionFailure.state())
        assertEquals("Fix permission", permissionFailure.primaryActionLabel())
        assertTrue(permissionFailure.body().contains("permission"))
        assertTrue(permissionFailure.body().contains("try syncing again"))

        val retryFailure = HomeImportOnboardingPolicy.plan(
            true,
            true,
            true,
            HomeImportOnboardingPolicy.LastSync("failed", 0, "provider timed out"),
            null,
            settings(true, true, false, emptyList(), false, false, "")
        )
        assertEquals(HomeImportOnboardingPolicy.State.RECOVER_SYNC, retryFailure.state())
        assertEquals("Try sync again", retryFailure.primaryActionLabel())
        assertTrue(retryFailure.body().contains("provider timed out"))

        val success = HomeImportOnboardingPolicy.plan(
            true,
            true,
            true,
            HomeImportOnboardingPolicy.LastSync("success", 7, ""),
            null,
            settings(false, true, true, listOf("leech"), true, true, "deck:Japanese")
        )
        assertEquals(HomeImportOnboardingPolicy.State.SYNCED, success.state())
        assertEquals("Sync again", success.primaryActionLabel())
        assertTrue(success.body().contains("Last sync imported 7 kanji"))
        assertTrue(success.body().contains("Note type Basic. Sources: suspended cards + tagged cards + weak cards + browser query"))
        assertTrue(success.body().contains("Query: deck:Japanese"))
    }

    private fun settings(
        active: Boolean,
        suspended: Boolean,
        tagged: Boolean,
        tags: List<String>,
        weak: Boolean,
        browserQueryEnabled: Boolean,
        browserQuery: String
    ): RecordsSyncModels.Settings {
        return RecordsSyncModels.Settings(
            "Basic",
            "Card 1",
            "Expression",
            "Reading",
            "Meaning",
            "Sentence",
            "Frequency",
            "FreqSort",
            21,
            2,
            1,
            2000,
            24,
            3,
            7,
            2,
            2,
            active,
            suspended,
            tagged,
            tags,
            weak,
            0.85,
            3,
            2,
            browserQueryEnabled,
            browserQuery,
            RecordsBase.DEFAULT_NEW_CARD_SORT_MODE,
            7,
            2
        )
    }
}
