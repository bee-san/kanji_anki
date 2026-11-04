package dev.bee.kanjianki.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import dev.bee.kanjianki.feature.home.generated.resources.Res
import dev.bee.kanjianki.feature.home.generated.resources.field_missing_required
import dev.bee.kanjianki.feature.home.generated.resources.field_role_expression
import dev.bee.kanjianki.feature.home.generated.resources.field_role_frequency
import dev.bee.kanjianki.feature.home.generated.resources.field_role_frequency_sort
import dev.bee.kanjianki.feature.home.generated.resources.field_role_meaning
import dev.bee.kanjianki.feature.home.generated.resources.field_role_optional
import dev.bee.kanjianki.feature.home.generated.resources.field_role_reading
import dev.bee.kanjianki.feature.home.generated.resources.field_role_required
import dev.bee.kanjianki.feature.home.generated.resources.field_role_sentence
import dev.bee.kanjianki.feature.home.generated.resources.field_stale
import dev.bee.kanjianki.feature.home.generated.resources.field_unmapped
import dev.bee.kanjianki.feature.home.generated.resources.import_source_active_cards
import dev.bee.kanjianki.feature.home.generated.resources.import_source_browser_query
import dev.bee.kanjianki.feature.home.generated.resources.import_source_suspended_cards
import dev.bee.kanjianki.feature.home.generated.resources.import_source_tagged_cards
import dev.bee.kanjianki.feature.home.generated.resources.import_source_weak_cards
import dev.bee.kanjianki.feature.home.generated.resources.note_type_field_count
import dev.bee.kanjianki.feature.home.generated.resources.note_type_label
import dev.bee.kanjianki.feature.home.generated.resources.note_type_title
import dev.bee.kanjianki.feature.home.generated.resources.note_type_too_few_fields
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_authorize_action
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_authorize_body
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_choose_source_action
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_choose_source_body
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_connect_action
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_connect_body
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_ready_action
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_recover_authorization_action
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_recover_authorization_body
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_recover_sync_action
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_recover_sync_body
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_repaired_tagging
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_source_and_model
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_source_none
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_source_query
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_synced_action
import dev.bee.kanjianki.feature.home.generated.resources.onboarding_synced_body
import dev.bee.kanjianki.feature.home.generated.resources.provider_status_absent
import dev.bee.kanjianki.feature.home.generated.resources.provider_status_ready
import dev.bee.kanjianki.feature.home.generated.resources.provider_status_title
import dev.bee.kanjianki.feature.home.generated.resources.provider_status_unauthorized
import dev.bee.kanjianki.feature.home.generated.resources.repaired_handoff_body
import dev.bee.kanjianki.feature.home.generated.resources.repaired_handoff_copied
import dev.bee.kanjianki.feature.home.generated.resources.repaired_handoff_copy
import dev.bee.kanjianki.feature.home.generated.resources.repaired_handoff_query
import dev.bee.kanjianki.feature.home.generated.resources.repaired_handoff_title
import dev.bee.kanjianki.feature.home.generated.resources.sync_already_running
import dev.bee.kanjianki.feature.home.generated.resources.sync_cancel
import dev.bee.kanjianki.feature.home.generated.resources.sync_cancel_action
import dev.bee.kanjianki.feature.home.generated.resources.sync_confirm_action
import dev.bee.kanjianki.feature.home.generated.resources.sync_confirm_body
import dev.bee.kanjianki.feature.home.generated.resources.sync_confirm_title
import dev.bee.kanjianki.feature.home.generated.resources.sync_failure_fallback
import dev.bee.kanjianki.feature.home.generated.resources.sync_in_progress_title
import dev.bee.kanjianki.presentation.CollectionBinding
import dev.bee.kanjianki.presentation.FieldRole
import dev.bee.kanjianki.presentation.ImportSource
import dev.bee.kanjianki.presentation.NoteTypeOption
import dev.bee.kanjianki.presentation.OnboardingPlan
import dev.bee.kanjianki.presentation.OnboardingStep
import dev.bee.kanjianki.presentation.ProviderReadiness
import dev.bee.kanjianki.presentation.SyncConfirmCopy
import dev.bee.kanjianki.presentation.UiText
import dev.bee.kanjianki.presentation.UiTextResolver
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The count-bearing lines for one plan, already through plural selection.
 *
 * Separate from [HomeCopy] because plural selection is a host rule that only a
 * composable can ask for, and because these change with the plan while everything
 * in [HomeCopy] is fixed for the whole composition. Splitting them keeps the
 * assembly functions in [HomeCopy] pure — they receive the finished line rather than
 * reaching for a resource — which is what makes them testable against known strings.
 *
 * Both are blank when there is nothing to say: no sync has succeeded, or no repaired
 * kanji would be tagged.
 */
data class HomeCountedCopy(
    val syncedBody: String = "",
    val repairedTaggingLine: String = "",
)

/**
 * Every fixed string Home and onboarding display, resolved once per composition.
 *
 * Same shape and same reasons as `ShellCopy`: `stringResource` is a composable, so
 * the plain functions that assemble an onboarding body or a source list cannot call
 * it, and passing a resolved holder down keeps those functions pure and testable
 * against known strings rather than against the shipped wording.
 */
@Suppress("LongParameterList")
data class HomeCopy(
    val providerStatusTitle: String,
    val noteTypeTitle: String,
    val noteTypeTooFewFields: String,
    val fieldUnmapped: String,
    val fieldRequired: String,
    val fieldOptional: String,
    val syncConfirmTitle: String,
    val syncConfirmAction: String,
    val syncCancel: String,
    val syncInProgressTitle: String,
    val syncCancelAction: String,
    val syncAlreadyRunning: String,
    val syncFailureFallback: String,
    val repairedHandoffTitle: String,
    val repairedHandoffQuery: String,
    val repairedHandoffCopy: String,
    val repairedHandoffCopied: String,
    private val sourceNone: String,
    private val sourceAndModel: String,
    private val sourceQuery: String,
    private val syncConfirmBodyTemplate: String,
    private val recoverSyncBodyTemplate: String,
    private val noteTypeLabelTemplate: String,
    private val fieldMissingRequiredTemplate: String,
    private val fieldStaleTemplate: String,
    private val stepBodies: Map<OnboardingStep, String>,
    private val stepActions: Map<OnboardingStep, String>,
    private val readinessLabels: Map<ProviderReadiness, String>,
    private val roleLabels: Map<FieldRole, String>,
    private val sourceLabels: Map<ImportSource, String>,
) {
    fun readinessLabel(readiness: ProviderReadiness): String =
        readinessLabels.getValue(readiness)

    fun roleLabel(role: FieldRole): String = roleLabels.getValue(role)

    fun roleRequirement(role: FieldRole): String =
        if (role.isRequired) fieldRequired else fieldOptional

    fun sourceLabel(source: ImportSource): String = sourceLabels.getValue(source)

    fun missingRequiredField(role: FieldRole): String =
        fieldMissingRequiredTemplate.replace(FIRST_ARGUMENT, roleLabel(role))

    fun staleField(role: FieldRole): String =
        fieldStaleTemplate.replace(FIRST_ARGUMENT, roleLabel(role))

    /**
     * The label on a note type picker row.
     *
     * Names the field count because note types in a real collection are often called
     * nearly the same thing, and the count is what distinguishes one Kani can import
     * from one it cannot.
     */
    fun noteTypeLabel(option: NoteTypeOption, fieldCount: String): String =
        noteTypeLabelTemplate
            .replace(FIRST_ARGUMENT, option.name)
            .replace(SECOND_ARGUMENT, fieldCount)

    /**
     * The one-line summary of what will be imported and from where.
     *
     * Reused verbatim by the synced body and the confirmation, because a user
     * comparing "what did it import" against "what will it import" should be reading
     * the same sentence rather than two paraphrases. Sources are listed in
     * [ImportSource] declaration order rather than set-iteration order, so the line
     * does not reshuffle between renders.
     */
    fun sourceAndModelLine(binding: CollectionBinding): String {
        val sources = ImportSource.entries
            .filter { it in binding.sources }
            .joinToString(SOURCE_SEPARATOR) { sourceLabel(it) }
        val line = sourceAndModel
            .replace(FIRST_ARGUMENT, binding.noteType)
            .replace(SECOND_ARGUMENT, sources.ifEmpty { sourceNone })
        // Appended rather than interpolated: a binding with no browser-query source
        // has no query to name, and an empty "Query: ." reads as a bug.
        return if (ImportSource.BROWSER_QUERY in binding.sources) {
            line + " " + sourceQuery.replace(FIRST_ARGUMENT, binding.browserQuery)
        } else {
            line
        }
    }

    /**
     * The body for an onboarding step, preferring what the host supplied.
     *
     * The host wins because on the connect and authorize steps only it can name the
     * remedy — install AnkiDroid, start Anki, grant this named permission. Blank
     * falls back to the generic shared wording rather than rendering an empty screen,
     * matching how `ShellCopy.failureMessage` treats failure text.
     *
     * The repaired-tagging line is appended, not substituted, and only where
     * [OnboardingPlan.showsRepairedTagging] allows it — so a step whose remedy is
     * granting access never promises a tag write-back that cannot happen yet.
     */
    fun stepBody(
        plan: OnboardingPlan,
        resolver: UiTextResolver,
        counted: HomeCountedCopy = HomeCountedCopy(),
    ): String {
        val base = hostOrSharedBody(plan, resolver, counted)
        return if (plan.showsRepairedTagging && counted.repairedTaggingLine.isNotBlank()) {
            base + "\n" + counted.repairedTaggingLine
        } else {
            base
        }
    }

    private fun hostOrSharedBody(
        plan: OnboardingPlan,
        resolver: UiTextResolver,
        counted: HomeCountedCopy,
    ): String {
        val hostBody = resolver.resolve(plan.hostCopy.guidance)
        if (hostBody.isNotBlank()) return hostBody
        return when (plan.step) {
            OnboardingStep.READY_FIRST_SYNC -> syncConfirmBody(plan.binding)
            OnboardingStep.SYNCED -> syncedBody(plan, counted)
            OnboardingStep.RECOVER_SYNC -> recoverSyncBody(plan, resolver)
            OnboardingStep.CONNECT_PROVIDER,
            OnboardingStep.AUTHORIZE_PROVIDER,
            OnboardingStep.CHOOSE_SOURCE,
            OnboardingStep.RECOVER_AUTHORIZATION,
            -> stepBodies.getValue(plan.step)
        }
    }

    fun stepAction(plan: OnboardingPlan, resolver: UiTextResolver): String {
        val hostLabel = resolver.resolve(plan.hostCopy.primaryActionLabel)
        return hostLabel.ifBlank { stepActions.getValue(plan.step) }
    }

    fun syncConfirmBody(binding: CollectionBinding): String =
        syncConfirmBodyTemplate.replace(FIRST_ARGUMENT, binding.noteType)

    /** What the last sync imported, followed by the configuration it imported under. */
    fun syncedBody(plan: OnboardingPlan, counted: HomeCountedCopy): String =
        (counted.syncedBody + " " + sourceAndModelLine(plan.binding)).trim()

    /**
     * The failure line, preferring the provider's own words.
     *
     * A sync that failed because AnkiConnect said something specific should say so;
     * the fallback exists so a failure with no message still explains itself instead
     * of rendering "Last sync failed: ." with nothing in the gap.
     */
    fun recoverSyncBody(plan: OnboardingPlan, resolver: UiTextResolver): String {
        val reported = plan.failure?.let { resolver.resolve(it.message) }.orEmpty()
        return recoverSyncBodyTemplate.replace(
            FIRST_ARGUMENT,
            reported.ifBlank { syncFailureFallback },
        )
    }

    /**
     * The confirmation wording, for `OnboardingPolicy.syncConfirmation` to wrap.
     *
     * The repaired-tagging count goes in the body the user reads *before* answering,
     * not only on the screen behind the dialog: stating the count at the point of
     * consent is what makes the tag write-back confirmed rather than merely
     * disclosed.
     */
    fun syncConfirmation(
        binding: CollectionBinding,
        repairedTaggingLine: String = "",
    ): SyncConfirmCopy {
        val body = syncConfirmBody(binding)
        return SyncConfirmCopy(
            title = UiText.Literal(syncConfirmTitle),
            body = UiText.Literal(
                if (repairedTaggingLine.isBlank()) body else body + "\n" + repairedTaggingLine,
            ),
            confirmLabel = UiText.Literal(syncConfirmAction),
            dismissLabel = UiText.Literal(syncCancel),
        )
    }

    companion object {
        private const val FIRST_ARGUMENT = "%1\$s"
        private const val SECOND_ARGUMENT = "%2\$s"

        /** Matches `HomeImportOnboardingPolicy.importSources`, which joined on this. */
        private const val SOURCE_SEPARATOR = " + "
    }
}

/**
 * Resolves [HomeCopy] from this module's own Compose Multiplatform resources.
 *
 * The maps are built exhaustively from the enum entries, so a new [OnboardingStep],
 * [FieldRole], [ImportSource], or [ProviderReadiness] is a compile error here rather
 * than a `NoSuchElementException` the first time a user reaches it.
 */
@Composable
@Suppress("LongMethod")
fun rememberHomeCopy(): HomeCopy {
    val stepBodies = OnboardingStep.entries
        .mapNotNull { step -> step.bodyResource()?.let { step to stringResource(it) } }
        .toMap()
    val stepActions = OnboardingStep.entries.associateWith { step ->
        stringResource(step.actionResource())
    }
    val readinessLabels = ProviderReadiness.entries.associateWith { readiness ->
        stringResource(readiness.resource())
    }
    val roleLabels = FieldRole.entries.associateWith { role ->
        stringResource(role.resource())
    }
    val sourceLabels = ImportSource.entries.associateWith { source ->
        stringResource(source.resource())
    }
    val fixed = FixedHomeStrings(
        providerStatusTitle = stringResource(Res.string.provider_status_title),
        noteTypeTitle = stringResource(Res.string.note_type_title),
        noteTypeTooFewFields = stringResource(Res.string.note_type_too_few_fields),
        fieldUnmapped = stringResource(Res.string.field_unmapped),
        fieldRequired = stringResource(Res.string.field_role_required),
        fieldOptional = stringResource(Res.string.field_role_optional),
        syncConfirmTitle = stringResource(Res.string.sync_confirm_title),
        syncConfirmAction = stringResource(Res.string.sync_confirm_action),
        syncCancel = stringResource(Res.string.sync_cancel),
        syncInProgressTitle = stringResource(Res.string.sync_in_progress_title),
        syncCancelAction = stringResource(Res.string.sync_cancel_action),
        syncAlreadyRunning = stringResource(Res.string.sync_already_running),
        syncFailureFallback = stringResource(Res.string.sync_failure_fallback),
        repairedHandoffTitle = stringResource(Res.string.repaired_handoff_title),
        repairedHandoffQuery = stringResource(Res.string.repaired_handoff_query),
        repairedHandoffCopy = stringResource(Res.string.repaired_handoff_copy),
        repairedHandoffCopied = stringResource(Res.string.repaired_handoff_copied),
        sourceNone = stringResource(Res.string.onboarding_source_none),
        sourceAndModel = stringResource(Res.string.onboarding_source_and_model),
        sourceQuery = stringResource(Res.string.onboarding_source_query),
        syncConfirmBodyTemplate = stringResource(Res.string.sync_confirm_body),
        recoverSyncBodyTemplate = stringResource(Res.string.onboarding_recover_sync_body),
        noteTypeLabelTemplate = stringResource(Res.string.note_type_label),
        fieldMissingRequiredTemplate = stringResource(Res.string.field_missing_required),
        fieldStaleTemplate = stringResource(Res.string.field_stale),
    )
    return remember(fixed, stepBodies, stepActions, readinessLabels, roleLabels, sourceLabels) {
        fixed.toCopy(stepBodies, stepActions, readinessLabels, roleLabels, sourceLabels)
    }
}

/**
 * The count-bearing lines for [plan], resolved through the host's plural rules.
 *
 * The repaired line is resolved only when the plan will show it, so a step that
 * cannot tag does not pay for a string it will discard — and so a caller cannot
 * accidentally render it out of place.
 */
@Composable
fun rememberHomeCountedCopy(plan: OnboardingPlan): HomeCountedCopy {
    val syncedBody = if (plan.step == OnboardingStep.SYNCED) {
        pluralStringResource(
            Res.plurals.onboarding_synced_body,
            plan.importedKanji,
            plan.importedKanji,
        )
    } else {
        ""
    }
    val repairedTaggingLine = if (plan.showsRepairedTagging) {
        pluralStringResource(
            Res.plurals.onboarding_repaired_tagging,
            plan.repairedKanjiCount,
            plan.repairedKanjiCount,
        )
    } else {
        ""
    }
    return remember(syncedBody, repairedTaggingLine) {
        HomeCountedCopy(syncedBody = syncedBody, repairedTaggingLine = repairedTaggingLine)
    }
}

/**
 * The sync confirmation for [plan], with its repaired-tagging count already selected.
 *
 * Resolved by a composable and handed to the host because that is the only way round:
 * the dialog's wording lives in this module's resources and its count needs the host's
 * plural rules, so `ShellReducer` cannot build the effect and the host cannot invent the
 * words. Both hosts call this and pass the result to their sync driver, which is what
 * keeps one confirmation — and one stated count — behind both.
 */
@Composable
fun rememberSyncConfirmCopy(plan: OnboardingPlan, copy: HomeCopy): SyncConfirmCopy {
    val counted = rememberHomeCountedCopy(plan)
    return remember(plan.binding, counted.repairedTaggingLine, copy) {
        copy.syncConfirmation(plan.binding, counted.repairedTaggingLine)
    }
}

/** The picker row label for [option], with its field count pluralized. */
@Composable
fun rememberNoteTypeLabel(option: NoteTypeOption, copy: HomeCopy): String {
    val fieldCount = pluralStringResource(
        Res.plurals.note_type_field_count,
        option.fields.size,
        option.fields.size,
    )
    return remember(option, fieldCount, copy) { copy.noteTypeLabel(option, fieldCount) }
}

/** The repaired hand-off sentence for [count] tagged kanji. */
@Composable
fun rememberRepairedHandoffBody(count: Int): String =
    pluralStringResource(Res.plurals.repaired_handoff_body, count, count)

/**
 * The fixed strings, grouped so [rememberHomeCopy] can key one `remember` on them.
 *
 * Purely mechanical: `remember` with twenty-odd separate keys is unreadable and easy
 * to leave a key out of, which would silently pin stale copy across a locale change.
 */
@Suppress("LongParameterList")
private data class FixedHomeStrings(
    val providerStatusTitle: String,
    val noteTypeTitle: String,
    val noteTypeTooFewFields: String,
    val fieldUnmapped: String,
    val fieldRequired: String,
    val fieldOptional: String,
    val syncConfirmTitle: String,
    val syncConfirmAction: String,
    val syncCancel: String,
    val syncInProgressTitle: String,
    val syncCancelAction: String,
    val syncAlreadyRunning: String,
    val syncFailureFallback: String,
    val repairedHandoffTitle: String,
    val repairedHandoffQuery: String,
    val repairedHandoffCopy: String,
    val repairedHandoffCopied: String,
    val sourceNone: String,
    val sourceAndModel: String,
    val sourceQuery: String,
    val syncConfirmBodyTemplate: String,
    val recoverSyncBodyTemplate: String,
    val noteTypeLabelTemplate: String,
    val fieldMissingRequiredTemplate: String,
    val fieldStaleTemplate: String,
) {
    fun toCopy(
        stepBodies: Map<OnboardingStep, String>,
        stepActions: Map<OnboardingStep, String>,
        readinessLabels: Map<ProviderReadiness, String>,
        roleLabels: Map<FieldRole, String>,
        sourceLabels: Map<ImportSource, String>,
    ): HomeCopy = HomeCopy(
        providerStatusTitle = providerStatusTitle,
        noteTypeTitle = noteTypeTitle,
        noteTypeTooFewFields = noteTypeTooFewFields,
        fieldUnmapped = fieldUnmapped,
        fieldRequired = fieldRequired,
        fieldOptional = fieldOptional,
        syncConfirmTitle = syncConfirmTitle,
        syncConfirmAction = syncConfirmAction,
        syncCancel = syncCancel,
        syncInProgressTitle = syncInProgressTitle,
        syncCancelAction = syncCancelAction,
        syncAlreadyRunning = syncAlreadyRunning,
        syncFailureFallback = syncFailureFallback,
        repairedHandoffTitle = repairedHandoffTitle,
        repairedHandoffQuery = repairedHandoffQuery,
        repairedHandoffCopy = repairedHandoffCopy,
        repairedHandoffCopied = repairedHandoffCopied,
        sourceNone = sourceNone,
        sourceAndModel = sourceAndModel,
        sourceQuery = sourceQuery,
        syncConfirmBodyTemplate = syncConfirmBodyTemplate,
        recoverSyncBodyTemplate = recoverSyncBodyTemplate,
        noteTypeLabelTemplate = noteTypeLabelTemplate,
        fieldMissingRequiredTemplate = fieldMissingRequiredTemplate,
        fieldStaleTemplate = fieldStaleTemplate,
        stepBodies = stepBodies,
        stepActions = stepActions,
        readinessLabels = readinessLabels,
        roleLabels = roleLabels,
        sourceLabels = sourceLabels,
    )
}

/**
 * The fixed body for a step, or `null` for the three that assemble one.
 *
 * `READY_FIRST_SYNC`, `RECOVER_SYNC`, and `SYNCED` have no fixed sentence: they are
 * built from the note type, the provider's own error text, and a plural count
 * respectively. Returning `null` rather than a stand-in string means
 * `HomeCopy.stepBodies` has no entry to read by mistake — `getValue` would throw —
 * instead of silently rendering an unrelated sentence if one of those branches were
 * ever removed.
 */
private fun OnboardingStep.bodyResource(): StringResource? = when (this) {
    OnboardingStep.CONNECT_PROVIDER -> Res.string.onboarding_connect_body
    OnboardingStep.AUTHORIZE_PROVIDER -> Res.string.onboarding_authorize_body
    OnboardingStep.CHOOSE_SOURCE -> Res.string.onboarding_choose_source_body
    OnboardingStep.RECOVER_AUTHORIZATION -> Res.string.onboarding_recover_authorization_body
    OnboardingStep.READY_FIRST_SYNC,
    OnboardingStep.RECOVER_SYNC,
    OnboardingStep.SYNCED,
    -> null
}

private fun OnboardingStep.actionResource(): StringResource = when (this) {
    OnboardingStep.CONNECT_PROVIDER -> Res.string.onboarding_connect_action
    OnboardingStep.AUTHORIZE_PROVIDER -> Res.string.onboarding_authorize_action
    OnboardingStep.CHOOSE_SOURCE -> Res.string.onboarding_choose_source_action
    OnboardingStep.READY_FIRST_SYNC -> Res.string.onboarding_ready_action
    OnboardingStep.RECOVER_AUTHORIZATION -> Res.string.onboarding_recover_authorization_action
    OnboardingStep.RECOVER_SYNC -> Res.string.onboarding_recover_sync_action
    OnboardingStep.SYNCED -> Res.string.onboarding_synced_action
}

private fun ProviderReadiness.resource(): StringResource = when (this) {
    ProviderReadiness.ABSENT -> Res.string.provider_status_absent
    ProviderReadiness.UNAUTHORIZED -> Res.string.provider_status_unauthorized
    ProviderReadiness.READY -> Res.string.provider_status_ready
}

private fun FieldRole.resource(): StringResource = when (this) {
    FieldRole.EXPRESSION -> Res.string.field_role_expression
    FieldRole.READING -> Res.string.field_role_reading
    FieldRole.MEANING -> Res.string.field_role_meaning
    FieldRole.SENTENCE -> Res.string.field_role_sentence
    FieldRole.FREQUENCY -> Res.string.field_role_frequency
    FieldRole.FREQUENCY_SORT -> Res.string.field_role_frequency_sort
}

private fun ImportSource.resource(): StringResource = when (this) {
    ImportSource.ACTIVE_CARDS -> Res.string.import_source_active_cards
    ImportSource.SUSPENDED_CARDS -> Res.string.import_source_suspended_cards
    ImportSource.TAGGED_CARDS -> Res.string.import_source_tagged_cards
    ImportSource.WEAK_CARDS -> Res.string.import_source_weak_cards
    ImportSource.BROWSER_QUERY -> Res.string.import_source_browser_query
}
