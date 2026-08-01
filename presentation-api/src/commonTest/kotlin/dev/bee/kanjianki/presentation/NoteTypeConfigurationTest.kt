package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NoteTypeConfigurationTest {
    @Test
    fun onlyExpressionAndMeaningBlockAnImport() {
        // The other four roles gate individual tasks, exactly as the
        // `hasSentenceReading` / `hasKanjiReading` predicates already do. Requiring
        // them would reject collections Kani can study from the starting rung.
        assertEquals(
            setOf(FieldRole.EXPRESSION, FieldRole.MEANING),
            FieldRole.entries.filter { it.isRequired }.toSet(),
        )
    }

    @Test
    fun anEmptyMappingNamesBothMissingRolesRatherThanJustFailing() {
        val mapping = FieldMapping()

        assertEquals(
            setOf(FieldRole.EXPRESSION, FieldRole.MEANING),
            mapping.missingRequiredRoles,
        )
        assertFalse(mapping.isComplete)
    }

    @Test
    fun mappingBothRequiredRolesIsEnoughEvenWithNoOptionalFields() {
        // A minimal two-field note type is a real shape: a Core deck with just a
        // front and a back. It studies fine, it just never reaches the reading or
        // sentence tasks.
        val mapping = FieldMapping()
            .with(FieldRole.EXPRESSION, "Front")
            .with(FieldRole.MEANING, "Back")

        assertTrue(mapping.isComplete)
        assertTrue(mapping.missingRequiredRoles.isEmpty())
        assertNull(mapping.field(FieldRole.SENTENCE))
    }

    @Test
    fun theKikuDefaultMappingIsComplete() {
        val mapping = kikuMapping

        assertTrue(mapping.isComplete)
        assertEquals("Expression", mapping.field(FieldRole.EXPRESSION))
        assertEquals("ExpressionReading", mapping.field(FieldRole.READING))
        assertEquals("MainDefinition", mapping.field(FieldRole.MEANING))
        assertEquals("Sentence", mapping.field(FieldRole.SENTENCE))
        assertEquals("Frequency", mapping.field(FieldRole.FREQUENCY))
        assertEquals("FreqSort", mapping.field(FieldRole.FREQUENCY_SORT))
    }

    @Test
    fun assigningABlankFieldClearsTheRoleInsteadOfStoringEmptiness()
    {
        // A cleared dropdown and a dropdown holding "" must not be different
        // states: only one of them would be reported by `missingRequiredRoles`, and
        // the other would import a note whose meaning is the empty string.
        val mapping = kikuMapping.with(FieldRole.MEANING, "   ")

        assertNull(mapping.field(FieldRole.MEANING))
        assertEquals(setOf(FieldRole.MEANING), mapping.missingRequiredRoles)
        assertFalse(FieldRole.MEANING in mapping.assignments)
    }

    @Test
    fun clearingAnOptionalRoleLeavesTheMappingImportable() {
        val mapping = kikuMapping.without(FieldRole.SENTENCE)

        assertTrue(mapping.isComplete)
        assertNull(mapping.field(FieldRole.SENTENCE))
    }

    @Test
    fun reassigningARoleReplacesItRatherThanAccumulating() {
        val mapping = kikuMapping.with(FieldRole.MEANING, "Back")

        assertEquals("Back", mapping.field(FieldRole.MEANING))
        assertEquals(FieldRole.entries.size, mapping.assignments.size)
    }

    @Test
    fun aFieldRenamedInAnkiIsReportedPerRoleAndNotSilentlyImportedEmpty() {
        // The case this exists for: the user renames `MainDefinition` to `Meaning`
        // in Anki. Kani's stored mapping still names the old field, and an import
        // that just read nothing from it would produce notes with no meaning.
        val renamed = NoteTypeOption(
            name = "Kiku",
            fields = listOf(
                "Expression",
                "ExpressionReading",
                "Meaning",
                "Sentence",
                "Frequency",
                "FreqSort",
            ),
        )

        assertEquals(setOf(FieldRole.MEANING), kikuMapping.rolesNotIn(renamed))
    }

    @Test
    fun aMappingThatMatchesTheNoteTypeReportsNothingStale() {
        assertTrue(kikuMapping.rolesNotIn(kikuOption).isEmpty())
    }

    @Test
    fun staleAndMissingAreSeparateProblemsBecauseTheRemediesDiffer() {
        // A stale role has a value that no longer resolves and needs re-picking; a
        // missing role was never picked. Collapsing them would let a complete-looking
        // mapping import empty fields.
        val stale = FieldMapping()
            .with(FieldRole.EXPRESSION, "Expression")
            .with(FieldRole.MEANING, "GoneAway")

        assertTrue(stale.isComplete)
        assertEquals(setOf(FieldRole.MEANING), stale.rolesNotIn(kikuOption))
    }

    @Test
    fun aNoteTypeKeepsAnkisOwnFieldOrder() {
        // The picker offers them in this order, and so does Anki. Sorting here would
        // make the two disagree about a list the user already knows.
        assertEquals(
            listOf(
                "Expression",
                "ExpressionReading",
                "MainDefinition",
                "Sentence",
                "Frequency",
                "FreqSort",
            ),
            kikuOption.fields,
        )
    }

    @Test
    fun aTwoFieldNoteTypeCouldStillSatisfyEveryRequiredRole() {
        assertTrue(
            NoteTypeOption(name = "Basic", fields = listOf("Front", "Back"))
                .canSatisfyRequiredRoles,
        )
    }

    @Test
    fun aSingleFieldNoteTypeCannotAndIsWorthSayingSoBeforeTheUserTries() {
        assertFalse(
            NoteTypeOption(name = "Cloze", fields = listOf("Text"))
                .canSatisfyRequiredRoles,
        )
    }

    @Test
    fun aNoteTypeWithNoFieldsIsRejectedRatherThanOfferedAsAChoice() {
        assertFalse(NoteTypeOption(name = "Empty", fields = emptyList()).canSatisfyRequiredRoles)
    }

    @Test
    fun aNamelessNoteTypeIsRejectedAtConstruction() {
        // A blank name cannot be selected in a picker or matched against a
        // collection, so it is a bug in whatever produced it.
        assertFailsWith<IllegalArgumentException> {
            NoteTypeOption(name = "  ", fields = listOf("Front", "Back"))
        }
    }

    @Test
    fun everyRoleCanBeAssignedAndClearedIndependently() {
        // A role that cannot be cleared traps a mistake; a role that cannot be
        // assigned is dead.
        for (role in FieldRole.entries) {
            val assigned = FieldMapping().with(role, "Front")
            assertEquals("Front", assigned.field(role), role.name)
            assertNull(assigned.without(role).field(role), role.name)
        }
    }

    private val kikuOption = NoteTypeOption(
        name = "Kiku",
        fields = listOf(
            "Expression",
            "ExpressionReading",
            "MainDefinition",
            "Sentence",
            "Frequency",
            "FreqSort",
        ),
    )

    /** The shipped Kiku defaults, matching `Settings.kikuDefaults()`. */
    private val kikuMapping = FieldMapping(
        assignments = mapOf(
            FieldRole.EXPRESSION to "Expression",
            FieldRole.READING to "ExpressionReading",
            FieldRole.MEANING to "MainDefinition",
            FieldRole.SENTENCE to "Sentence",
            FieldRole.FREQUENCY to "Frequency",
            FieldRole.FREQUENCY_SORT to "FreqSort",
        ),
    )
}
