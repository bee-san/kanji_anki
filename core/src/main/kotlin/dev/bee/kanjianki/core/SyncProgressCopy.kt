package dev.bee.kanjianki.core

import java.util.Locale

object SyncProgressCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun stageTitle(stage: Stage?): String {
        return if (isJapaneseLocale()) {
            when (stage) {
                Stage.FINDING_NOTE_TYPE -> "ノートタイプを確認中"
                Stage.READING_NOTES -> "ノートを読み込み中"
                Stage.SCANNING_CARDS -> "カードをスキャン中"
                Stage.PROCESSING_IMPORTED_CARDS -> "取り込んだカードを処理中"
                Stage.SAVING_LOCAL_DATA -> "ローカルデータを保存中"
                Stage.BUILDING_PRACTICE_QUEUE -> "練習キューを作成中"
                Stage.ARCHIVING_IMPORTED_CARDS -> "取り込んだ休止カードをアーカイブ中"
                Stage.TAGGING_REPAIRED -> "修復済みカードをタグ付け中"
                null -> "カードを同期中"
            }
        } else {
            when (stage) {
                Stage.FINDING_NOTE_TYPE -> "Finding note type"
                Stage.READING_NOTES -> "Reading notes"
                Stage.SCANNING_CARDS -> "Scanning cards"
                Stage.PROCESSING_IMPORTED_CARDS -> "Processing imported cards"
                Stage.SAVING_LOCAL_DATA -> "Saving local data"
                Stage.BUILDING_PRACTICE_QUEUE -> "Building practice queue"
                Stage.ARCHIVING_IMPORTED_CARDS -> "Archiving imported suspended cards"
                Stage.TAGGING_REPAIRED -> "Tagging repaired cards"
                null -> "Syncing cards"
            }
        }
    }

    @JvmStatic
    fun stageBody(stage: Stage?): String {
        return if (isJapaneseLocale()) {
            when (stage) {
                Stage.FINDING_NOTE_TYPE -> "コレクションの形を確認しています。"
                Stage.READING_NOTES -> "カード総数が分かる前にノートを読み込んでいます。"
                Stage.PROCESSING_IMPORTED_CARDS -> "AnkiDroid の読み取りが完了しました。取り込んだカードをローカルで処理しています。"
                Stage.SAVING_LOCAL_DATA -> "Anki のスナップショットと取り込み証跡を保存しています。"
                Stage.BUILDING_PRACTICE_QUEUE -> "練習キューを保存しています。"
                Stage.ARCHIVING_IMPORTED_CARDS -> "取り込んだ休止カードをアーカイブしています。"
                Stage.TAGGING_REPAIRED -> "修復済みノートをAnkiDroidでタグ付けしています。"
                Stage.SCANNING_CARDS,
                null -> "カードをスキャンする準備をしています。"
            }
        } else {
            when (stage) {
                Stage.FINDING_NOTE_TYPE -> "Checking collection shape."
                Stage.READING_NOTES -> "Reading notes before the card total is known."
                Stage.PROCESSING_IMPORTED_CARDS -> "AnkiDroid read finished. Processing imported cards locally."
                Stage.SAVING_LOCAL_DATA -> "Saving the Anki snapshot and import evidence."
                Stage.BUILDING_PRACTICE_QUEUE -> "Saving the practice queue."
                Stage.ARCHIVING_IMPORTED_CARDS -> "Updating archived suspended cards."
                Stage.TAGGING_REPAIRED -> "Tagging repaired notes in AnkiDroid."
                Stage.SCANNING_CARDS,
                null -> "Preparing card scan."
            }
        }
    }

    @JvmStatic
    fun progressPermille(scannedCards: Int, totalCards: Int): Int {
        if (totalCards <= 0) {
            return 1000
        }
        return minOf(1000, maxOf(0, Math.round((maxOf(0, scannedCards) * 1000f) / totalCards)))
    }

    @JvmStatic
    fun cardProgressText(scannedCards: Int, totalCards: Int): String {
        val scanned = maxOf(0, scannedCards)
        val total = maxOf(0, totalCards)
        return if (isJapaneseLocale()) {
            "$scanned / $total 枚をスキャン済み"
        } else {
            "$scanned / $total cards scanned"
        }
    }

    @JvmStatic
    fun scanRateText(stage: Stage?, scannedCards: Int, totalCards: Int, elapsedMillis: Long): String {
        if (stage != Stage.SCANNING_CARDS) {
            return stageBody(stage)
        }
        val scanned = maxOf(0, scannedCards)
        if (scanned <= 0) {
            return localizedText("Scanning cards.", "カードをスキャンしています。")
        }
        val elapsed = maxOf(1L, elapsedMillis)
        val perSecond = scanned * 1000.0 / elapsed
        val rateText = cardsPerSecondText(perSecond)
        val remaining = maxOf(0, maxOf(0, totalCards) - scanned)
        if (remaining == 0) {
            return localizedText("$rateText - finishing up", "$rateText - 仕上げ中")
        }
        if (scanned >= 3 && elapsed >= 1000L) {
            val etaMillis = Math.round((remaining / perSecond) * 1000.0)
            return localizedText(
                "$rateText - about ${shortDuration(etaMillis)} left",
                "$rateText - 残り約${shortDuration(etaMillis)}",
            )
        }
        return localizedText("$rateText - estimating time left", "$rateText - 残り時間を計算中")
    }

    @JvmStatic
    fun shortDuration(millis: Long): String {
        val seconds = maxOf(1L, Math.round(millis / 1000.0))
        if (isJapaneseLocale()) {
            if (seconds < 60L) {
                return "${seconds}秒"
            }
            val minutes = maxOf(1L, Math.round(seconds / 60.0))
            if (minutes < 60L) {
                return "${minutes}分"
            }
            val hours = maxOf(1L, Math.round(minutes / 60.0))
            return "${hours}時間"
        }
        if (seconds < 60L) {
            return "$seconds sec"
        }
        val minutes = maxOf(1L, Math.round(seconds / 60.0))
        if (minutes < 60L) {
            return "$minutes min"
        }
        val hours = maxOf(1L, Math.round(minutes / 60.0))
        return "$hours hr"
    }

    @JvmStatic
    fun progressDescription(value: String): String {
        return localizedText("Sync progress: $value", "同期の進捗: $value")
    }

    private fun cardsPerSecondText(perSecond: Double): String {
        val pattern = if (perSecond >= 10.0) "%.0f" else "%.1f"
        val value = String.format(Locale.US, pattern, perSecond)
        return if (isJapaneseLocale()) "$value 枚/秒" else "$value cards/sec"
    }

    private fun localizedText(english: String, japanese: String): String {
        return if (isJapaneseLocale()) japanese else english
    }

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

    enum class Stage {
        FINDING_NOTE_TYPE,
        READING_NOTES,
        SCANNING_CARDS,
        PROCESSING_IMPORTED_CARDS,
        SAVING_LOCAL_DATA,
        BUILDING_PRACTICE_QUEUE,
        ARCHIVING_IMPORTED_CARDS,
        TAGGING_REPAIRED,
    }
}
