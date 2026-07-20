package dev.bee.kanjianki.updatecore

import java.util.Locale

object UpdateTextPolicy {
    const val DEFAULT_PENDING_UPDATE_MESSAGE = "Kani update is ready. Open Kani to install it."
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun readableMessage(error: Throwable?): String {
        if (error == null) {
            return localizedText("unknown error", "不明なエラー")
        }
        val message = error.message
        if (!message.isNullOrBlank()) {
            return message
        }
        return error::class.java.simpleName
    }

    @JvmStatic
    fun alreadyOnVersionMessage(version: String): String = localizedText(
        "Already on $version.",
        "すでにバージョン $version を使用しています。",
    )

    @JvmStatic
    fun noUsableReleaseMetadataReason(): String = localizedText(
        "no network connection",
        "ネットワークに接続されていません",
    )

    @JvmStatic
    fun updateCheckFailedMessage(reason: String?): String = localizedErrorMessage(
        "Update check failed",
        "更新の確認に失敗しました",
        reason,
    )

    @JvmStatic
    fun noVerifiedApkWaitingMessage(): String = localizedText(
        "No verified APK is waiting to install.",
        "インストール待ちの確認済みAPKはありません。",
    )

    @JvmStatic
    fun verifiedApkCacheMissingMessage(): String = localizedText(
        "Verified APK cache is missing. Check again to download it.",
        "確認済みAPKのキャッシュがありません。もう一度確認してダウンロードしてください。",
    )

    @JvmStatic
    fun updateInstallFailedMessage(reason: String?): String = localizedErrorMessage(
        "Update install failed",
        "更新のインストールに失敗しました",
        reason,
    )

    @JvmStatic
    fun apkVerifiedGrantInstallPermissionMessage(): String = localizedText(
        "APK verified. Grant install permission to continue.",
        "APKを確認しました。続行するにはインストール権限を許可してください。",
    )

    @JvmStatic
    fun apkVerifiedAndroidInstallerStartedMessage(): String = localizedText(
        "APK verified. Android installer started.",
        "APKを確認しました。Androidのインストーラーを起動しました。",
    )

    @JvmStatic
    fun installPermissionDialogTitle(): String = localizedText(
        "Keep Kani up to date",
        "Kaniを最新の状態に保つ",
    )

    @JvmStatic
    fun installPermissionDialogMessage(pendingVersion: String?): String {
        val cleanVersion = InstallPermissionPromptPolicy.normalizedVersion(pendingVersion)
            .replaceFirst("^v".toRegex(), "")
        if (cleanVersion.isNotEmpty()) {
            return localizedText(
                "Kani $cleanVersion is verified and ready to install. " +
                    "Allow Kani to install updates on the next Android settings screen " +
                    "and it will update itself automatically.",
                "Kani $cleanVersion の確認が完了し、インストールの準備ができました。" +
                    "次のAndroid設定画面でKaniによる更新のインストールを許可すると、自動的に更新されます。",
            )
        }
        return localizedText(
            "Kani can download and install verified updates by itself. " +
                "Allow Kani to install updates on the next Android settings screen.",
            "Kaniは確認済みの更新を自動でダウンロードしてインストールできます。" +
                "次のAndroid設定画面でKaniによる更新のインストールを許可してください。",
        )
    }

    @JvmStatic
    fun installPermissionDialogAllowLabel(): String = localizedText("Allow", "許可する")

    @JvmStatic
    fun installPermissionDialogNotNowLabel(): String = localizedText("Not now", "今はしない")

    @JvmStatic
    fun notificationTitle(): String = localizedText(
        "Kani update ready to install",
        "Kaniの更新をインストールできます",
    )

    @JvmStatic
    fun notificationChannelName(): String = localizedText("App updates", "アプリの更新")

    @JvmStatic
    fun notificationChannelDescription(): String = localizedText(
        "Friendly Kani update prompts.",
        "Kaniの更新をわかりやすくお知らせします。",
    )

    @JvmStatic
    fun notificationBody(version: String?, message: String?): String {
        if (!version.isNullOrEmpty()) {
            val cleanVersion = version.replaceFirst("^v".toRegex(), "")
            return localizedText(
                "Version $cleanVersion is ready. Open Kani to install it.",
                "バージョン $cleanVersion の準備ができました。Kaniを開いてインストールします。",
            )
        }
        return appendInstallAction(message)
    }

    private fun appendInstallAction(message: String?): String {
        if (message.isNullOrBlank()) {
            return localizedText(
                DEFAULT_PENDING_UPDATE_MESSAGE,
                "Kaniの更新準備ができました。Kaniを開いてインストールします。",
            )
        }
        return "${message.trim()} ${localizedInstallAction()}"
    }

    private fun localizedInstallAction(): String = localizedText(
        "Open Kani to install it.",
        "Kaniを開いてインストールします。",
    )

    private fun localizedErrorMessage(englishPrefix: String, japanesePrefix: String, reason: String?): String {
        val suffix = if (reason.isNullOrBlank()) "" else ": ${reason.trim()}"
        return localizedText("$englishPrefix$suffix", "$japanesePrefix$suffix")
    }

    private fun localizedText(english: String, japanese: String): String =
        if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english
}
