package dev.bee.kanjianki.presentation

/**
 * A field Kani needs, described by what it is for rather than what it is called.
 *
 * The user's note type names its fields whatever they like — `Expression`,
 * `Front`, `単語` — so every screen that reads a note has to go through a mapping.
 * These are the roles that mapping targets.
 */
enum class FieldRole {
    /** The word or kanji being studied. Without it there is nothing to show. */
    EXPRESSION,

    /** The reading, which the reading and sentence tasks test. */
    READING,

    /** The meaning, which recognition tests. */
    MEANING,

    /** A mined example sentence, used by the sentence task. */
    SENTENCE,

    /** A frequency figure, shown as context. */
    FREQUENCY,

    /** A sortable frequency, used for ordering rather than display. */
    FREQUENCY_SORT;

    /**
     * True when Kani cannot import a note without it.
     *
     * Only expression and meaning: those two are what a recognition card is made
     * of, and the starting rung for every new item is `kanji_meaning`. The rest gate
     * individual tasks — a note with no sentence field simply never reaches
     * `sentence_reading`, which is already how the `hasSentenceReading` predicate
     * behaves — so requiring them would reject collections Kani can study perfectly
     * well.
     */
    val isRequired: Boolean
        get() = this == EXPRESSION || this == MEANING
}

/**
 * A note type the user could pick, and the fields it actually has.
 *
 * [fields] is the collection's own field list, in its own order, because that is
 * what the picker offers and what the mapping must be validated against. Order is
 * preserved rather than sorted: it is the order the user sees in Anki, and
 * reordering it in Kani's picker would make the two disagree.
 */
data class NoteTypeOption(
    val name: String,
    val fields: List<String>,
) {
    init {
        require(name.isNotBlank()) { "a note type has a name" }
    }

    /** True when this note type could satisfy every required role at all. */
    val canSatisfyRequiredRoles: Boolean
        get() = fields.size >= FieldRole.entries.count { it.isRequired }
}

/**
 * Which of a note type's fields fills each role.
 *
 * A role absent from [assignments] is unmapped, which is a legitimate state for an
 * optional role and a blocking one for a required role — see [missingRequiredRoles].
 * Validation is not done in `init` on purpose: a half-finished mapping is exactly
 * what the configuration screen shows while the user is still working, and a type
 * that could not hold one would force the screen to keep its own shadow copy.
 */
data class FieldMapping(
    val assignments: Map<FieldRole, String> = emptyMap(),
) {
    /** The required roles with nothing assigned. Empty means importable. */
    val missingRequiredRoles: Set<FieldRole>
        get() = FieldRole.entries
            .filter { it.isRequired && assignments[it].isNullOrBlank() }
            .toSet()

    val isComplete: Boolean
        get() = missingRequiredRoles.isEmpty()

    fun field(role: FieldRole): String? = assignments[role]?.takeIf { it.isNotBlank() }

    fun with(role: FieldRole, field: String): FieldMapping =
        if (field.isBlank()) without(role) else copy(assignments = assignments + (role to field))

    fun without(role: FieldRole): FieldMapping = copy(assignments = assignments - role)

    /**
     * The roles whose assigned field does not exist on [option].
     *
     * The case this catches is a user renaming a field in Anki after configuring
     * Kani. The mapping still names the old field, so an import silently reads
     * nothing from it. Reporting it lets the screen say which role broke instead of
     * importing empty notes.
     */
    fun rolesNotIn(option: NoteTypeOption): Set<FieldRole> = assignments
        .filterValues { it !in option.fields }
        .keys
}
