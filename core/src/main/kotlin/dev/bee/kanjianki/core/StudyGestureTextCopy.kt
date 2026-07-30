package dev.bee.kanjianki.core

import java.util.Locale

/** User-facing copy for the flashcard swipe-to-grade setting. */
object StudyGestureTextCopy {
    @JvmStatic fun swipeTitle(): String = localized("Swipe to grade", "スワイプで採点")

    @JvmStatic fun swipeBody(): String = localized(
        "After you reveal the answer, swipe the card left to fail or right to pass. " +
            "Turn this off to grade only with the Pass and Fail buttons — handy when you like to " +
            "pull long answers up to read without the card sliding sideways.",
        "答えを表示した後、カードを左にスワイプで不正解、右にスワイプで正解にできます。" +
            "長い解説を上にスクロールして読む際にカードが横に動かないようにしたい場合や、" +
            "合格・不合格ボタンだけで採点したい場合はオフにしてください。",
    )

    @JvmStatic fun swipeToggleLabel(): String = localized("Enable swipe to grade", "スワイプ採点を有効化")

    @JvmStatic fun swipeStatus(enabled: Boolean): String =
        if (enabled) {
            localized("On — swipe left or right to grade", "オン — 左右にスワイプで採点")
        } else {
            localized("Off — grade with the buttons", "オフ — ボタンで採点")
        }

    @JvmStatic fun swipeEnabledToast(): String =
        localized("Swipe to grade turned on.", "スワイプ採点をオンにしました。")

    @JvmStatic fun swipeDisabledToast(): String =
        localized("Swipe to grade turned off.", "スワイプ採点をオフにしました。")

    private fun localized(english: String, japanese: String): String =
        if (Locale.getDefault().language == "ja") japanese else english
}
