package dev.bee.kanjianki.updatecore

import java.io.File

object UpdateCacheFilePolicy {
    const val DEFAULT_APK_NAME = "kani-update.apk"
    const val STALE_CACHE_MAX_AGE_MILLIS = 7L * 24L * 60L * 60L * 1000L

    @JvmStatic
    fun safeFileName(name: String?): String {
        val safe = File(name.orEmpty()).name
        return safe.ifEmpty { DEFAULT_APK_NAME }
    }

    @JvmStatic
    fun staleCachedApks(updatesDir: File, pendingApkName: String?, nowMillis: Long): List<File> {
        val pendingName = pendingApkName?.takeIf { it.trim().isNotEmpty() }?.let(::safeFileName)
        val files = updatesDir.listFiles() ?: return emptyList()
        return files
            .filter { file ->
                file.isFile &&
                    file.name.endsWith(".apk", ignoreCase = true) &&
                    file.name != pendingName &&
                    nowMillis - file.lastModified() >= STALE_CACHE_MAX_AGE_MILLIS
            }
            .sortedBy { file -> file.name }
    }
}
