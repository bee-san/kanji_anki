package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.MemoryModelTextCopy
import dev.bee.kanjianki.memory.RwkvRuntime
import dev.bee.rwkv.RwkvAlgorithmInfo
import kotlinx.coroutines.flow.StateFlow

internal object SettingsMemoryModelControlDescriptions {
    const val REBUILD = "Rebuild the memory model from review history"
}

/** Settings › Study behavior › Memory model: what schedules reviews, its state, and a rebuild action. */
internal data class SettingsMemoryModelPanelModel(
    val title: String,
    val body: String,
    val modelLine: String,
    val provenanceLine: String,
    val status: StateFlow<RwkvRuntime.Status>,
    val rebuildLabel: String,
    val onRebuild: Runnable,
) : SettingsPanelModel

internal class MainActivitySettingsMemoryModel(private val activity: MainActivitySettings) {
    fun panelModel(): SettingsMemoryModelPanelModel {
        RwkvRuntime.ensureReady(activity)
        return SettingsMemoryModelPanelModel(
            title = MemoryModelTextCopy.title(),
            body = MemoryModelTextCopy.body(),
            modelLine = MemoryModelTextCopy.modelLine(RwkvAlgorithmInfo.MODEL_FAMILY, RwkvAlgorithmInfo.PARAMETER_COUNT),
            provenanceLine = MemoryModelTextCopy.provenanceLine(),
            status = RwkvRuntime.status,
            rebuildLabel = MemoryModelTextCopy.rebuildLabel(),
            onRebuild = Runnable(::rebuild),
        )
    }

    private fun rebuild() {
        RwkvRuntime.requestRebuild(activity)
        Toast.makeText(activity, MemoryModelTextCopy.rebuildStartedToast(), Toast.LENGTH_SHORT).show()
    }
}
