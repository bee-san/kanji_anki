package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class HomeImportOnboardingPolicyTest {
    @Test
    public void firstRunAdvancesFromInstallToPermissionExplanationToReadyImport() {
        HomeImportOnboardingPolicy.Plan missingApp = HomeImportOnboardingPolicy.plan(
                false,
                false,
                false,
                null,
                null,
                settings(true, true, false, Collections.emptyList(), false, false, "")
        );
        assertEquals(HomeImportOnboardingPolicy.State.INSTALL_ANKIDROID, missingApp.state());
        assertEquals("Install AnkiDroid", missingApp.primaryActionLabel());
        assertTrue(missingApp.body().contains("Install AnkiDroid"));

        HomeImportOnboardingPolicy.Plan needsPermission = HomeImportOnboardingPolicy.plan(
                true,
                false,
                false,
                null,
                "com.ichi2.anki.permission.READ_WRITE_DATABASE",
                settings(true, true, false, Collections.emptyList(), false, false, "")
        );
        assertEquals(HomeImportOnboardingPolicy.State.GRANT_PERMISSION, needsPermission.state());
        assertEquals("Grant permission", needsPermission.primaryActionLabel());
        assertTrue(needsPermission.body().contains("read your local AnkiDroid decks"));
        assertTrue(needsPermission.body().contains("does not upload"));

        HomeImportOnboardingPolicy.Plan ready = HomeImportOnboardingPolicy.plan(
                true,
                true,
                true,
                null,
                "com.ichi2.anki.permission.READ_WRITE_DATABASE",
                settings(true, true, false, Collections.emptyList(), false, false, "")
        );
        assertEquals(HomeImportOnboardingPolicy.State.READY_FIRST_SYNC, ready.state());
        assertEquals("Sync cards", ready.primaryActionLabel());
        assertTrue(ready.body().contains("source selection: active cards + suspended cards"));
        assertTrue(ready.body().contains("note type Basic"));
    }

    @Test
    public void firstRunStopsForMissingSourceSelectionBeforeSyncing() {
        HomeImportOnboardingPolicy.Plan noSources = HomeImportOnboardingPolicy.plan(
                true,
                true,
                true,
                null,
                null,
                settings(false, false, false, Collections.emptyList(), false, false, "Japanese::Core")
        );
        assertEquals(HomeImportOnboardingPolicy.State.CHOOSE_SOURCE, noSources.state());
        assertEquals("Review import settings", noSources.primaryActionLabel());
        assertTrue(noSources.body().contains("Choose AnkiDroid import sources"));
        assertTrue(noSources.body().contains("enable suspended, active, tagged, weak, or browser-query import"));
    }

    @Test
    public void syncStatusTransitionsRecoverFailuresAndSummarizeSuccess() {
        HomeImportOnboardingPolicy.Plan permissionFailure = HomeImportOnboardingPolicy.plan(
                true,
                true,
                true,
                new HomeImportOnboardingPolicy.LastSync("failed", 0, "Permission denied"),
                null,
                settings(true, true, false, Collections.emptyList(), false, false, "")
        );
        assertEquals(HomeImportOnboardingPolicy.State.RECOVER_PERMISSION, permissionFailure.state());
        assertEquals("Fix permission", permissionFailure.primaryActionLabel());
        assertTrue(permissionFailure.body().contains("permission"));
        assertTrue(permissionFailure.body().contains("try sync again"));

        HomeImportOnboardingPolicy.Plan retryFailure = HomeImportOnboardingPolicy.plan(
                true,
                true,
                true,
                new HomeImportOnboardingPolicy.LastSync("failed", 0, "provider timed out"),
                null,
                settings(true, true, false, Collections.emptyList(), false, false, "")
        );
        assertEquals(HomeImportOnboardingPolicy.State.RECOVER_SYNC, retryFailure.state());
        assertEquals("Try sync again", retryFailure.primaryActionLabel());
        assertTrue(retryFailure.body().contains("provider timed out"));

        HomeImportOnboardingPolicy.Plan success = HomeImportOnboardingPolicy.plan(
                true,
                true,
                true,
                new HomeImportOnboardingPolicy.LastSync("success", 7, ""),
                null,
                settings(false, true, true, Arrays.asList("leech"), true, true, "deck:Japanese")
        );
        assertEquals(HomeImportOnboardingPolicy.State.SYNCED, success.state());
        assertEquals("Sync again", success.primaryActionLabel());
        assertTrue(success.body().contains("7 kanji ready"));
        assertTrue(success.body().contains("source selection: suspended cards + tagged cards + weak cards + browser query"));
    }

    private static RecordsSyncModels.Settings settings(
            boolean active,
            boolean suspended,
            boolean tagged,
            java.util.List<String> tags,
            boolean weak,
            boolean browserQueryEnabled,
            String browserQuery
    ) {
        return new RecordsSyncModels.Settings(
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
        );
    }
}
