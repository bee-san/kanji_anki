package dev.bee.kanjianki.missing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.Res
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_add_to_kani
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_cancel
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_clear
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_create_anki
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_dismiss
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_error_title
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_export_csv
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_first_run_body
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_first_run_title
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_permission_body
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_permission_title
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_provider_missing_body
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_provider_missing_title
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_scanning
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_select_all
import dev.bee.kanjianki.feature.missing.kanji.generated.resources.missing_selection
import org.jetbrains.compose.resources.stringResource

/**
 * The Missing Kanji shell's structural labels.
 *
 * Small, like the other feature copies: the scan summary, row text, and result prose
 * are host-computed and arrive on the portable model. Only the first-run/provider
 * states, selection controls, and destination button labels are here.
 */
@Suppress("LongParameterList")
data class MissingKanjiCopy(
    val firstRunTitle: String,
    val firstRunBody: String,
    val providerMissingTitle: String,
    val providerMissingBody: String,
    val permissionTitle: String,
    val permissionBody: String,
    val errorTitle: String,
    val scanning: String,
    val cancel: String,
    val selectAll: String,
    val clear: String,
    val addToKani: String,
    val createAnki: String,
    val exportCsv: String,
    val dismiss: String,
    private val selectionTemplate: String,
) {
    /** The "N selected" line above the destinations. */
    fun selection(count: Int): String = selectionTemplate.replace(FIRST_ARGUMENT, count.toString())

    companion object {
        private const val FIRST_ARGUMENT = "%1\$d"
    }
}

/** Resolves [MissingKanjiCopy] from this module's resources. */
@Composable
fun rememberMissingKanjiCopy(): MissingKanjiCopy {
    val fixed = FixedMissingStrings(
        firstRunTitle = stringResource(Res.string.missing_first_run_title),
        firstRunBody = stringResource(Res.string.missing_first_run_body),
        providerMissingTitle = stringResource(Res.string.missing_provider_missing_title),
        providerMissingBody = stringResource(Res.string.missing_provider_missing_body),
        permissionTitle = stringResource(Res.string.missing_permission_title),
        permissionBody = stringResource(Res.string.missing_permission_body),
        errorTitle = stringResource(Res.string.missing_error_title),
        scanning = stringResource(Res.string.missing_scanning),
        cancel = stringResource(Res.string.missing_cancel),
        selectAll = stringResource(Res.string.missing_select_all),
        clear = stringResource(Res.string.missing_clear),
        addToKani = stringResource(Res.string.missing_add_to_kani),
        createAnki = stringResource(Res.string.missing_create_anki),
        exportCsv = stringResource(Res.string.missing_export_csv),
        dismiss = stringResource(Res.string.missing_dismiss),
        selectionTemplate = stringResource(Res.string.missing_selection),
    )
    return remember(fixed) { fixed.toCopy() }
}

private data class FixedMissingStrings(
    val firstRunTitle: String,
    val firstRunBody: String,
    val providerMissingTitle: String,
    val providerMissingBody: String,
    val permissionTitle: String,
    val permissionBody: String,
    val errorTitle: String,
    val scanning: String,
    val cancel: String,
    val selectAll: String,
    val clear: String,
    val addToKani: String,
    val createAnki: String,
    val exportCsv: String,
    val dismiss: String,
    val selectionTemplate: String,
) {
    fun toCopy(): MissingKanjiCopy = MissingKanjiCopy(
        firstRunTitle = firstRunTitle,
        firstRunBody = firstRunBody,
        providerMissingTitle = providerMissingTitle,
        providerMissingBody = providerMissingBody,
        permissionTitle = permissionTitle,
        permissionBody = permissionBody,
        errorTitle = errorTitle,
        scanning = scanning,
        cancel = cancel,
        selectAll = selectAll,
        clear = clear,
        addToKani = addToKani,
        createAnki = createAnki,
        exportCsv = exportCsv,
        dismiss = dismiss,
        selectionTemplate = selectionTemplate,
    )
}
