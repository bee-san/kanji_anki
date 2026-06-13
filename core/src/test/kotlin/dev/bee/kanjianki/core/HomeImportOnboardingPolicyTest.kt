package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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
        assertEquals("Install AnkiDroid, then come back to sync your kanji.", missingApp.body())

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
            "Kani keeps suspended Basic cards on device. Turn on active cards if you want those too.",
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
        assertEquals("Choose import sources before you sync.", noSources.body())
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

    @Test
    fun onboardingPlansLocalizeInJapaneseLocale() {
        withDefaultLocale(Locale.JAPANESE) {
            val missingApp = HomeImportOnboardingPolicy.plan(
                false,
                false,
                false,
                null,
                null,
                settings(true, true, false, emptyList(), false, false, "")
            )
            assertEquals("AnkiDroidをインストール", missingApp.primaryActionLabel())
            assertEquals("AnkiDroidをインストールしてから、戻って漢字を同期してください。", missingApp.body())

            val needsPermission = HomeImportOnboardingPolicy.plan(
                true,
                false,
                false,
                null,
                "com.ichi2.anki.permission.READ_WRITE_DATABASE",
                settings(true, true, false, emptyList(), false, false, "")
            )
            assertEquals("権限を許可", needsPermission.primaryActionLabel())
            assertTrue(needsPermission.body().contains("ローカルのAnkiDroidデッキ"))
            assertTrue(needsPermission.body().contains("データは端末内にとどまります"))

            val noSources = HomeImportOnboardingPolicy.plan(
                true,
                true,
                true,
                null,
                null,
                settings(false, false, false, emptyList(), false, false, "Japanese::Core")
            )
            assertEquals("インポート設定を確認", noSources.primaryActionLabel())
            assertEquals("同期前にインポート元を選んでください。", noSources.body())

            val permissionFailure = HomeImportOnboardingPolicy.plan(
                true,
                true,
                true,
                HomeImportOnboardingPolicy.LastSync("failed", 0, "Permission denied"),
                null,
                settings(true, true, false, emptyList(), false, false, "")
            )
            assertEquals("権限を修正", permissionFailure.primaryActionLabel())
            assertTrue(permissionFailure.body().contains("権限の問題"))
            assertTrue(permissionFailure.body().contains("もう一度同期してください"))

            val retryFailure = HomeImportOnboardingPolicy.plan(
                true,
                true,
                true,
                HomeImportOnboardingPolicy.LastSync("failed", 0, "provider timed out"),
                null,
                settings(true, true, false, emptyList(), false, false, "")
            )
            assertEquals("もう一度同期", retryFailure.primaryActionLabel())
            assertTrue(retryFailure.body().contains("前回の同期に失敗しました"))
            assertTrue(retryFailure.body().contains("provider timed out"))

            val success = HomeImportOnboardingPolicy.plan(
                true,
                true,
                true,
                HomeImportOnboardingPolicy.LastSync("success", 7, ""),
                null,
                settings(false, true, true, listOf("leech"), true, true, "deck:Japanese")
            )
            assertEquals("もう一度同期", success.primaryActionLabel())
            assertTrue(success.body().contains("前回の同期で7件の漢字をインポートしました"))
            assertTrue(success.body().contains("ノートタイプ Basic。ソース: 停止中カード + タグ付きカード + 弱いカード + ブラウザ検索"))
            assertTrue(success.body().contains("クエリ: deck:Japanese"))
        }
    }

    private fun withDefaultLocale(locale: Locale, block: () -> Unit) {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previousLocale)
        }
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
