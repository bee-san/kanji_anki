package dev.bee.kanjianki.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.presentation.FieldMapping
import dev.bee.kanjianki.presentation.FieldRole
import dev.bee.kanjianki.presentation.NoteTypeOption
import dev.bee.kanjianki.ui.KaniTheme
import dev.bee.kanjianki.ui.KaniUiTokens

const val NOTE_TYPE_PICKER_TEST_TAG: String = "kani-note-type-picker"
const val FIELD_MAPPING_TEST_TAG: String = "kani-field-mapping"
const val FIELD_PROBLEM_TEST_TAG: String = "kani-field-problem"

/** One row per selectable note type, tagged as `kani-note-type-<name>`. */
fun noteTypeRowTestTag(name: String): String = "kani-note-type-$name"

/** One row per field role, tagged as `kani-field-<role>`. */
fun fieldRowTestTag(role: FieldRole): String = "kani-field-${role.name.lowercase()}"

/**
 * The note type picker.
 *
 * Offers every note type the collection has, including ones with too few fields for
 * Kani to import from — with those marked rather than hidden. A user looking for a
 * note type that is not in the list has no way to tell whether Kani could not see it
 * or would not offer it, and "could not see it" is the far more alarming reading.
 */
@Composable
fun NoteTypePicker(
    options: List<NoteTypeOption>,
    selected: String,
    copy: HomeCopy,
    onSelect: (NoteTypeOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(NOTE_TYPE_PICKER_TEST_TAG)
            // A selectable group so a screen reader announces "2 of 7" rather than
            // reading seven unrelated buttons.
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = copy.noteTypeTitle,
            color = KaniTheme.colors.muted,
            fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
        )
        for (option in options) {
            NoteTypeRow(
                option = option,
                selected = option.name == selected,
                copy = copy,
                onSelect = onSelect,
            )
        }
    }
}

@Composable
private fun NoteTypeRow(
    option: NoteTypeOption,
    selected: Boolean,
    copy: HomeCopy,
    onSelect: (NoteTypeOption) -> Unit,
) {
    val label = rememberNoteTypeLabel(option, copy)
    val usable = option.canSatisfyRequiredRoles
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(noteTypeRowTestTag(option.name))
            // The whole row selects, not a button inside it. A `Role.RadioButton`
            // announcement promises a target the size of the row, and a nested
            // button would leave most of that row inert — a tap next to the name
            // doing nothing, with nothing on screen to say why.
            .selectable(
                selected = selected,
                // Selecting an unusable note type is refused rather than allowed and
                // then rejected at sync: the field mapping below would have nothing
                // to offer, so there is no state worth entering.
                enabled = usable,
                role = Role.RadioButton,
                onClick = { onSelect(option) },
            )
            .semantics {
                contentDescription = if (usable) label else "$label. ${copy.noteTypeTooFewFields}"
            },
        shape = KaniUiTokens.LeafShape,
        color = if (selected) KaniTheme.colors.secondaryFill else KaniTheme.colors.panelSoft,
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = label,
                color = if (usable) KaniTheme.colors.ink else KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            )
            if (!usable) {
                Text(
                    text = copy.noteTypeTooFewFields,
                    color = KaniTheme.colors.coral,
                    fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
                )
            }
        }
    }
}

/**
 * The field mapping: which of the note type's fields fills each role Kani needs.
 *
 * Every role is shown, required and optional alike, with its requirement stated. A
 * screen that showed only the required two would leave a user wondering why the
 * sentence task never appears — the answer being an unmapped optional field they
 * were never shown.
 */
@Composable
fun FieldMappingList(
    option: NoteTypeOption,
    mapping: FieldMapping,
    copy: HomeCopy,
    modifier: Modifier = Modifier,
) {
    val stale = mapping.rolesNotIn(option)
    val missing = mapping.missingRequiredRoles
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(FIELD_MAPPING_TEST_TAG),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (role in FieldRole.entries) {
            FieldRow(
                role = role,
                field = mapping.field(role),
                copy = copy,
                problem = when (role) {
                    in missing -> copy.missingRequiredField(role)
                    in stale -> copy.staleField(role)
                    else -> null
                },
            )
        }
    }
}

@Composable
private fun FieldRow(
    role: FieldRole,
    field: String?,
    copy: HomeCopy,
    problem: String?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(fieldRowTestTag(role)),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = copy.roleLabel(role),
                color = KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
            )
            Text(
                text = copy.roleRequirement(role),
                color = KaniTheme.colors.muted,
                fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            )
            Text(
                // An unmapped role says so rather than rendering an empty gap the
                // user cannot distinguish from a field whose name did not load.
                text = field ?: copy.fieldUnmapped,
                color = if (field == null) KaniTheme.colors.muted else KaniTheme.colors.ink,
                fontSize = KaniUiTokens.StudyBodyTextSizeSp.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        problem?.let {
            Text(
                text = it,
                modifier = Modifier.testTag(FIELD_PROBLEM_TEST_TAG),
                color = KaniTheme.colors.coral,
                fontSize = KaniUiTokens.StudyCaptionTextSizeSp.sp,
            )
        }
    }
}
