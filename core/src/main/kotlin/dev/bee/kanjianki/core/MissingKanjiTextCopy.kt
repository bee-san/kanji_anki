package dev.bee.kanjianki.core

import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

object MissingKanjiTextCopy {
    const val LABEL_MISSING_KANJI = "Missing Kanji"
    const val LABEL_SCAN_ANKI = "Scan Anki"
    const val LABEL_SCAN_AGAIN = "Scan again"
    const val LABEL_GRANT_ACCESS = "Grant access"
    const val LABEL_INSTALL_ANKIDROID = "Install AnkiDroid"
    const val LABEL_CANCEL = "Cancel scan"
    const val LABEL_CUSTOM = "Custom"
    const val LABEL_APPLY_RANGE = "Apply range"
    const val LABEL_SEARCH = "Search missing kanji"
    const val LABEL_CLOSE = "Close"

    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun actionLabel(): String = localized(LABEL_MISSING_KANJI, "未登録漢字")

    @JvmStatic
    fun title(): String = actionLabel()

    @JvmStatic
    fun subtitle(): String = localized(
        "Find useful kanji that do not appear anywhere in Anki.",
        "Ankiのどこにもない、学ぶ価値のある漢字を探します。",
    )

    @JvmStatic
    fun firstRunTitle(): String = localized(
        "Compare your whole collection",
        "コレクション全体を比較",
    )

    @JvmStatic
    fun firstRunBody(): String = localized(
        "Kani checks every note field in Anki, then compares the unique kanji it finds with the selected Jiten rank range. Note text is processed in memory and is never saved.",
        "KaniはAnkiのすべてのノート欄を確認し、見つかった重複のない漢字を、選択したJiten順位の範囲と比較します。ノート本文はメモリ内で処理され、保存されません。",
    )

    @JvmStatic
    fun ankiDroidMissingTitle(): String = localized(
        "AnkiDroid is required",
        "AnkiDroidが必要です",
    )

    @JvmStatic
    fun ankiDroidMissingBody(): String = localized(
        "Install AnkiDroid to scan the kanji represented in your collection.",
        "コレクションに含まれる漢字を調べるには、AnkiDroidをインストールしてください。",
    )

    @JvmStatic
    fun permissionTitle(): String = localized(
        "Allow collection access",
        "コレクションへのアクセスを許可",
    )

    @JvmStatic
    fun permissionBody(): String = localized(
        "Kani needs AnkiDroid's database permission to read all note fields. Only aggregate kanji membership and scan counts are stored.",
        "すべてのノート欄を読むため、KaniにはAnkiDroidのデータベース権限が必要です。保存されるのは漢字の集合とスキャン件数だけです。",
    )

    @JvmStatic
    fun providerUnavailableTitle(): String = localized(
        "AnkiDroid is unavailable",
        "AnkiDroidを利用できません",
    )

    @JvmStatic
    fun providerUnavailableBody(): String = localized(
        "Kani could not read the AnkiDroid collection provider. Close any collection maintenance in AnkiDroid, then try again.",
        "AnkiDroidのコレクションプロバイダーを読み取れませんでした。AnkiDroidでのメンテナンスを終了してから、もう一度お試しください。",
    )

    @JvmStatic
    fun scanningTitle(): String = localized(
        "Scanning Anki",
        "Ankiをスキャン中",
    )

    @JvmStatic
    fun scanningBody(): String = localized(
        "Checking every note field. You can cancel without replacing the last completed report.",
        "すべてのノート欄を確認しています。キャンセルしても、前回完了したレポートは置き換わりません。",
    )

    @JvmStatic
    fun scanningProgress(notesScanned: Int, uniqueKanji: Int): String = localized(
        "${number(notesScanned)} notes checked · ${number(uniqueKanji)} unique kanji",
        "${number(notesScanned)}件のノートを確認・重複なしの漢字${number(uniqueKanji)}字",
    )

    @JvmStatic
    fun scanningSkipped(skippedNotes: Int): String = localized(
        "${number(skippedNotes)} malformed notes skipped",
        "不正なノート${number(skippedNotes)}件をスキップ",
    )

    @JvmStatic
    fun cancellingLabel(): String = localized(
        "Cancelling safely…",
        "安全にキャンセルしています…",
    )

    @JvmStatic
    fun scanErrorTitle(failureCode: String?): String = when (failureCode) {
        "cancelled" -> localized("Scan cancelled", "スキャンをキャンセルしました")
        "permission_missing" -> permissionTitle()
        "not_installed" -> ankiDroidMissingTitle()
        "dictionary_unavailable" -> localized(
            "Dictionary unavailable",
            "辞書を利用できません",
        )
        else -> localized("Scan needs attention", "スキャンを完了できませんでした")
    }

    @JvmStatic
    fun scanErrorBody(failureCode: String?): String = when (failureCode) {
        "cancelled" -> localized(
            "No partial inventory was published. Start another scan when you are ready.",
            "途中の結果は公開されていません。準備ができたら、もう一度スキャンしてください。",
        )
        "permission_missing" -> permissionBody()
        "not_installed" -> ankiDroidMissingBody()
        "dictionary_unavailable" -> localized(
            "Kani could not load its offline dictionary. Retry after the dictionary finishes installing.",
            "オフライン辞書を読み込めませんでした。辞書のインストール完了後に再試行してください。",
        )
        else -> providerUnavailableBody()
    }

    @JvmStatic
    fun staleResultsLabel(reason: String?): String = when (reason) {
        "cancelled" -> localized(
            "The refresh was cancelled. Showing the last completed scan.",
            "更新はキャンセルされました。前回完了したスキャンを表示しています。",
        )
        "failed" -> localized(
            "The latest refresh failed. Showing the last completed scan.",
            "最新の更新に失敗しました。前回完了したスキャンを表示しています。",
        )
        else -> localized(
            "This report is over 7 days old. Scan again for current results.",
            "このレポートは7日以上前のものです。最新の結果を得るには再スキャンしてください。",
        )
    }

    @JvmStatic
    fun malformedRowsWarning(skippedNotes: Int): String = localized(
        "${number(skippedNotes)} malformed notes could not be read. All other notes are included.",
        "不正なノート${number(skippedNotes)}件を読み取れませんでした。その他のノートはすべて含まれています。",
    )

    @JvmStatic
    fun frequencyTitle(): String = localized(
        "Jiten frequency range",
        "Jiten頻度順位の範囲",
    )

    @JvmStatic
    fun frequencyBody(): String = localized(
        "Smaller rank numbers are more frequent. Rank bounds are inclusive.",
        "順位の数字が小さいほど使用頻度が高くなります。指定した両端の順位を含みます。",
    )

    @JvmStatic
    fun topPresetLabel(maximumRank: Int): String = localized(
        "Top ${number(maximumRank)}",
        "上位${number(maximumRank)}",
    )

    @JvmStatic
    fun customLabel(): String = localized(LABEL_CUSTOM, "カスタム")

    @JvmStatic
    fun minimumRankLabel(): String = localized("Minimum rank", "最小順位")

    @JvmStatic
    fun maximumRankLabel(): String = localized("Maximum rank", "最大順位")

    @JvmStatic
    fun includeUnrankedLabel(): String = localized(
        "Include unranked dictionary kanji",
        "順位のない辞書漢字を含める",
    )

    @JvmStatic
    fun includeUnrankedHelper(): String = localized(
        "Unranked means no Jiten frequency data, not least frequent.",
        "順位なしはJiten頻度データがないという意味で、最低頻度ではありません。",
    )

    @JvmStatic
    fun applyRangeLabel(): String = localized(LABEL_APPLY_RANGE, "範囲を適用")

    @JvmStatic
    fun invalidRangeMessage(reason: String?): String = when (reason) {
        "inverted" -> localized(
            "Minimum rank cannot be greater than maximum rank.",
            "最小順位を最大順位より大きくすることはできません。",
        )
        else -> localized(
            "Enter positive whole-number ranks.",
            "正の整数で順位を入力してください。",
        )
    }

    @JvmStatic
    fun expectedEligibleLoading(): String = localized(
        "Checking expected dictionary count…",
        "辞書の対象件数を確認中…",
    )

    @JvmStatic
    fun expectedEligibleCount(count: Int): String = localized(
        "${number(count)} dictionary kanji in this range",
        "この範囲の辞書漢字は${number(count)}字",
    )

    @JvmStatic
    fun lastScanLabel(completedAtMillis: Long): String = localized(
        "Last scan: ${dateTime(completedAtMillis)}",
        "最終スキャン: ${dateTime(completedAtMillis)}",
    )

    @JvmStatic
    fun notesScannedMetric(count: Int): String = localized(
        "${number(count)} notes scanned",
        "スキャン済みノート ${number(count)}件",
    )

    @JvmStatic
    fun uniqueAnkiMetric(count: Int): String = localized(
        "${number(count)} unique kanji in Anki",
        "Anki内の重複なし漢字 ${number(count)}字",
    )

    @JvmStatic
    fun eligibleMetric(count: Int): String = localized(
        "${number(count)} eligible dictionary kanji",
        "対象の辞書漢字 ${number(count)}字",
    )

    @JvmStatic
    fun missingMetric(count: Int): String = localized(
        "${number(count)} missing",
        "未登録 ${number(count)}字",
    )

    @JvmStatic
    fun searchLabel(): String = localized(LABEL_SEARCH, "未登録漢字を検索")

    @JvmStatic
    fun clearSearchDescription(): String = localized(
        "Clear missing kanji search",
        "未登録漢字の検索を消去",
    )

    @JvmStatic
    fun visibleResultCount(visible: Int, total: Int): String = localized(
        "${number(visible)} of ${number(total)} shown",
        "${number(total)}字中${number(visible)}字を表示",
    )

    @JvmStatic
    fun selectVisibleLabel(count: Int): String = localized(
        "Select ${number(count)} visible",
        "表示中の${number(count)}字を選択",
    )

    @JvmStatic
    fun clearVisibleLabel(count: Int): String = localized(
        "Clear ${number(count)} visible",
        "表示中の${number(count)}字を解除",
    )

    @JvmStatic
    fun selectedCount(count: Int): String = localized(
        "${number(count)} selected",
        "${number(count)}字を選択中",
    )

    @JvmStatic
    fun noSearchResultsTitle(): String = localized(
        "No matching kanji",
        "一致する漢字がありません",
    )

    @JvmStatic
    fun noSearchResultsBody(): String = localized(
        "Try a kanji, meaning, reading, or Jiten rank.",
        "漢字、意味、読み、またはJiten順位で検索してください。",
    )

    @JvmStatic
    fun noEligibleTitle(): String = localized(
        "No dictionary kanji in this range",
        "この範囲に辞書漢字がありません",
    )

    @JvmStatic
    fun noEligibleBody(): String = localized(
        "Choose a different Jiten rank range.",
        "別のJiten順位範囲を選んでください。",
    )

    @JvmStatic
    fun noneMissingTitle(): String = localized(
        "Nothing missing in this range",
        "この範囲に未登録漢字はありません",
    )

    @JvmStatic
    fun noneMissingBody(): String = localized(
        "Every eligible dictionary kanji appears somewhere in your Anki collection.",
        "対象となる辞書漢字はすべてAnkiコレクションのどこかに含まれています。",
    )

    @JvmStatic
    fun rankLabel(rank: Int?): String = if (rank == null) {
        localized("Unranked", "順位なし")
    } else {
        localized("Jiten #${number(rank)}", "Jiten ${number(rank)}位")
    }

    @JvmStatic
    fun unknownMeaningLabel(): String = localized(
        "No English meaning",
        "英語の意味なし",
    )

    @JvmStatic
    fun noReadingLabel(): String = localized(
        "No reading listed",
        "読みの記載なし",
    )

    @JvmStatic
    fun rowDescription(
        literal: String,
        meaning: String,
        reading: String,
        rank: Int?,
        selected: Boolean,
    ): String = localized(
        "$literal, $meaning, $reading, ${rankLabel(rank)}, ${if (selected) "selected" else "not selected"}. Open details.",
        "$literal、$meaning、$reading、${rankLabel(rank)}、${if (selected) "選択済み" else "未選択"}。詳細を開く。",
    )

    @JvmStatic
    fun selectionDescription(literal: String): String = localized(
        "Select $literal",
        "${literal}を選択",
    )

    @JvmStatic
    fun detailsTitle(literal: String): String = localized(
        "Details for $literal",
        "${literal}の詳細",
    )

    @JvmStatic
    fun meaningsLabel(): String = localized("Meanings", "意味")

    @JvmStatic
    fun onReadingsLabel(): String = localized("On readings", "音読み")

    @JvmStatic
    fun kunReadingsLabel(): String = localized("Kun readings", "訓読み")

    @JvmStatic
    fun noValuesLabel(): String = localized("None listed", "記載なし")

    @JvmStatic
    fun closeLabel(): String = localized(LABEL_CLOSE, "閉じる")

    @JvmStatic
    fun scanAnkiLabel(): String = localized(LABEL_SCAN_ANKI, "Ankiをスキャン")

    @JvmStatic
    fun scanAgainLabel(): String = localized(LABEL_SCAN_AGAIN, "再スキャン")

    @JvmStatic
    fun grantAccessLabel(): String = localized(LABEL_GRANT_ACCESS, "アクセスを許可")

    @JvmStatic
    fun installAnkiDroidLabel(): String = localized(LABEL_INSTALL_ANKIDROID, "AnkiDroidをインストール")

    @JvmStatic
    fun cancelLabel(): String = localized(LABEL_CANCEL, "スキャンをキャンセル")

    @JvmStatic
    fun addToKaniLabel(): String = localized("Add to Kani", "Kaniに追加")

    @JvmStatic
    fun inKaniLabel(): String = localized("In Kani", "Kaniに追加済み")

    @JvmStatic
    fun addToKaniConfirmationTitle(): String = localized(
        "Add selected kanji?",
        "選択した漢字を追加しますか？",
    )

    @JvmStatic
    fun addToKaniConfirmationBody(count: Int, newPerDay: Int): String = localized(
        "${number(count)} selected kanji will become eligible for Kani. The normal limit of up to ${number(newPerDay)} new items per day still applies.",
        "選択した${number(count)}字がKaniの学習対象になります。1日最大${number(newPerDay)}件の通常の新規項目制限が適用されます。",
    )

    @JvmStatic
    fun confirmAddToKaniLabel(): String = localized("Add", "追加")

    @JvmStatic
    fun kaniAdmissionResultTitle(): String = localized(
        "Kani list updated",
        "Kaniリストを更新しました",
    )

    @JvmStatic
    fun kaniAdmissionResultBody(
        added: Int,
        alreadyInKani: Int,
        admittedNow: Int,
        deferred: Int,
        skipped: Int,
    ): String = localized(
        "${number(added)} added · ${number(alreadyInKani)} already in Kani\n" +
            "${number(admittedNow)} ready now · ${number(deferred)} waiting for daily admission\n" +
            "${number(skipped)} skipped for incomplete dictionary data",
        "${number(added)}字を追加・${number(alreadyInKani)}字は追加済み\n" +
            "${number(admittedNow)}字は今すぐ学習可能・${number(deferred)}字は日次追加待ち\n" +
            "辞書データ不足で${number(skipped)}字をスキップ",
    )

    @JvmStatic
    fun removeFromKaniLabel(): String = localized("Remove from Kani", "Kaniから削除")

    @JvmStatic
    fun removeFromKaniConfirmationTitle(literal: String): String = localized(
        "Remove $literal from Kani?",
        "${literal}をKaniから削除しますか？",
    )

    @JvmStatic
    fun removeFromKaniConfirmationBody(): String = localized(
        "This is available only before the first review.",
        "初回レビュー前のみ削除できます。",
    )

    @JvmStatic
    fun removedFromKaniBody(literal: String): String = localized(
        "$literal was removed from Kani.",
        "${literal}をKaniから削除しました。",
    )

    @JvmStatic
    fun reviewedSourceKeptBody(literal: String): String = localized(
        "$literal has review history, so its source was kept.",
        "${literal}にはレビュー履歴があるため、追加元を保持しました。",
    )

    @JvmStatic
    fun operationFailedTitle(): String = localized(
        "Could not update Missing Kanji",
        "未登録漢字を更新できませんでした",
    )

    @JvmStatic
    fun operationFailedBody(): String = localized(
        "Nothing was lost. Try again.",
        "データは失われていません。もう一度お試しください。",
    )

    @JvmStatic
    fun studyNowLabel(): String = localized("Study now", "今すぐ学習")

    @JvmStatic
    fun dictionarySourceReason(): String = localized(
        "Added from the offline dictionary.",
        "オフライン辞書から追加しました。",
    )

    @JvmStatic
    fun createAnkiDeckLabel(): String = localized("Create Anki deck", "Ankiデッキを作成")

    @JvmStatic
    fun homeLabel(): String = localized("Home", "ホーム")

    private fun number(value: Int): String =
        NumberFormat.getIntegerInstance(Locale.getDefault()).format(value.coerceAtLeast(0))

    private fun dateTime(value: Long): String = DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
        Locale.getDefault(),
    ).format(Date(value.coerceAtLeast(0L)))

    private fun localized(english: String, japanese: String): String =
        if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english
}
