package dev.bee.kanjianki.presentation

/**
 * The keyboard conventions of the host a binding is being shown on.
 *
 * Only for *labelling* and for the reserved-shortcut list. The bindings themselves are
 * platform-neutral, which is deliberate: [StudyKeybindings.DEFAULT] binds both `Ctrl+Z`
 * and `Meta+Z` so undo works everywhere without the model asking where it is running.
 * What does differ per host is what a user reads on a Settings row — `Ctrl+Z` on
 * Windows, `⌘Z` on macOS — and which chords the OS or the window manager has already
 * taken.
 */
enum class KeyboardPlatform {
    WINDOWS,
    MACOS,
    LINUX,
    ;

    /** True on the host where [StudyKeystroke.meta] is the Command key. */
    val metaIsCommand: Boolean
        get() = this == MACOS

    companion object {
        /**
         * The conventions for a JVM `os.name`, defaulting to [LINUX].
         *
         * By prefix and defaulting to Linux, matching `DesktopHostOs` — `os.name` is
         * free-form vendor text ("Mac OS X", "Windows 11", "FreeBSD"), and the Linux
         * default is the safe one here for the same reason it is there: Ctrl-primary
         * notation and a Super-chord reserved list are right on every platform except
         * macOS, and macOS names itself unambiguously. Android hosts pass nothing and
         * get the same, which is correct — Android has no OS chord set to avoid.
         */
        fun of(osName: String?): KeyboardPlatform {
            val name = osName.orEmpty().trim()
            return when {
                name.startsWith("Windows", ignoreCase = true) -> WINDOWS
                name.startsWith("Mac", ignoreCase = true) -> MACOS
                name.startsWith("Darwin", ignoreCase = true) -> MACOS
                else -> LINUX
            }
        }
    }
}

/**
 * A keystroke as the user reads it, in the host's own notation.
 *
 * macOS writes chords as glyphs with no separator and in a fixed
 * Control-Option-Shift-Command order, which is the order Apple's own menus use; the
 * other hosts spell the modifiers and join them with `+`. Both are what a user sees in
 * every other application on that platform, and a Settings editor that showed `Meta+Z`
 * to a Mac user would be describing a key that is not on the keyboard.
 */
fun StudyKeystroke.label(platform: KeyboardPlatform): String {
    val name = KEY_LABELS[key] ?: key.token.uppercase()
    if (platform.metaIsCommand) {
        return buildString {
            if (ctrl) append('⌃')
            if (alt) append('⌥')
            if (shift) append('⇧')
            if (meta) append('⌘')
            append(name)
        }
    }
    val parts = buildList {
        if (ctrl) add("Ctrl")
        if (alt) add("Alt")
        if (shift) add("Shift")
        // Windows and most Linux desktops call this the Super or Windows key; "Super"
        // is the name that is right on both and wrong on neither.
        if (meta) add("Super")
        add(name)
    }
    return parts.joinToString("+")
}

/**
 * Why a keystroke cannot be bound to the command the user picked.
 *
 * Both cases are refusals the editor shows rather than silently applying: an overwrite
 * would take a key away from another command without saying so, and a reserved chord
 * would produce a binding that never fires because the OS swallows the event first —
 * which reads to the user as Kani being broken.
 */
sealed interface StudyKeybindingIssue {
    /** [stroke] already asks for [command]. */
    data class Conflict(val stroke: StudyKeystroke, val command: StudyCommand) : StudyKeybindingIssue

    /** [stroke] belongs to the platform, described by [reservedFor]. */
    data class Reserved(val stroke: StudyKeystroke, val reservedFor: String) : StudyKeybindingIssue
}

/**
 * Validation and editing of a keybinding set, as pure functions over the map.
 *
 * The editor operations return a new [StudyKeybindings] rather than mutating one, so a
 * host can show a pending edit, validate it, and only then persist — and so every rule
 * here is assertable without a Settings screen.
 */
object StudyKeybindingEditor {
    /**
     * The reason [stroke] cannot become [command]'s binding, or null when it can.
     *
     * Re-binding a keystroke to the command it already has is not a conflict — it is a
     * no-op, and reporting it would make the editor refuse an edit that changes nothing.
     */
    fun issueFor(
        stroke: StudyKeystroke,
        command: StudyCommand,
        bindings: StudyKeybindings,
        platform: KeyboardPlatform,
    ): StudyKeybindingIssue? {
        reservedFor(stroke, platform)?.let { return StudyKeybindingIssue.Reserved(stroke, it) }
        val existing = bindings.commandFor(stroke)
        return if (existing != null && existing != command) {
            StudyKeybindingIssue.Conflict(stroke, existing)
        } else {
            null
        }
    }

    /**
     * [bindings] with [stroke] bound to [command], or null when [issueFor] refuses it.
     *
     * Null rather than a forced write, so the caller has to deal with the conflict. The
     * command's other keystrokes are kept: a command may have several, and remapping
     * `3` should not silently unbind `P`.
     */
    fun bind(
        stroke: StudyKeystroke,
        command: StudyCommand,
        bindings: StudyKeybindings,
        platform: KeyboardPlatform,
    ): StudyKeybindings? {
        if (issueFor(stroke, command, bindings, platform) != null) return null
        return StudyKeybindings(LinkedHashMap(bindings.bindings).apply { put(stroke, command) })
    }

    /**
     * [bindings] with [stroke] no longer bound, or the same set when it was not bound.
     *
     * A command may legitimately end up with no keystroke — a user who never wants to
     * grade by key is entitled to that, and every action stays reachable by pointer. It
     * is not a state the editor has to prevent, only one it has to show.
     */
    fun unbind(stroke: StudyKeystroke, bindings: StudyKeybindings): StudyKeybindings {
        if (!bindings.bindings.containsKey(stroke)) return bindings
        return StudyKeybindings(LinkedHashMap(bindings.bindings).apply { remove(stroke) })
    }

    /** The reviewed defaults, for the editor's reset control. */
    fun resetToDefaults(): StudyKeybindings = StudyKeybindings.DEFAULT

    /**
     * What the platform uses [stroke] for, or null when Kani may bind it.
     *
     * A curated list, not an OS query: no platform exposes "is this chord taken", and
     * the honest thing is a short list of chords that are genuinely reserved on the
     * named platform, kept narrow enough that it never refuses a chord the OS would
     * actually deliver. Unmodified keys are never listed — a bare letter or digit is
     * always the focused application's — and neither is any chord
     * [StudyKeybindings.DEFAULT] uses, which a test pins.
     */
    fun reservedFor(stroke: StudyKeystroke, platform: KeyboardPlatform): String? {
        if (stroke.isPlain) return null
        val primary = if (platform.metaIsCommand) stroke.meta else stroke.ctrl
        // Only the platform's own primary modifier, held alone, reaches a reserved
        // chord. Adding Shift or Alt to Ctrl+C makes a chord no OS claims.
        val onlyPrimary = primary && !stroke.shift && !stroke.alt &&
            (if (platform.metaIsCommand) !stroke.ctrl else !stroke.meta)
        if (onlyPrimary) {
            PRIMARY_RESERVED[stroke.key]?.let { return it }
        }
        // Windows and Linux hand Super chords to the shell before any application.
        if (!platform.metaIsCommand && stroke.meta && !stroke.ctrl) {
            SUPER_RESERVED[stroke.key]?.let { return it }
        }
        return null
    }
}

/**
 * Bindings as one device-local string, and back.
 *
 * Device-local rather than portable state: a Mac user's `⌘Z` must not arrive on their
 * Windows install as a Super chord, so a restore resets these to the reviewed defaults
 * (see `DeviceSettingKeys.portableExclusionStorageNames`). One string rather than a key
 * per command keeps the device-settings namespace stable while the command set grows.
 *
 * The format is `stroke>command`, entries joined by `;`, with modifiers written in a
 * fixed order — flat, human-readable in a diagnostics dump, and with no dependency on a
 * serialization library in a module that has none.
 */
object StudyKeybindingsCodec {
    /** [bindings] as a stored string, in binding order. */
    fun encode(bindings: StudyKeybindings): String =
        bindings.bindings.entries.joinToString(ENTRY_SEPARATOR) { (stroke, command) ->
            "${encodeStroke(stroke)}$PAIR_SEPARATOR${command.id}"
        }

    /**
     * The stored bindings, or [StudyKeybindings.DEFAULT] for absent or malformed state.
     *
     * Fails open as a whole rather than per entry, and that is the point: a
     * partially-applied map is the dangerous outcome. If the entry naming Pass is the
     * one that failed to parse and the entry naming Fail survived, the user is left
     * studying with a keyboard that can only fail cards. The reviewed defaults are
     * always a coherent set.
     */
    fun decode(stored: String?): StudyKeybindings = parse(stored) ?: StudyKeybindings.DEFAULT

    /**
     * The stored bindings, or null when the string is absent or malformed.
     *
     * For a caller that needs to tell "nothing stored" from "stored and unreadable" —
     * diagnostics, and the tests that pin the fail-open behavior. Everyone else wants
     * [decode].
     *
     * An empty string parses to an empty set, not null: a user who unbound every key
     * chose that, and resetting them to the defaults on the next launch would undo a
     * deliberate choice.
     */
    fun parse(stored: String?): StudyKeybindings? {
        val text = stored?.trim() ?: return null
        if (text.isEmpty()) return StudyKeybindings(emptyMap())
        val parsed = LinkedHashMap<StudyKeystroke, StudyCommand>()
        for (entry in text.split(ENTRY_SEPARATOR)) {
            val separator = entry.indexOf(PAIR_SEPARATOR)
            if (separator <= 0) return null
            val stroke = decodeStroke(entry.substring(0, separator)) ?: return null
            val command = StudyCommand.fromId(entry.substring(separator + 1)) ?: return null
            // A repeated keystroke is malformed, not a last-one-wins: the writer that
            // produced it disagreed with itself about what that key does.
            if (parsed.put(stroke, command) != null) return null
        }
        return StudyKeybindings(parsed)
    }

    private fun encodeStroke(stroke: StudyKeystroke): String = buildString {
        if (stroke.ctrl) append("ctrl$MODIFIER_SEPARATOR")
        if (stroke.alt) append("alt$MODIFIER_SEPARATOR")
        if (stroke.shift) append("shift$MODIFIER_SEPARATOR")
        if (stroke.meta) append("meta$MODIFIER_SEPARATOR")
        append(stroke.key.token)
    }

    private fun decodeStroke(text: String): StudyKeystroke? {
        val parts = text.trim().lowercase().split(MODIFIER_SEPARATOR)
        val key = StudyKey.fromToken(parts.last()) ?: return null
        var ctrl = false
        var alt = false
        var shift = false
        var meta = false
        for (modifier in parts.dropLast(1)) {
            when (modifier) {
                "ctrl" -> if (ctrl) return null else ctrl = true
                "alt" -> if (alt) return null else alt = true
                "shift" -> if (shift) return null else shift = true
                "meta" -> if (meta) return null else meta = true
                else -> return null
            }
        }
        return StudyKeystroke(key = key, ctrl = ctrl, shift = shift, alt = alt, meta = meta)
    }

    private const val ENTRY_SEPARATOR = ";"
    private const val PAIR_SEPARATOR = '>'
    private const val MODIFIER_SEPARATOR = "+"
}

/**
 * The user-facing name of a key, where its stored token is not it.
 *
 * The numpad keys and the two whitespace keys need spelling out; a letter or a digit is
 * its own uppercased token, which is why this map is only the exceptions.
 */
private val KEY_LABELS: Map<StudyKey, String> = buildMap {
    put(StudyKey.SPACE, "Space")
    put(StudyKey.ENTER, "Enter")
    put(StudyKey.NUMPAD_ENTER, "Numpad Enter")
    for (key in StudyKey.entries) {
        val digit = key.digit ?: continue
        if (key.token.startsWith("numpad_")) put(key, "Numpad $digit")
    }
}

/**
 * Chords the platform's primary modifier — Command on macOS, Control elsewhere — takes
 * before the focused application sees them.
 *
 * The clipboard and select-all chords are reserved on every platform Kani ships to, and
 * quit/close/minimize/hide are the window-management ones that reach the OS or the
 * toolkit rather than the app. `Z` is absent on purpose: `Ctrl+Z`/`⌘Z` is *undo*, which
 * is exactly what Kani binds it to.
 */
private val PRIMARY_RESERVED: Map<StudyKey, String> = mapOf(
    StudyKey.A to "Select all",
    StudyKey.C to "Copy",
    StudyKey.V to "Paste",
    StudyKey.X to "Cut",
    StudyKey.Q to "Quit",
    StudyKey.W to "Close window",
    StudyKey.M to "Minimize window",
    StudyKey.H to "Hide window",
)

/**
 * Chords the Windows and Linux shells take before any application.
 *
 * Narrow on purpose: these four are the ones a desktop environment reliably intercepts,
 * so a binding on them would appear to do nothing. `Z` is absent, because
 * [StudyKeybindings.DEFAULT] binds `Meta+Z` as the macOS-shaped undo and it is
 * harmless — and delivered — on the other hosts.
 */
private val SUPER_RESERVED: Map<StudyKey, String> = mapOf(
    StudyKey.L to "Lock screen",
    StudyKey.D to "Show desktop",
    StudyKey.E to "File manager",
    StudyKey.R to "Run dialog",
)
