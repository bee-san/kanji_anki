package dev.bee.kanjianki.updatecore

import java.util.Locale

object PackageInstallStatusPolicy {
    const val STATUS_SUCCESS = 0
    const val STATUS_PENDING_USER_ACTION = -1
    const val ANDROID_S_API_LEVEL = 31
    const val ANDROID_T_API_LEVEL = 33
    const val ANDROID_U_API_LEVEL = 34
    const val ANDROID_V_API_LEVEL = 35
    const val ANDROID_B_API_LEVEL = 36
    const val SOURCE_MANUAL = "MANUAL"
    const val SOURCE_AUTOMATIC = "AUTOMATIC"
    const val SOURCE_CACHED = "CACHED"

    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun mapInstallStatus(status: Int, message: String?): InstallCallback {
        if (status == STATUS_SUCCESS) {
            return InstallCallback(false, true, installFinishedMessage())
        }
        if (status == STATUS_PENDING_USER_ACTION) {
            return InstallCallback(true, false, installPermissionNeededMessage())
        }
        return InstallCallback(false, false, installFailedMessage(message))
    }

    @JvmStatic
    fun installFinishedMessage(): String = localizedText("Install finished.", "インストールが完了しました。")

    @JvmStatic
    fun installPermissionNeededMessage(): String = localizedText(
        "Android needs permission to finish installing.",
        "インストールを完了するにはAndroidの許可が必要です。",
    )

    @JvmStatic
    fun installFailedMessage(message: String?): String {
        val suffix = if (message.isNullOrBlank()) "" else ": ${message.trim()}"
        return localizedText("Install failed$suffix.", "インストールに失敗しました$suffix。")
    }

    @JvmStatic
    fun sourceNameOrDefault(raw: String?): String {
        if (raw == SOURCE_MANUAL || raw == SOURCE_AUTOMATIC || raw == SOURCE_CACHED) {
            return raw
        }
        return SOURCE_AUTOMATIC
    }

    @JvmStatic
    fun shouldLaunchInstallConfirmation(sourceName: String?): Boolean {
        val normalized = sourceNameOrDefault(sourceName)
        return normalized == SOURCE_MANUAL || normalized == SOURCE_CACHED
    }

    @JvmStatic
    fun shouldAllowInstallerWithoutExtraUserAction(targetSdk: Int, runtimeSdk: Int): Boolean {
        return targetSdk >= minimumTargetSdkForInstallerWithoutExtraUserAction(runtimeSdk)
    }

    @JvmStatic
    fun minimumTargetSdkForInstallerWithoutExtraUserAction(runtimeSdk: Int): Int {
        if (runtimeSdk >= ANDROID_B_API_LEVEL) {
            return ANDROID_U_API_LEVEL
        }
        if (runtimeSdk >= ANDROID_V_API_LEVEL) {
            return ANDROID_T_API_LEVEL
        }
        if (runtimeSdk >= ANDROID_U_API_LEVEL) {
            return ANDROID_S_API_LEVEL
        }
        if (runtimeSdk >= ANDROID_T_API_LEVEL) {
            return 30
        }
        if (runtimeSdk >= ANDROID_S_API_LEVEL) {
            return 29
        }
        return Int.MAX_VALUE
    }

    private fun localizedText(english: String, japanese: String): String {
        return if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english
    }

    class InstallCallback(
        @JvmField val pendingUserAction: Boolean,
        @JvmField val success: Boolean,
        @JvmField val message: String,
    ) {
        fun pendingUserAction(): Boolean = pendingUserAction
        fun success(): Boolean = success
        fun message(): String = message
    }
}
