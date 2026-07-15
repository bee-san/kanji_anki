package dev.bee.kanjianki.core

import java.util.Locale

enum class SyncFailureClassification {
    TRANSIENT_LOCK,
    PERMISSION_DENIED,
    PROVIDER_UNAVAILABLE,
    PERMANENT_OTHER;

    companion object {
        private const val JAPANESE_LANGUAGE = "ja"

        @JvmStatic
        fun classify(message: String?, permanentFailure: Boolean, retryable: Boolean): SyncFailureClassification {
            val lower = message?.lowercase(Locale.ROOT) ?: ""
            return when {
                lower.contains("permission") || lower.contains("denied") -> PERMISSION_DENIED
                lower.contains("not installed") || lower.contains("provider is not") -> PROVIDER_UNAVAILABLE
                lower.contains("timed out") || lower.contains("lock") || retryable -> TRANSIENT_LOCK
                permanentFailure -> PERMANENT_OTHER
                else -> PERMANENT_OTHER
            }
        }

        @JvmStatic
        fun userMessage(classification: SyncFailureClassification): String {
            val japanese = Locale.getDefault().language == JAPANESE_LANGUAGE
            return when (classification) {
                TRANSIENT_LOCK -> if (japanese)
                    "AnkiDroidが一時的にビジーです。自動的にリトライされます。"
                else
                    "AnkiDroid is temporarily busy. Will retry automatically."
                PERMISSION_DENIED -> if (japanese)
                    "AnkiDroidの権限が必要です。設定で権限を付与してください。"
                else
                    "AnkiDroid permission needed. Grant permission in Settings."
                PROVIDER_UNAVAILABLE -> if (japanese)
                    "AnkiDroidがインストールされていないか、プロバイダーが応答していません。"
                else
                    "AnkiDroid is not installed or its provider is not responding."
                PERMANENT_OTHER -> if (japanese)
                    "同期に失敗しました。設定を確認してください。"
                else
                    "Sync failed. Check your settings."
            }
        }
    }
}
