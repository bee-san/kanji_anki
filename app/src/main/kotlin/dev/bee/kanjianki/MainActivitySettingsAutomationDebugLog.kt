package dev.bee.kanjianki

import android.content.Intent
import android.widget.Toast
import dev.bee.kanjianki.core.DebugLogSettingsTogglePolicy
import dev.bee.kanjianki.core.SettingsTextCopy

/**
 * Settings > Automation panel for the diagnostic debug log: an on/off switch for
 * [AppDebugLog] capture plus a share action that hands the captured file to the
 * Android share sheet. Sharing works even while capture is off, so a recorded
 * problem can be sent after the switch is turned back off.
 */
internal class MainActivitySettingsAutomationDebugLog(private val activity: MainActivitySettings) {
    fun debugLogSettingsPanelModel(): SettingsDebugLogPanelModel {
        val enabled = activity.store.debugLogEnabled()
        return SettingsDebugLogPanelModel(
            title = SettingsTextCopy.debugLogTitle(),
            status = SettingsTextCopy.debugLogStatus(enabled),
            statusColor = if (enabled) MainActivityUiSupport.TEAL else MainActivityUiSupport.MUTED,
            detail = SettingsTextCopy.debugLogDetail(enabled),
            toggleLabel = SettingsTextCopy.debugLogToggleLabel(enabled),
            togglePrimary = !enabled,
            onToggle = Runnable { setDebugLogEnabled(!enabled) },
            shareLabel = SettingsTextCopy.shareDebugLogLabel(),
            onShare = Runnable { shareDebugLog() },
        )
    }

    private fun setDebugLogEnabled(enabled: Boolean) {
        val result = if (enabled) {
            DebugLogSettingsTogglePolicy.enable()
        } else {
            DebugLogSettingsTogglePolicy.disable()
        }
        activity.runSettingsWrite(
            traceSection = if (enabled) "kani.settings.debug-log.enable" else "kani.settings.debug-log.disable",
            write = {
                activity.store.saveDebugLogEnabled(result.enabled)
                AppDebugLog.setEnabled(activity, result.enabled)
            },
        ) {
            Toast.makeText(activity, result.message, Toast.LENGTH_SHORT).show()
            activity.renderSettingsAutomation(true)
        }
    }

    private fun shareDebugLog() {
        AppDebugLog.prepareShareIntent(activity) { intent ->
            activity.postToMainIfActive {
                if (intent == null) {
                    Toast.makeText(activity, SettingsTextCopy.debugLogEmptyToast(), Toast.LENGTH_LONG).show()
                    return@postToMainIfActive
                }
                activity.startActivity(
                    Intent.createChooser(intent, SettingsTextCopy.shareDebugLogChooserTitle())
                )
            }
        }
    }
}
