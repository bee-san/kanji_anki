package dev.bee.kanjianki.core

import java.text.NumberFormat
import java.util.Locale

object FsrsPersonalizationTextCopy {
    @JvmStatic fun title(): String = localized("Personalized scheduling", "個人向けスケジュール")

    @JvmStatic fun body(): String = localized(
        "Optionally fit FSRS to your real review history. New weights are used only when at least 400 training reviews (about 500 reviews total) show a validation improvement of 1% or more.",
        "実際の復習履歴に合わせてFSRSを調整できます。学習用データが400件以上（全体で約500件）あり、検証結果が1%以上改善した場合にのみ新しい重みを使用します。",
    )

    @JvmStatic fun toggleLabel(): String = localized(
        "Use my review history",
        "自分の復習履歴を使用",
    )

    @JvmStatic fun fitNowLabel(): String = localized("Fit now", "今すぐ調整")

    @JvmStatic fun resetLabel(): String = localized("Reset to defaults", "標準設定に戻す")

    @JvmStatic fun enabledToast(): String = localized(
        "Personalized scheduling turned on.",
        "個人向けスケジュールをオンにしました。",
    )

    @JvmStatic fun disabledToast(): String = localized(
        "Personalized scheduling turned off. Using defaults.",
        "個人向けスケジュールをオフにしました。標準設定を使用します。",
    )

    @JvmStatic fun fitQueuedToast(): String = localized(
        "FSRS fitting queued.",
        "FSRSの調整を予約しました。",
    )

    @JvmStatic fun turnOnFirstToast(): String = localized(
        "Turn on personalized scheduling first.",
        "先に個人向けスケジュールをオンにしてください。",
    )

    @JvmStatic fun resetToast(): String = localized(
        "Fitted weights cleared. Using defaults.",
        "調整済みの重みを消去しました。標準設定を使用します。",
    )

    @JvmStatic
    fun status(
        enabled: Boolean,
        adopted: Boolean,
        sampleCount: Int,
        relativeImprovement: Double?,
        reason: String?,
    ): String {
        if (!enabled) {
            return localized("Off — using defaults", "オフ — 標準設定を使用中")
        }
        if (adopted && reason == FsrsWeightFitter.REASON_CANCELLED) {
            return localized(
                "Using your previously fitted weights — the last fit was cancelled",
                "以前の調整済み重みを使用中 — 前回の調整はキャンセルされました",
            )
        }
        if (adopted && reason == FsrsWeightFitter.REASON_FAILED) {
            return localized(
                "Using your previously fitted weights — the last fit failed",
                "以前の調整済み重みを使用中 — 前回の調整に失敗しました",
            )
        }
        if (adopted && relativeImprovement != null && relativeImprovement.isFinite()) {
            val locale = Locale.getDefault()
            val percent = NumberFormat.getPercentInstance(locale).apply {
                minimumFractionDigits = 1
                maximumFractionDigits = 1
            }.format(relativeImprovement)
            val reviews = NumberFormat.getIntegerInstance(locale).format(sampleCount.coerceAtLeast(0))
            return localized(
                "Using your fitted weights — $percent better on your last $reviews reviews",
                "調整済みの重みを使用中 — 直近${reviews}件の復習で${percent}改善",
            )
        }
        if (adopted) {
            return localized("Using your fitted weights", "調整済みの重みを使用中")
        }
        return when (reason) {
            FsrsWeightFitter.REASON_NOT_ENOUGH_HISTORY -> localized(
                "Using defaults — not enough history yet",
                "標準設定を使用中 — 履歴がまだ不足しています",
            )
            FsrsWeightFitter.REASON_INSUFFICIENT_IMPROVEMENT -> localized(
                "Using defaults — the fitted weights did not improve validation by 1%",
                "標準設定を使用中 — 調整後の検証結果が1%改善しませんでした",
            )
            FsrsWeightFitter.REASON_CANCELLED -> localized(
                "Using defaults — the last fit was cancelled",
                "標準設定を使用中 — 前回の調整はキャンセルされました",
            )
            FsrsWeightFitter.REASON_DISABLED_DURING_FIT -> localized(
                "Using defaults — personalization was turned off during the fit",
                "標準設定を使用中 — 調整中に個人向けスケジュールがオフになりました",
            )
            FsrsWeightFitter.REASON_FAILED -> localized(
                "Using defaults — the last fit failed",
                "標準設定を使用中 — 前回の調整に失敗しました",
            )
            else -> localized(
                "Using defaults — no fit has run yet",
                "標準設定を使用中 — まだ調整していません",
            )
        }
    }

    private fun localized(english: String, japanese: String): String =
        if (Locale.getDefault().language == "ja") japanese else english
}
