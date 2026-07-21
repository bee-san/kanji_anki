package dev.bee.kanjianki.core

import java.util.Locale

object HowKaniWorksCopy {
    private const val JAPANESE_LANGUAGE = "ja"
    private const val MAX_SECTION_CHARS = 600

    private fun localizedText(english: String, japanese: String): String =
        if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english

    @JvmStatic
    fun pageTitle(): String = localizedText("How Kani works", "Kaniの仕組み")

    @JvmStatic
    fun sections(): List<Section> = listOf(
        Section(
            localizedText("What Kani reads from AnkiDroid", "AnkiDroidから読み取るもの"),
            localizedText(
                "Kani reads your note text, card scheduling data, and suspension status through AnkiDroid's content provider. It never modifies your review schedule, card queue, due dates, or deck assignments. The only writes Kani makes to AnkiDroid are note-level tags (kani_archived and kani_repaired) — and those are always manual-confirm-only.",
                "KaniはAnkiDroidのコンテンツプロバイダーを通じて、ノートのテキスト、カードのスケジュールデータ、停止状態を読み取ります。レビュースケジュール、カードキュー、期日、デッキ割り当てを変更することはありません。Kaniが行う唯一の書き込みはノートレベルのタグ（kani_archivedとkani_repaired）であり、常に手動確認が必要です。"
            )
        ),
        Section(
            localizedText("Two core checks", "2つのコアチェック"),
            localizedText(
                "Every kanji in your deck has two core memories that Kani tracks independently: recognition (can you identify the kanji's meaning?) and contextual reading (can you read it in a real word?). Each core has its own FSRS-scheduled review cycle.",
                "デッキ内のすべての漢字には、Kaniが独立して追跡する2つのコアメモリーがあります：認識（漢字の意味を識別できるか？）と文脈読み（実際の単語で読めるか？）。各コアには独自のFSRSスケジュールされたレビューサイクルがあります。"
            )
        ),
        Section(
            localizedText("Variants", "バリエーション"),
            localizedText(
                "Font variation and sentence reading are optional presentations of the same core memories — they keep practice fresh without adding separate scheduler queues. You can enable or disable them in Settings.",
                "フォントバリエーションと文読みは、同じコアメモリーの任意のプレゼンテーションです。別のスケジューラーキューを追加せずに練習を新鮮に保ちます。設定で有効・無効を切り替えられます。"
            )
        ),
        Section(
            localizedText("Repair tasks", "修復タスク"),
            localizedText(
                "When you confuse similar kanji or struggle with a reading, Kani inserts targeted repair tasks inline — similar-kanji discrimination, reading-choice cards, or writing practice. These are practice-only: they help you correct the confusion but don't create separate long-term memories.",
                "似た漢字を混同したり読みに苦労した場合、Kaniはインラインで対象を絞った修復タスクを挿入します。類似漢字の識別、読み選択カード、書き取り練習などです。これらは練習のみで、別の長期記憶を作成しません。"
            )
        ),
        Section(
            localizedText("Pass, fail, and revalidation", "合格・不合格と再確認"),
            localizedText(
                "Pass and Fail are the two study answers. A due Fail updates that core memory once, starts practice-only repair, then rechecks the same core. Strong recognition unlocks contextual reading only after the memory-strength and pass-count gates; contextual reading never demotes back to recognition.",
                "合格と不合格が2つの学習回答です。期日の不合格はコアメモリーを1回だけ更新し、練習専用の修復後に同じコアを再確認します。認識は記憶強度と合格回数の条件を満たした場合のみ文脈読みに進み、文脈読みから認識へ降格することはありません。"
            )
        ),
        Section(
            localizedText("Backups", "バックアップ"),
            localizedText(
                "Kani automatically backs up its database daily with a tiered retention policy (7 daily, 4 weekly). You can also export a fresh snapshot or restore from a backup in Settings > Automation > Backup & restore.",
                "Kaniはティアード保持ポリシー（7日分、4週間分）でデータベースを毎日自動バックアップします。設定 > 自動化 > バックアップ＆復元で新しいスナップショットのエクスポートやバックアップからの復元も可能です。"
            )
        ),
    )

    class Section(
        @JvmField val title: String,
        @JvmField val body: String,
    ) {
        init {
            require(title.isNotBlank()) { "Section title must not be blank" }
            require(body.isNotBlank()) { "Section body must not be blank" }
            require(body.length <= MAX_SECTION_CHARS) {
                "Section body exceeds $MAX_SECTION_CHARS chars: ${body.length}"
            }
        }
    }
}
