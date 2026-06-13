package dev.bee.kanjianki.core

import java.util.Locale

object HomeImportOnboardingPolicy {
    private const val JAPANESE_LANGUAGE = "ja"

    enum class State {
        INSTALL_ANKIDROID,
        GRANT_PERMISSION,
        CHOOSE_SOURCE,
        READY_FIRST_SYNC,
        RECOVER_PERMISSION,
        RECOVER_SYNC,
        SYNCED,
    }

    class LastSync(status: String?, importedKanji: Int, errorMessage: String?) {
        @JvmField val status: String = status ?: ""
        @JvmField val importedKanji: Int = maxOf(0, importedKanji)
        @JvmField val errorMessage: String = errorMessage ?: ""
    }

    class Plan(
        private val stateValue: State,
        private val bodyValue: String,
        private val primaryActionLabelValue: String,
    ) {
        fun state(): State = stateValue

        fun body(): String = bodyValue

        fun primaryActionLabel(): String = primaryActionLabelValue
    }

    @JvmStatic
    fun plan(
        providerInstalled: Boolean,
        permissionGranted: Boolean,
        canSync: Boolean,
        lastSync: LastSync?,
        permissionName: String?,
        settings: RecordsSyncModels.Settings,
    ): Plan {
        if (!providerInstalled) {
            return Plan(
                State.INSTALL_ANKIDROID,
                localizedText(
                    "Install AnkiDroid, then come back to sync your kanji.",
                    "AnkiDroidをインストールしてから、戻って漢字を同期してください。",
                ),
                localizedText("Install AnkiDroid", "AnkiDroidをインストール"),
            )
        }
        if (!permissionGranted || !canSync) {
            return Plan(
                State.GRANT_PERMISSION,
                permissionBody(permissionName),
                localizedText("Grant permission", "権限を許可"),
            )
        }
        if (!settings.hasImportSourceEnabled()) {
            return Plan(
                State.CHOOSE_SOURCE,
                localizedText(
                    "Choose import sources before you sync.",
                    "同期前にインポート元を選んでください。",
                ),
                localizedText("Review import settings", "インポート設定を確認"),
            )
        }
        val status = lastSync?.status ?: ""
        if (status == "success") {
            val sync = lastSync ?: LastSync(null, 0, null)
            return Plan(
                State.SYNCED,
                localizedText(
                    "Last sync imported " + StudyTextCopy.countText(sync.importedKanji, "kanji", "kanji") + ". " + sourceAndModelLine(settings),
                    "前回の同期で${sync.importedKanji}件の漢字をインポートしました。 " + sourceAndModelLine(settings),
                ),
                localizedText("Sync again", "もう一度同期"),
            )
        }
        if (status == "failed") {
            val sync = lastSync ?: LastSync(null, 0, null)
            val error = if (sync.errorMessage.isEmpty()) HomeTextCopy.syncFailureFallback() else sync.errorMessage
            if (error.lowercase(Locale.ROOT).contains("permission")) {
                return Plan(
                    State.RECOVER_PERMISSION,
                    localizedText(
                        "Kani couldn't read AnkiDroid because of permission: $error. Grant database access, then try syncing again.",
                        "権限の問題でKaniがAnkiDroidを読み取れませんでした: $error。データベースアクセスを許可してから、もう一度同期してください。",
                    ),
                    localizedText("Fix permission", "権限を修正"),
                )
            }
            return Plan(
                State.RECOVER_SYNC,
                localizedText(
                    "Last sync failed: $error. Check source settings, then try again.",
                    "前回の同期に失敗しました: $error。インポート元設定を確認してから、もう一度試してください。",
                ),
                localizedText("Try sync again", "もう一度同期"),
            )
        }
        return Plan(
            State.READY_FIRST_SYNC,
            HomeTextCopy.syncDialogMessage(settings),
            localizedText("Sync cards", "カードを同期"),
        )
    }

    @JvmStatic
    fun sourceAndModelLine(settings: RecordsSyncModels.Settings): String {
        val browser = if (settings.browserQueryImportEnabled()) {
            localizedText(" Query: ${settings.normalizedBrowserQuery()}.", " クエリ: ${settings.normalizedBrowserQuery()}。")
        } else {
            ""
        }
        val sources = importSources(settings)
        val sourceText = if (sources.isEmpty()) localizedText("none", "なし") else sources
        return localizedText(
            "Note type ${settings.modelName}. Sources: $sourceText.",
            "ノートタイプ ${settings.modelName}。ソース: $sourceText。",
        ) + browser
    }

    private fun permissionBody(permissionName: String?): String {
        val permission = if (permissionName.isNullOrEmpty()) "AnkiDroid database" else permissionName
        return localizedText(
            "Kani needs the $permission permission to read your local AnkiDroid decks. Data stays on your device.",
            "KaniがローカルのAnkiDroidデッキを読み取るには、$permission の権限が必要です。データは端末内にとどまります。",
        )
    }

    private fun importSources(settings: RecordsSyncModels.Settings): String {
        val sources = ArrayList<String>()
        if (settings.importActiveCards) {
            sources.add(localizedText("active cards", "アクティブカード"))
        }
        if (settings.importSuspendedCards) {
            sources.add(localizedText("suspended cards", "停止中カード"))
        }
        if (settings.importTaggedCardsEnabled()) {
            sources.add(localizedText("tagged cards", "タグ付きカード"))
        }
        if (settings.importWeakCards) {
            sources.add(localizedText("weak cards", "弱いカード"))
        }
        if (settings.browserQueryImportEnabled()) {
            sources.add(localizedText("browser query", "ブラウザ検索"))
        }
        return sources.joinToString(" + ")
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
