package dev.bee.kanjianki.updatecore

import java.io.File

object UpdateCacheFilePolicy {
    const val DEFAULT_APK_NAME = "kani-update.apk"

    @JvmStatic
    fun safeFileName(name: String?): String {
        val safe = File(name.orEmpty()).name
        return safe.ifEmpty { DEFAULT_APK_NAME }
    }
}
