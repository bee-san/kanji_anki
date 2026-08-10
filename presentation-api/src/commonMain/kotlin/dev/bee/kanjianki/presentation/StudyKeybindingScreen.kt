package dev.bee.kanjianki.presentation

/**
 * The keybinding editor as portable data, built from a binding set.
 *
 * One row per [StudyCommand] — always every command, in the command's own declaration
 * order, so a command with no key is a visible empty row rather than a missing one. The
 * rows carry labels already in the host platform's notation, and each candidate
 * keystroke carries the action that binds it plus the reason it cannot be bound, so a
 * host renders and validates without re-deriving either.
 *
 * Built here rather than in a host mapper because both hosts want the same editor and
 * because every rule worth checking — what conflicts, what the platform reserves, what
 * a reset restores — is then assertable without a Settings screen.
 */
data class StudyKeybindingScreen(
    val platform: KeyboardPlatform,
    val rows: List<StudyKeybindingRow>,
) {
    /** The row for a command; every command always has one. */
    fun row(command: StudyCommand): StudyKeybindingRow = rows.first { it.command == command }

    companion object {
        /**
         * The editor for [bindings] as read on [platform].
         *
         * [candidates] is the set a host offers for remapping, and defaults to
         * [defaultCandidates] for [platform].
         */
        fun of(
            bindings: StudyKeybindings,
            platform: KeyboardPlatform,
            candidates: List<StudyKeystroke> = defaultCandidates(platform),
        ): StudyKeybindingScreen = StudyKeybindingScreen(
            platform = platform,
            rows = StudyCommand.entries.map { command ->
                StudyKeybindingRow(
                    command = command,
                    platform = platform,
                    bound = bindings.strokesFor(command).map { stroke ->
                        BoundKeystroke(stroke = stroke, label = stroke.label(platform))
                    },
                    candidates = candidates.map { stroke ->
                        KeystrokeCandidate(
                            stroke = stroke,
                            label = stroke.label(platform),
                            issue = StudyKeybindingEditor.issueFor(stroke, command, bindings, platform),
                        )
                    },
                )
            },
        )

        /**
         * Every keystroke a host may offer for remapping on [platform].
         *
         * Bare keys, plus one chord per letter using the platform's own primary modifier
         * — `Ctrl+` on Windows and Linux, `⌘` on macOS. Deliberately not every modifier
         * combination: the product would be thousands of rows, almost all of them chords
         * no user wants.
         *
         * The letter chords are also what make the reserved-shortcut rule visible rather
         * than theoretical. With only bare keys and the two undo chords on offer, every
         * chord the editor lists is one Kani already owns — `Ctrl+C` never appears, so
         * "Used by the system: Copy" can never be shown, and the validation exists with
         * nothing able to reach it.
         *
         * Only the platform's own primary modifier, because the other one is not a chord
         * the user can type on this machine: offering `⌘Z` on Windows would name a key
         * that is not on the keyboard, which is the same mistake reading the platform from
         * the running JVM exists to prevent.
         */
        fun defaultCandidates(platform: KeyboardPlatform): List<StudyKeystroke> {
            val primaryChord: (StudyKey) -> StudyKeystroke = if (platform.metaIsCommand) {
                { key -> StudyKeystroke(key, meta = true) }
            } else {
                { key -> StudyKeystroke(key, ctrl = true) }
            }
            return StudyKey.entries.map { StudyKeystroke(it) } +
                StudyKey.entries.filter { it.isLetter }.map(primaryChord)
        }
    }
}

/**
 * One command's row: what it is bound to now, and what it could be bound to.
 *
 * [bound] is in binding order and may be empty — a command reachable only by pointer is
 * a state the editor shows rather than prevents, because every action stays available at
 * a visible control.
 */
data class StudyKeybindingRow(
    val command: StudyCommand,
    val platform: KeyboardPlatform,
    val bound: List<BoundKeystroke>,
    val candidates: List<KeystrokeCandidate>,
) {
    /** The bound keystrokes as one readable accelerator line; empty when unbound. */
    val acceleratorLabel: String
        get() = bound.joinToString(ACCELERATOR_SEPARATOR) { it.label }

    /**
     * The single accelerator to advertise in a native menu, or null when unbound.
     *
     * The first binding, because [StudyKeybindings] preserves insertion order and the
     * reviewed defaults list the Anki-canonical key first. A menu item takes one
     * accelerator, so this is the one to show; the editor row shows them all.
     */
    val menuAccelerator: String?
        get() = bound.firstOrNull()?.label

    /** The candidates a host may offer without a refusal, in candidate order. */
    val bindable: List<KeystrokeCandidate>
        get() = candidates.filter { it.issue == null }
}

/** A keystroke currently bound to the row's command, with its platform label. */
data class BoundKeystroke(val stroke: StudyKeystroke, val label: String) {
    /** Removing this binding from the row's command. */
    val unbindAction: KaniAction
        get() = KaniAction.Settings.Command(StudyKeybindingCommands.unbindCommandId(stroke))
}

/**
 * A keystroke a host may offer for the row's command.
 *
 * [issue] is the reason it cannot be chosen — already another command's, or the
 * platform's own — and null when it can. A host may render an unavailable candidate
 * disabled with its reason rather than hiding it, which is what tells a user why `⌘Q` is
 * not on offer.
 */
data class KeystrokeCandidate(
    val stroke: StudyKeystroke,
    val label: String,
    val issue: StudyKeybindingIssue? = null,
) {
    /** Binding this keystroke to [command]. */
    fun bindAction(command: StudyCommand): KaniAction =
        KaniAction.Settings.Command(StudyKeybindingCommands.bindCommandId(command, stroke))
}

/**
 * The settings-command ids the keybinding editor dispatches, and their parsing.
 *
 * The editor's edits are [KaniAction.Settings.Command]s rather than new action types,
 * because the shared vocabulary already carries "a named settings command the host
 * resolves" and a keybinding edit is exactly that. The ids are structured — `bind`,
 * `unbind`, `reset` — so a host maps them back without a second parallel channel.
 *
 * Parsing is fail-closed: an id this build cannot read resolves to null, and the host
 * ignores it rather than guessing at an edit. A misread keybinding edit would silently
 * rebind a grade key.
 */
object StudyKeybindingCommands {
    /** The id that restores [StudyKeybindings.DEFAULT]. */
    const val RESET: String = "study_keybindings.reset"

    /** The edit an id names, or null when this build cannot read it. */
    fun parse(id: String): StudyKeybindingEdit? {
        val trimmed = id.trim()
        if (trimmed == RESET) return StudyKeybindingEdit.Reset
        val body = trimmed.removePrefix(PREFIX)
        if (body == trimmed) return null
        val parts = body.split(FIELD_SEPARATOR)
        return when (parts.firstOrNull()) {
            BIND -> {
                if (parts.size != 3) return null
                val command = StudyCommand.fromId(parts[1]) ?: return null
                val stroke = parseStroke(parts[2]) ?: return null
                StudyKeybindingEdit.Bind(stroke = stroke, command = command)
            }
            UNBIND -> {
                if (parts.size != 2) return null
                StudyKeybindingEdit.Unbind(parseStroke(parts[1]) ?: return null)
            }
            else -> null
        }
    }

    /**
     * [bindings] with [edit] applied, or null when the edit is refused or changes nothing.
     *
     * Null covers both "the platform or another command holds that key" and "this was
     * already the state", so a host persists only on a real change and never writes a
     * set the editor would have refused.
     */
    fun apply(
        edit: StudyKeybindingEdit,
        bindings: StudyKeybindings,
        platform: KeyboardPlatform,
    ): StudyKeybindings? = when (edit) {
        is StudyKeybindingEdit.Bind ->
            StudyKeybindingEditor.bind(edit.stroke, edit.command, bindings, platform)
                ?.takeIf { it.bindings != bindings.bindings }
        is StudyKeybindingEdit.Unbind ->
            StudyKeybindingEditor.unbind(edit.stroke, bindings)
                .takeIf { it.bindings != bindings.bindings }
        StudyKeybindingEdit.Reset ->
            StudyKeybindingEditor.resetToDefaults()
                .takeIf { it.bindings != bindings.bindings }
    }

    internal fun bindCommandId(command: StudyCommand, stroke: StudyKeystroke): String =
        "$PREFIX$BIND$FIELD_SEPARATOR${command.id}$FIELD_SEPARATOR${strokeId(stroke)}"

    internal fun unbindCommandId(stroke: StudyKeystroke): String =
        "$PREFIX$UNBIND$FIELD_SEPARATOR${strokeId(stroke)}"

    private fun strokeId(stroke: StudyKeystroke): String =
        StudyKeybindingsCodec.encode(StudyKeybindings(mapOf(stroke to StudyCommand.PRIMARY)))
            .substringBefore('>')

    private fun parseStroke(text: String): StudyKeystroke? =
        StudyKeybindingsCodec.parse("$text>${StudyCommand.PRIMARY.id}")
            ?.bindings?.keys?.firstOrNull()

    private const val PREFIX = "study_keybindings."
    private const val BIND = "bind"
    private const val UNBIND = "unbind"
    private const val FIELD_SEPARATOR = ":"
}

/** One edit the keybinding editor can ask for. */
sealed interface StudyKeybindingEdit {
    data class Bind(val stroke: StudyKeystroke, val command: StudyCommand) : StudyKeybindingEdit

    data class Unbind(val stroke: StudyKeystroke) : StudyKeybindingEdit

    data object Reset : StudyKeybindingEdit
}

private const val ACCELERATOR_SEPARATOR = ", "
