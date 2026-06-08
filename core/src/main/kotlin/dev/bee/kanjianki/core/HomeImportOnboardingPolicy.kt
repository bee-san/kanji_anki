package dev.bee.kanjianki.core

import java.util.Locale

object HomeImportOnboardingPolicy {
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
                "Install AnkiDroid, then return to Kani to import your local kanji cards.",
                "Install AnkiDroid",
            )
        }
        if (!permissionGranted || !canSync) {
            return Plan(
                State.GRANT_PERMISSION,
                permissionBody(permissionName),
                "Grant permission",
            )
        }
        if (!settings.hasImportSourceEnabled()) {
            return Plan(
                State.CHOOSE_SOURCE,
                "Choose import sources before the first sync.",
                "Review import settings",
            )
        }
        val status = lastSync?.status ?: ""
        if (status == "success") {
            val sync = lastSync ?: LastSync(null, 0, null)
            return Plan(
                State.SYNCED,
                "Last sync imported " + StudyTextCopy.countText(sync.importedKanji, "kanji", "kanji") + ". " + sourceAndModelLine(settings),
                "Sync again",
            )
        }
        if (status == "failed") {
            val sync = lastSync ?: LastSync(null, 0, null)
            val error = if (sync.errorMessage.isEmpty()) HomeTextCopy.syncFailureFallback() else sync.errorMessage
            if (error.lowercase(Locale.ROOT).contains("permission")) {
                return Plan(
                    State.RECOVER_PERMISSION,
                    "Kani couldn't read AnkiDroid because of permission: $error. Grant database access, then try syncing again.",
                    "Fix permission",
                )
            }
            return Plan(
                State.RECOVER_SYNC,
                "Last sync failed: $error. Check source settings, then try again.",
                "Try sync again",
            )
        }
        return Plan(
            State.READY_FIRST_SYNC,
            HomeTextCopy.syncDialogMessage(settings) + " Tap Sync cards to start.",
            "Sync cards",
        )
    }

    @JvmStatic
    fun sourceAndModelLine(settings: RecordsSyncModels.Settings): String {
        val browser = if (settings.browserQueryImportEnabled()) {
            " Query: ${settings.normalizedBrowserQuery()}."
        } else {
            ""
        }
        val sources = importSources(settings)
        return "Note type ${settings.modelName}. Sources: ${if (sources.isEmpty()) "none" else sources}." + browser
    }

    private fun permissionBody(permissionName: String?): String {
        val permission = if (permissionName.isNullOrEmpty()) "AnkiDroid database" else permissionName
        return "Kani needs the $permission permission to read your local AnkiDroid decks. Data stays on your device."
    }

    private fun importSources(settings: RecordsSyncModels.Settings): String {
        val sources = ArrayList<String>()
        if (settings.importActiveCards) {
            sources.add("active cards")
        }
        if (settings.importSuspendedCards) {
            sources.add("suspended cards")
        }
        if (settings.importTaggedCardsEnabled()) {
            sources.add("tagged cards")
        }
        if (settings.importWeakCards) {
            sources.add("weak cards")
        }
        if (settings.browserQueryImportEnabled()) {
            sources.add("browser query")
        }
        return sources.joinToString(" + ")
    }
}
