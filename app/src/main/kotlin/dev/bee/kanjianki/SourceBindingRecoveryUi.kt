package dev.bee.kanjianki

import dev.bee.kanjianki.sync.SourceBindingEvidence
import dev.bee.kanjianki.syncapi.CollectionProviderKind
import dev.bee.kanjianki.syncapi.SourceBindingReason
import java.util.Locale

internal data class SourceBindingRecoveryPresentation(
    val title: String,
    val headline: String,
    val lines: List<String>,
    val firstBindAllowed: Boolean,
    val rebindAllowed: Boolean,
    val newProfileAllowed: Boolean,
)

internal object SourceBindingRecoveryUi {
    fun presentation(
        reason: SourceBindingReason,
        evidence: SourceBindingEvidence?,
        safeStorageAvailable: Boolean,
    ): SourceBindingRecoveryPresentation {
        val firstBind = reason == SourceBindingReason.FIRST_BIND_REQUIRED
        val rebind = safeStorageAvailable && reason in setOf(
            SourceBindingReason.PROVIDER_KIND_CHANGED,
            SourceBindingReason.SOURCE_KEY_CHANGED,
            SourceBindingReason.INSUFFICIENT_OVERLAP,
        )
        val lines = buildList {
            add(reasonLine(reason))
            evidence?.let { add(evidenceLine(it)) }
            add(redactionLine())
            if (!safeStorageAvailable && !firstBind) {
                add(unsupportedRecoveryLine())
            }
        }
        return SourceBindingRecoveryPresentation(
            title = if (firstBind) firstBindTitle() else mismatchTitle(),
            headline = if (firstBind) firstBindHeadline() else mismatchHeadline(),
            lines = lines,
            firstBindAllowed = firstBind,
            rebindAllowed = rebind,
            newProfileAllowed = safeStorageAvailable && !firstBind,
        )
    }

    fun bindingProgressTitle(): String =
        localized("Confirming AnkiDroid collection", "AnkiDroidコレクションを確認中")

    fun freshProfileProgressTitle(): String =
        localized("Preparing a new Kani profile", "新しいKaniプロファイルを準備中")

    fun firstBindLabel(): String =
        localized("Use this collection", "このコレクションを使用")

    fun rebindLabel(): String =
        localized("Rebind this Kani profile", "このKaniプロファイルを再関連付け")

    fun newProfileLabel(): String =
        localized("Start a new Kani profile", "新しいKaniプロファイルを開始")

    fun rebindConfirmTitle(): String =
        localized("Rebind this profile?", "このプロファイルを再関連付けしますか？")

    fun rebindConfirmMessage(): String = localized(
        "Kani will first create a fresh safety backup. It will keep your review history " +
            "and scheduler state, replace the saved source binding, and clear only " +
            "provider-derived caches and write receipts before syncing again.",
        "Kaniは最初に新しい安全バックアップを作成します。復習履歴とスケジューラ状態を保持し、" +
            "保存済みの参照元を置き換え、プロバイダ由来のキャッシュと書き込み記録だけを消去してから再同期します。",
    )

    fun rebindConfirmLabel(): String =
        localized("Back up and rebind", "バックアップして再関連付け")

    fun newProfileConfirmTitle(): String =
        localized("Start a new Kani profile?", "新しいKaniプロファイルを開始しますか？")

    fun newProfileConfirmMessage(): String = localized(
        "Kani will create a durable backup of this profile, stage a fresh local database, " +
            "and restart. The archived profile can be restored from Backup & restore. " +
            "Your AnkiDroid collection is not modified.",
        "Kaniはこのプロファイルの永続バックアップを作成し、新しいローカルデータベースを準備して再起動します。" +
            "保存したプロファイルは「バックアップと復元」から復元できます。AnkiDroidコレクションは変更されません。",
    )

    fun newProfileConfirmLabel(): String =
        localized("Back up and start new", "バックアップして新規開始")

    fun safetyBackupFailed(): String = localized(
        "Kani could not create the required safety backup. No binding or profile data was changed.",
        "必要な安全バックアップを作成できませんでした。関連付けやプロファイルデータは変更されていません。",
    )

    fun freshProfileFailed(): String = localized(
        "Kani could not stage a fresh profile. The current profile and safety backup were preserved.",
        "新しいプロファイルを準備できませんでした。現在のプロファイルと安全バックアップは保持されています。",
    )

    fun verificationFailed(): String = localized(
        "Kani could not safely verify the available AnkiDroid collection. No binding or profile data was changed.",
        "利用可能なAnkiDroidコレクションを安全に確認できませんでした。関連付けやプロファイルデータは変更されていません。",
    )

    fun recoveryFailedTitle(): String =
        localized("Collection recovery stopped", "コレクションの復旧を停止しました")

    private fun firstBindTitle(): String =
        localized("Confirm AnkiDroid collection", "AnkiDroidコレクションを確認")

    private fun firstBindHeadline(): String =
        localized("First local binding required", "最初のローカル関連付けが必要です")

    private fun mismatchTitle(): String =
        localized("AnkiDroid collection changed", "AnkiDroidコレクションが変更されました")

    private fun mismatchHeadline(): String =
        localized("Sync was blocked before local or AnkiDroid data changed", "データ変更前に同期を停止しました")

    private fun reasonLine(reason: SourceBindingReason): String = when (reason) {
        SourceBindingReason.FIRST_BIND_REQUIRED -> localized(
            "This empty Kani profile has not been linked to an AnkiDroid collection.",
            "この空のKaniプロファイルはAnkiDroidコレクションにまだ関連付けられていません。",
        )
        SourceBindingReason.PROVIDER_KIND_CHANGED,
        SourceBindingReason.SOURCE_KEY_CHANGED,
        -> localized(
            "The available source does not match the source saved in this Kani profile.",
            "利用可能な参照元が、このKaniプロファイルに保存された参照元と一致しません。",
        )
        SourceBindingReason.INSUFFICIENT_OVERLAP -> localized(
            "The available collection does not contain enough matching note and card evidence.",
            "利用可能なコレクションには、一致するノートとカードの証拠が十分にありません。",
        )
        SourceBindingReason.NO_STABLE_IDS -> localized(
            "AnkiDroid did not provide stable note or card evidence for a safe decision.",
            "安全に判断するための安定したノートまたはカードの証拠をAnkiDroidから取得できませんでした。",
        )
        SourceBindingReason.UNKNOWN_ORIGIN -> localized(
            "Kani cannot verify which collection this local database came from.",
            "このローカルデータベースの元のコレクションを確認できません。",
        )
        SourceBindingReason.UNSUPPORTED_VERSION -> localized(
            "This profile uses a newer source-binding format.",
            "このプロファイルは新しい参照元関連付け形式を使用しています。",
        )
        SourceBindingReason.BACKUP_REQUIRED,
        SourceBindingReason.FRESH_SALT_REQUIRED,
        -> localized(
            "The explicit recovery requirements were not completed.",
            "明示的な復旧要件を完了できませんでした。",
        )
        SourceBindingReason.VALIDATED,
        SourceBindingReason.EXPLICIT_BIND,
        SourceBindingReason.EXPLICIT_REBIND,
        -> localized("The source is validated.", "参照元を確認しました。")
    }

    private fun evidenceLine(evidence: SourceBindingEvidence): String {
        val provider = when (evidence.candidate.providerKind) {
            CollectionProviderKind.ANKIDROID -> "AnkiDroid"
            CollectionProviderKind.ANKI_CONNECT -> "AnkiConnect"
            CollectionProviderKind.TEST -> "Test provider"
        }
        return localized(
            "$provider evidence: ${evidence.candidate.noteIdSampleSize} note IDs and " +
                "${evidence.candidate.cardIdSampleSize} card IDs available; " +
                "${evidence.priorNoteSampleSize} note and ${evidence.priorCardSampleSize} card " +
                "fingerprints saved.",
            "$provider の証拠: ノートID ${evidence.candidate.noteIdSampleSize}件、カードID " +
                "${evidence.candidate.cardIdSampleSize}件を取得。保存済み指紋はノート " +
                "${evidence.priorNoteSampleSize}件、カード ${evidence.priorCardSampleSize}件です。",
        )
    }

    private fun redactionLine(): String = localized(
        "Profile names, provider addresses, and raw note or card IDs are hidden.",
        "プロファイル名、プロバイダのアドレス、生のノートIDとカードIDは表示しません。",
    )

    private fun unsupportedRecoveryLine(): String = localized(
        "Creating or rebinding a profile requires Android 11 or later. On this device, " +
            "switch back to the previously linked AnkiDroid collection and try again.",
        "プロファイルの作成または再関連付けにはAndroid 11以降が必要です。この端末では、" +
            "以前に関連付けたAnkiDroidコレクションへ戻してから再試行してください。",
    )

    private fun localized(english: String, japanese: String): String =
        if (Locale.getDefault().language == "ja") japanese else english
}
