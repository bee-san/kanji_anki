package dev.bee.kanjianki.core

import java.text.NumberFormat
import java.util.Locale

/** User-facing copy for the RWKV memory model (Study gate and Settings panel). */
object MemoryModelTextCopy {
    @JvmStatic fun title(): String = localized("Memory model", "記憶モデル")

    @JvmStatic fun body(): String = localized(
        "Kani schedules reviews with RWKV-Curve, a small neural model of memory that reads your whole review history. It runs entirely on this device.",
        "Kaniは、復習履歴全体を読み取る小さなニューラル記憶モデル「RWKV-Curve」で復習を予定します。処理はすべてこの端末内で行われます。",
    )

    @JvmStatic fun modelLine(label: String, parameters: Int): String = localized(
        "Model: $label (${number(parameters)} parameters)",
        "モデル: $label（パラメータ数 ${number(parameters)}）",
    )

    @JvmStatic fun provenanceLine(): String = localized(
        "Ported from the RWKV scheduler in kuma3-anki (Anki fork, AGPL-3.0-or-later), based on the srs-benchmark RWKV model. " +
            "Kani is AGPL-3.0-or-later; source: $SOURCE_URL",
        "kuma3-anki（Ankiのフォーク、AGPL-3.0-or-later）のRWKVスケジューラを移植したもので、srs-benchmarkのRWKVモデルに基づいています。" +
            "KaniはAGPL-3.0-or-laterで公開されています。ソース: $SOURCE_URL",
    )

    const val SOURCE_URL: String = "https://github.com/bee-san/kanji_anki"

    @JvmStatic fun statusReady(): String = localized("Ready", "準備完了")

    @JvmStatic fun statusChecking(): String = localized("Checking…", "確認中…")

    @JvmStatic fun statusRebuilding(done: Int, total: Int): String =
        if (total <= 0) {
            localized("Preparing…", "準備中…")
        } else {
            localized(
                "Reading your review history… ${number(done)} of ${number(total)}",
                "復習履歴を読み込み中… ${number(total)}件中${number(done)}件",
            )
        }

    @JvmStatic fun statusFailed(detail: String): String = localized(
        "Could not prepare the memory model: $detail",
        "記憶モデルを準備できませんでした: $detail",
    )

    @JvmStatic fun rebuildLabel(): String = localized("Rebuild from history", "履歴から再構築")

    @JvmStatic fun rebuildStartedToast(): String = localized(
        "Rebuilding the memory model from your review history.",
        "復習履歴から記憶モデルを再構築しています。",
    )

    /** Shown when an answer arrives before the model state is ready; the answer can be retried. */
    @JvmStatic fun preparingToast(progressPercent: Int?): String =
        if (progressPercent == null) {
            localized(
                "Kani is preparing its memory model. Try that answer again in a moment.",
                "Kaniが記憶モデルを準備しています。少ししてからもう一度回答してください。",
            )
        } else {
            localized(
                "Kani is preparing its memory model ($progressPercent%). Try that answer again in a moment.",
                "Kaniが記憶モデルを準備しています（$progressPercent%）。少ししてからもう一度回答してください。",
            )
        }

    @JvmStatic fun failedToast(): String = localized(
        "Kani could not prepare its memory model. Open Settings › Memory model to rebuild it.",
        "記憶モデルを準備できませんでした。設定 › 記憶モデルから再構築してください。",
    )

    private fun number(value: Int): String = NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)

    private fun localized(english: String, japanese: String): String =
        if (Locale.getDefault().language == "ja") japanese else english
}
