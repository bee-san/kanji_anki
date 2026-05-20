package dev.bee.kanjianki

import android.view.View
import android.widget.Toast
import dev.bee.kanjianki.core.AutoSyncSettingsTogglePolicy
import dev.bee.kanjianki.core.DateTextPolicy
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.sync.AutoSyncScheduler

internal class MainActivitySettingsAutomationAutoSync(private val activity: MainActivitySettings) {
    fun autoSyncSettingsPanel(): View {
        return autoSyncSettingsPanelView(activity, autoSyncSettingsPanelModel())
    }

    fun autoSyncSettingsPanelModel(): SettingsAutoSyncPanelModel {
        val auto = activity.store.autoSyncSettings()
        val action = autoSyncAction(auto)
        return SettingsAutoSyncPanelModel(
            title = SettingsTextCopy.dailyAnkiSyncTitle(),
            status = SettingsTextCopy.autoSyncStatus(auto.configured, auto.enabled, auto.displayTime()),
            statusColor = if (auto.enabled) MainActivityUiSupport.TEAL else MainActivityUiSupport.MUTED,
            detail = SettingsTextCopy.autoSyncDetail(
                auto.configured,
                auto.enabled,
                shortDateTime(auto.lastSuccessAt),
                lastAttemptText(auto),
                shortDateTime(auto.nextRunAt)
            ),
            actionLabel = action?.label,
            primaryAction = action?.primary ?: false,
            onAction = action?.callback
        )
    }

    private fun enableAutoSync() {
        val result = AutoSyncSettingsTogglePolicy.enable()
        activity.store.setAutoSyncEnabled(result.enabled())
        AutoSyncScheduler.schedule(activity)
        Toast.makeText(activity, result.message(), Toast.LENGTH_SHORT).show()
        activity.renderSettings()
    }

    private fun disableAutoSync() {
        val result = AutoSyncSettingsTogglePolicy.disable()
        activity.store.setAutoSyncEnabled(result.enabled())
        AutoSyncScheduler.cancel(activity)
        Toast.makeText(activity, result.message(), Toast.LENGTH_SHORT).show()
        activity.renderSettings()
    }

    private fun autoSyncAction(auto: LocalStoreBase.AutoSyncSettings): AutoSyncActionModel? {
        if (!auto.configured) {
            return null
        }
        return if (auto.enabled) {
            AutoSyncActionModel(
                label = SettingsTextCopy.turnOffDailySyncLabel(),
                primary = false,
                callback = SettingsAutoSyncAction { disableAutoSync() }
            )
        } else {
            AutoSyncActionModel(
                label = SettingsTextCopy.turnOnDailySyncLabel(),
                primary = true,
                callback = SettingsAutoSyncAction { enableAutoSync() }
            )
        }
    }

    private data class AutoSyncActionModel(
        val label: String,
        val primary: Boolean,
        val callback: SettingsAutoSyncAction
    )

    private companion object {
        fun shortDateTime(value: Long): String {
            return if (value > 0L) DateTextPolicy.shortDateTime(value) else ""
        }

        fun lastAttemptText(auto: LocalStoreBase.AutoSyncSettings): String {
            return if (auto.lastAttemptAt > 0L && auto.lastAttemptAt != auto.lastSuccessAt) {
                DateTextPolicy.shortDateTime(auto.lastAttemptAt)
            } else {
                ""
            }
        }
    }
}
