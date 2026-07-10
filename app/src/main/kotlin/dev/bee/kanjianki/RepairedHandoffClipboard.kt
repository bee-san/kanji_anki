package dev.bee.kanjianki

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import dev.bee.kanjianki.core.RepairedHandoffPolicy

internal fun copyRepairedAnkiSearch(context: Context, search: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Kani repaired cards", search))
    Toast.makeText(context, RepairedHandoffPolicy.copiedToast(), Toast.LENGTH_LONG).show()
}
