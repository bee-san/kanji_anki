package dev.bee.kanjianki.memory

import dev.bee.kanjianki.core.MemoryModelTextCopy

/** Maps [RwkvRuntime.Status] to user-facing copy. */
internal object MemoryModelCopy {
    fun preparingMessage(status: RwkvRuntime.Status): String =
        when (status) {
            is RwkvRuntime.Status.Failed -> MemoryModelTextCopy.failedToast()
            is RwkvRuntime.Status.Rebuilding ->
                MemoryModelTextCopy.preparingToast(
                    if (status.total > 0) (100L * status.done / status.total).toInt().coerceIn(0, 99) else null,
                )
            else -> MemoryModelTextCopy.preparingToast(null)
        }

    fun statusLine(status: RwkvRuntime.Status): String =
        when (status) {
            RwkvRuntime.Status.Ready -> MemoryModelTextCopy.statusReady()
            RwkvRuntime.Status.Unchecked, RwkvRuntime.Status.Checking -> MemoryModelTextCopy.statusChecking()
            is RwkvRuntime.Status.Rebuilding -> MemoryModelTextCopy.statusRebuilding(status.done, status.total)
            is RwkvRuntime.Status.Failed -> MemoryModelTextCopy.statusFailed(status.message)
        }
}
