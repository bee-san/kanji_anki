package dev.bee.kanjianki

import android.content.Context
import android.widget.Toast
import dev.bee.kanjianki.core.RepairedHandoffPolicy
import dev.bee.kanjianki.platform.android.AndroidClipboardService

internal fun copyRepairedAnkiSearch(context: Context, search: String) {
    AndroidClipboardService(context).setText("Kani repaired cards", search)
    Toast.makeText(context, RepairedHandoffPolicy.copiedToast(), Toast.LENGTH_LONG).show()
}
