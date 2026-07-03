package dev.bee.kanjianki.updatecore

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateTextPolicyTest {
    @Test
    fun readableMessageUsesExceptionMessageWhenPresent() {
        assertEquals("HTTP 403", UpdateTextPolicy.readableMessage(RuntimeException("HTTP 403")))
    }

    @Test
    fun readableMessageFallsBackToClassOrUnknown() {
        assertEquals("RuntimeException", UpdateTextPolicy.readableMessage(RuntimeException()))
        assertEquals("IllegalStateException", UpdateTextPolicy.readableMessage(IllegalStateException("   ")))
        assertEquals("unknown error", UpdateTextPolicy.readableMessage(null))
    }

    @Test
    fun readableMessageLocalizesUnknownErrorInJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            assertEquals("不明なエラー", UpdateTextPolicy.readableMessage(null))
        }
    }

    @Test
    fun updateResultMessagesPreserveExistingEnglishCopy() {
        assertEquals("Already on 1.2.3.", UpdateTextPolicy.alreadyOnVersionMessage("1.2.3"))
        assertEquals("Update check failed: download broke", UpdateTextPolicy.updateCheckFailedMessage("download broke"))
        assertEquals("No verified APK is waiting to install.", UpdateTextPolicy.noVerifiedApkWaitingMessage())
        assertEquals(
            "Verified APK cache is missing. Check again to download it.",
            UpdateTextPolicy.verifiedApkCacheMissingMessage(),
        )
        assertEquals("Update install failed: metadata reader failed", UpdateTextPolicy.updateInstallFailedMessage("metadata reader failed"))
        assertEquals(
            "APK verified. Grant install permission to continue.",
            UpdateTextPolicy.apkVerifiedGrantInstallPermissionMessage(),
        )
        assertEquals(
            "APK verified. Android installer started.",
            UpdateTextPolicy.apkVerifiedAndroidInstallerStartedMessage(),
        )
    }

    @Test
    fun notificationBodyPrefersVerifiedVersion() {
        assertEquals(
            "Version 0.4.3 is ready. Open Kani to install it.",
            UpdateTextPolicy.notificationBody("v0.4.3", "manual message")
        )
    }

    @Test
    fun notificationBodyFallsBackToMessageOrDefault() {
        assertEquals("Kani update is ready. Open Kani to install it.", UpdateTextPolicy.DEFAULT_PENDING_UPDATE_MESSAGE)
        assertEquals(
            "Checksum verified. Open Kani to install it.",
            UpdateTextPolicy.notificationBody("", "Checksum verified.")
        )
        assertEquals(
            "Kani update is ready. Open Kani to install it.",
            UpdateTextPolicy.notificationBody(null, "  ")
        )
        assertEquals(
            "Kani update is ready. Open Kani to install it.",
            UpdateTextPolicy.notificationBody(null, null)
        )
    }

    @Test
    fun notificationCopyTranslatesToJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            assertEquals("Kaniの更新をインストールできます", UpdateTextPolicy.notificationTitle())
            assertEquals("アプリの更新", UpdateTextPolicy.notificationChannelName())
            assertEquals("Kaniの更新をわかりやすくお知らせします。", UpdateTextPolicy.notificationChannelDescription())
            assertEquals(
                "バージョン 0.4.3 の準備ができました。Kaniを開いてインストールします。",
                UpdateTextPolicy.notificationBody("v0.4.3", "manual message")
            )
            assertEquals(
                "Checksum verified. Kaniを開いてインストールします。",
                UpdateTextPolicy.notificationBody("", "Checksum verified.")
            )
            assertEquals(
                "Kaniの更新準備ができました。Kaniを開いてインストールします。",
                UpdateTextPolicy.notificationBody(null, null)
            )
        }
    }

    @Test
    fun updateResultMessagesTranslateToJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            assertEquals("すでにバージョン 1.2.3 を使用しています。", UpdateTextPolicy.alreadyOnVersionMessage("1.2.3"))
            assertEquals("更新の確認に失敗しました: download broke", UpdateTextPolicy.updateCheckFailedMessage("download broke"))
            assertEquals("インストール待ちの確認済みAPKはありません。", UpdateTextPolicy.noVerifiedApkWaitingMessage())
            assertEquals(
                "確認済みAPKのキャッシュがありません。もう一度確認してダウンロードしてください。",
                UpdateTextPolicy.verifiedApkCacheMissingMessage(),
            )
            assertEquals("更新のインストールに失敗しました: metadata reader failed", UpdateTextPolicy.updateInstallFailedMessage("metadata reader failed"))
            assertEquals(
                "APKを確認しました。続行するにはインストール権限を許可してください。",
                UpdateTextPolicy.apkVerifiedGrantInstallPermissionMessage(),
            )
            assertEquals(
                "APKを確認しました。Androidのインストーラーを起動しました。",
                UpdateTextPolicy.apkVerifiedAndroidInstallerStartedMessage(),
            )
        }
    }

    @Test
    fun installPermissionDialogCopyMentionsPendingVersionWhenKnown() {
        assertEquals("Keep Kani up to date", UpdateTextPolicy.installPermissionDialogTitle())
        assertEquals("Allow", UpdateTextPolicy.installPermissionDialogAllowLabel())
        assertEquals("Not now", UpdateTextPolicy.installPermissionDialogNotNowLabel())
        assertEquals(
            "Kani 0.5.0 is verified and ready to install. " +
                "Allow Kani to install updates on the next Android settings screen " +
                "and it will update itself automatically.",
            UpdateTextPolicy.installPermissionDialogMessage("v0.5.0"),
        )
        assertEquals(
            "Kani can download and install verified updates by itself. " +
                "Allow Kani to install updates on the next Android settings screen.",
            UpdateTextPolicy.installPermissionDialogMessage(null),
        )
        assertEquals(
            "Kani can download and install verified updates by itself. " +
                "Allow Kani to install updates on the next Android settings screen.",
            UpdateTextPolicy.installPermissionDialogMessage("  "),
        )
    }

    @Test
    fun installPermissionDialogCopyTranslatesToJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            assertEquals("Kaniを最新の状態に保つ", UpdateTextPolicy.installPermissionDialogTitle())
            assertEquals("許可する", UpdateTextPolicy.installPermissionDialogAllowLabel())
            assertEquals("今はしない", UpdateTextPolicy.installPermissionDialogNotNowLabel())
            assertEquals(
                "Kani 0.5.0 の確認が完了し、インストールの準備ができました。" +
                    "次のAndroid設定画面でKaniによる更新のインストールを許可すると、自動的に更新されます。",
                UpdateTextPolicy.installPermissionDialogMessage("v0.5.0"),
            )
            assertEquals(
                "Kaniは確認済みの更新を自動でダウンロードしてインストールできます。" +
                    "次のAndroid設定画面でKaniによる更新のインストールを許可してください。",
                UpdateTextPolicy.installPermissionDialogMessage(""),
            )
        }
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
