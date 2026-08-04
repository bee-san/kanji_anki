package dev.bee.kanjianki.presentation

/**
 * A study action the user can ask for, named independently of the key that asks.
 *
 * Goal 195 mapped keys straight to actions inside `:feature-study`. That put the
 * decision in a Compose module, keyed off Compose's `Key`, and gave the mapping no
 * name a Settings editor or a native menu accelerator could refer to. This is the
 * same decision as portable data: a host translates its native key event into a
 * [StudyKeyPress], [StudyKeyboardPolicy] turns that into a command and then into the
 * *same* [KaniAction] the visible button dispatches, and the host never reaches the
 * scheduler or a repository itself.
 *
 * The set is deliberately small, and it is the set Kani genuinely has:
 *
 * - [PRIMARY] is the one safe default action of the visible card — reveal a
 *   self-graded front, submit a typed answer, or continue past applied feedback.
 * - [GRADE_PASS] and [GRADE_FAIL] submit the grades a card declares.
 * - [UNDO] requests the existing guarded review undo.
 *
 * There is no `GRADE_HARD` or `GRADE_EASY`. Kani's study UI offers Pass and Fail, and
 * the one `hard` it ever submits is the writing rung's "Save hard" — which is the
 * *pass* button under a different label, chosen by the ink evaluator and not by the
 * user. A command for a rating the user cannot choose would be a keyboard-only way to
 * grade differently from every visible control, which is exactly the divergence the
 * command model exists to prevent. This is why `2` and `4` are unbound by default:
 * see [StudyKeybindings.DEFAULT].
 *
 * Multiple-choice selection is not a command here; it is a property of the card. See
 * [StudyKeyboardPolicy.actionFor].
 */
enum class StudyCommand(val id: String) {
    /** The visible card's single default action: reveal, submit, or continue. */
    PRIMARY("primary"),

    /** Submit the pass grade the visible card declares. */
    GRADE_PASS("grade_pass"),

    /** Submit the fail grade the visible card declares. */
    GRADE_FAIL("grade_fail"),

    /** Reverse the last committed card, where the session reports one. */
    UNDO("undo"),
    ;

    companion object {
        /**
         * The command a stored id names, or null for an absent or unknown one.
         *
         * Null rather than a guess so a stored binding for a command this build no
         * longer has falls open to the reviewed defaults instead of resolving to some
         * neighbouring command — a remapping that silently became "fail" would grade
         * cards the user meant to reveal.
         */
        fun fromId(id: String?): StudyCommand? {
            val normalized = id?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.id == normalized }
        }
    }
}

/**
 * A key Kani can bind, as a portable name.
 *
 * Not Compose's `Key`: that is a platform keycode wearing a value class, so the same
 * physical key is a different `Long` on Android and on the desktop JVM. Bindings are
 * stored settings and asserted in tests, and both want a name that means the same
 * thing on every host. Hosts translate their native event into one of these, and a
 * key that is not in this set is simply not a study key.
 *
 * Number-row and numpad digits are separate entries with the same [digit], because
 * they are separate keys that must behave identically — matching on the digit rather
 * than the keycode is also what keeps the mapping independent of keyboard layout.
 */
enum class StudyKey(val token: String, val digit: Int? = null) {
    SPACE("space"),
    ENTER("enter"),
    NUMPAD_ENTER("numpad_enter"),

    DIGIT_1("1", digit = 1),
    DIGIT_2("2", digit = 2),
    DIGIT_3("3", digit = 3),
    DIGIT_4("4", digit = 4),
    DIGIT_5("5", digit = 5),
    DIGIT_6("6", digit = 6),
    DIGIT_7("7", digit = 7),
    DIGIT_8("8", digit = 8),
    DIGIT_9("9", digit = 9),

    NUMPAD_1("numpad_1", digit = 1),
    NUMPAD_2("numpad_2", digit = 2),
    NUMPAD_3("numpad_3", digit = 3),
    NUMPAD_4("numpad_4", digit = 4),
    NUMPAD_5("numpad_5", digit = 5),
    NUMPAD_6("numpad_6", digit = 6),
    NUMPAD_7("numpad_7", digit = 7),
    NUMPAD_8("numpad_8", digit = 8),
    NUMPAD_9("numpad_9", digit = 9),

    A("a"),
    B("b"),
    C("c"),
    D("d"),
    E("e"),
    F("f"),
    G("g"),
    H("h"),
    I("i"),
    J("j"),
    K("k"),
    L("l"),
    M("m"),
    N("n"),
    O("o"),
    P("p"),
    Q("q"),
    R("r"),
    S("s"),
    T("t"),
    U("u"),
    V("v"),
    W("w"),
    X("x"),
    Y("y"),
    Z("z"),
    ;

    /**
     * True when pressing this key unmodified would put a character into a text field.
     *
     * The dispatch-precedence rule in one property: a focused editor owns its own
     * printable input, so a study binding on a printable key must not fire while the
     * user is typing. Enter is not printable — it is a field's submit — which is why
     * a typed card can be submitted from the keyboard at all.
     */
    val isTextInput: Boolean
        get() = this != ENTER && this != NUMPAD_ENTER

    /**
     * True for the twenty-six letter keys.
     *
     * The editor offers a primary-modifier chord for these and not for the digits,
     * numpad keys, Space, or Enter: `Ctrl+3` and `Ctrl+Space` are chords no platform
     * documents and several IMEs claim, so listing them would offer bindings that may
     * never arrive. Derived from [digit] and the token length rather than a second hand-
     * kept list, so a new key cannot be added to one and forgotten in the other.
     */
    val isLetter: Boolean
        get() = digit == null && token.length == 1

    companion object {
        /** The key a stored token names, or null for an absent or unknown one. */
        fun fromToken(token: String?): StudyKey? {
            val normalized = token?.trim()?.lowercase() ?: return null
            return entries.firstOrNull { it.token == normalized }
        }
    }
}

/**
 * A key plus its modifiers, as one bindable value.
 *
 * [meta] is Command on macOS and the Windows/Super key elsewhere, which is why undo
 * is bound to both `Ctrl+Z` and `Meta+Z` by default rather than the model asking
 * which OS it is on: accepting both is correct on every host, and neither chord means
 * anything else in a study session.
 */
data class StudyKeystroke(
    val key: StudyKey,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
    val alt: Boolean = false,
    val meta: Boolean = false,
) {
    /** True when no modifier is held, which is the only form a bare key binding takes. */
    val isPlain: Boolean
        get() = !ctrl && !shift && !alt && !meta

    /**
     * True when a focused text field swallows this keystroke instead of Kani acting on it.
     *
     * The precedence rule as a property of the keystroke, so [StudyKeyboardPolicy.claims]
     * and anything that *advertises* a key answer it the same way. A control that printed
     * "Submit (Space)" beside the typed card's answer box would be naming a key that types
     * a space there — which is worse than advertising nothing.
     */
    val isClaimedByTextField: Boolean
        get() = isPlain && key.isTextInput
}

/**
 * One key event, reduced to what the policy needs.
 *
 * [isKeyDown] and [isRepeat] are both here because both produce the same duplicate:
 * a press and its release are one intent, and a held key that auto-repeats is one
 * intent too. Dropping them in the pure policy rather than in each host is what makes
 * "exactly once" checkable without a window.
 */
data class StudyKeyPress(
    val stroke: StudyKeystroke,
    val isKeyDown: Boolean = true,
    val isRepeat: Boolean = false,
)

/**
 * What else on screen has a claim on the keyboard.
 *
 * Everything here is a reason a study binding must *not* fire, and each is a way a
 * grade could otherwise be submitted by accident:
 *
 * - [textFieldFocused]: the typed card's answer box. Typing "possible" must not pass
 *   the card, and typing "3" must not fail it.
 * - [imeComposing]: a Japanese IME is mid-composition, so Enter and Space commit the
 *   candidate rather than the card. Nothing at all is claimed while composing.
 * - [modalActive]: a dialog or an open menu owns its own keys.
 * - [answerRevealed]: not a claim but the other half of the same guard — a self-graded
 *   card that is still face down cannot be graded, only revealed.
 */
data class StudyInputContext(
    val textFieldFocused: Boolean = false,
    val imeComposing: Boolean = false,
    val answerRevealed: Boolean = false,
    val modalActive: Boolean = false,
)

/**
 * Which keystrokes ask for which commands.
 *
 * A command may have several keystrokes ([StudyCommand.PRIMARY] has three); a
 * keystroke has exactly one command, which is what makes the map the right shape and
 * a conflict representable only as an overwrite. Insertion order is preserved so
 * [strokesFor] returns a stable list — a Settings row and a menu accelerator must not
 * name a different key on each launch.
 */
data class StudyKeybindings(val bindings: Map<StudyKeystroke, StudyCommand>) {
    /** The command a keystroke asks for, or null when it is not bound. */
    fun commandFor(stroke: StudyKeystroke): StudyCommand? = bindings[stroke]

    /** Every keystroke bound to a command, in binding order; empty when unbound. */
    fun strokesFor(command: StudyCommand): List<StudyKeystroke> =
        bindings.entries.filter { it.value == command }.map { it.key }

    companion object {
        /**
         * The reviewed defaults, Anki-compatible where Kani has the same action.
         *
         * `Space`/`Enter` for the primary action, `1` for Fail/Again and `3` for
         * Pass/Good, and `Ctrl+Z`/`Cmd+Z` for undo, all as Anki binds them. `2` and
         * `4` are deliberately absent: Anki's Hard and Easy have no user-selectable
         * equivalent here (see [StudyCommand]), and binding them to the nearest thing
         * would make the keyboard grade differently from the buttons. On a
         * multiple-choice card they are not idle — every digit selects the option in
         * that position, which is a property of the card rather than a binding.
         *
         * `P` and `F` are kept from Goal 195's shortcuts as second bindings for pass
         * and fail. They collide with nothing Anki reserves in the reviewer, and
         * dropping them would silently break the shortcuts already shipped.
         *
         * Numpad digits are bound alongside the number row, and `NumPadEnter`
         * alongside `Enter`, because a user on a numeric keypad is pressing the same
         * key as far as the intent goes.
         */
        val DEFAULT: StudyKeybindings = StudyKeybindings(
            linkedMapOf(
                StudyKeystroke(StudyKey.SPACE) to StudyCommand.PRIMARY,
                StudyKeystroke(StudyKey.ENTER) to StudyCommand.PRIMARY,
                StudyKeystroke(StudyKey.NUMPAD_ENTER) to StudyCommand.PRIMARY,
                StudyKeystroke(StudyKey.DIGIT_1) to StudyCommand.GRADE_FAIL,
                StudyKeystroke(StudyKey.NUMPAD_1) to StudyCommand.GRADE_FAIL,
                StudyKeystroke(StudyKey.F) to StudyCommand.GRADE_FAIL,
                StudyKeystroke(StudyKey.DIGIT_3) to StudyCommand.GRADE_PASS,
                StudyKeystroke(StudyKey.NUMPAD_3) to StudyCommand.GRADE_PASS,
                StudyKeystroke(StudyKey.P) to StudyCommand.GRADE_PASS,
                StudyKeystroke(StudyKey.Z, ctrl = true) to StudyCommand.UNDO,
                StudyKeystroke(StudyKey.Z, meta = true) to StudyCommand.UNDO,
            ),
        )
    }
}

/**
 * What a key press means in a study session, as a pure decision.
 *
 * The whole keyboard path, testable without a window: a host translates its native
 * event to a [StudyKeyPress], reports what else holds the keyboard as a
 * [StudyInputContext], and dispatches whatever [KaniAction] comes back. Every action
 * this can return is one a visible control also dispatches, which is the checkable
 * form of "the keyboard and the pointer do the same thing exactly once".
 *
 * The guards, not the mapping, are the substance:
 *
 * - **A card decides which grades exist.** Every rating returned is read off the
 *   visible [StudyCard], so the keyboard cannot submit a rating the card does not
 *   offer, and cannot expose one the writing contract forbids.
 * - **A face-down card can only be revealed.** Pass and Fail resolve to nothing until
 *   [StudyInputContext.answerRevealed], so no key grades an answer the user has not
 *   seen.
 * - **An editor, an IME, and a modal win.** See [StudyInputContext].
 * - **Duplicates are dropped.** Key-up and auto-repeat produce no action, so a held
 *   key cannot commit twice — and behind that, [StudySession.acceptsGrade] means a
 *   second grade on an answered card is dropped anyway, exactly as at the button.
 * - **Escape is never a study action.** It is unbindable in [StudyKeybindings.DEFAULT]
 *   and absent from [StudyKey] altogether: it is the shell's back, and a study
 *   shortcut that graded on Escape would turn "leave this card" into "fail this card".
 */
object StudyKeyboardPolicy {
    /**
     * The action a key press should dispatch, or null when the press is not ours.
     *
     * Multiple-choice selection is resolved before the bindings are consulted: on a
     * [StudyCard.Choice] an unmodified digit picks the option in that position, taking
     * its grade from the choice itself. It is ahead of the bindings because the digits
     * that grade a self-graded card mean something different here — `3` is the third
     * option, not Pass — and because picking *is* grading on a choice card, so there
     * is no pass or fail for those keys to mean instead.
     */
    fun actionFor(
        press: StudyKeyPress,
        session: StudySession,
        context: StudyInputContext = StudyInputContext(),
        bindings: StudyKeybindings = StudyKeybindings.DEFAULT,
    ): KaniAction? {
        if (!claims(press, context)) return null
        choiceAction(press.stroke, session)?.let { return it }
        val command = bindings.commandFor(press.stroke) ?: return null
        return actionFor(command, session, context)
    }

    /**
     * The command a key press asks for, before the session is consulted.
     *
     * Separate from [actionFor] so a menu or a Settings row can say which command a
     * key is for without a live session, and so the precedence rules are assertable on
     * their own.
     */
    fun commandFor(
        press: StudyKeyPress,
        context: StudyInputContext = StudyInputContext(),
        bindings: StudyKeybindings = StudyKeybindings.DEFAULT,
    ): StudyCommand? =
        if (claims(press, context)) bindings.commandFor(press.stroke) else null

    /**
     * Whether a study binding may act on this press at all.
     *
     * The precedence layer: duplicates, then the surfaces that own their own keys.
     * A modified chord is still claimed while a text field has focus, because
     * `Ctrl+Z` is not typing — dropping it there would take undo away from the one
     * card that most needs it.
     */
    fun claims(press: StudyKeyPress, context: StudyInputContext): Boolean {
        // One press is one intent: a release repeats it and a held key repeats it
        // again, and either would commit twice.
        if (!press.isKeyDown || press.isRepeat) return false
        if (context.modalActive || context.imeComposing) return false
        return !(context.textFieldFocused && press.stroke.isClaimedByTextField)
    }

    /**
     * The action a command means for the visible card, or null when the card does not
     * offer it.
     *
     * Null is the common answer and is never an error: a choice card has no pass key
     * because picking grades it, a writing card has no primary because there is
     * nothing to reveal, and an unrevealed flashcard has no grade because its answer
     * is still hidden.
     */
    fun actionFor(
        command: StudyCommand,
        session: StudySession,
        context: StudyInputContext = StudyInputContext(),
    ): KaniAction? = when (command) {
        StudyCommand.UNDO -> if (session.undoable) KaniAction.Study.Undo else null
        StudyCommand.PRIMARY -> primaryAction(session, context)
        StudyCommand.GRADE_PASS -> gradeAction(session, context) { card ->
            when (card) {
                // Save hard replaces Pass for a CLOSE ink attempt, so the keyboard
                // follows the visible primary rather than inventing a second one.
                is StudyCard.Writing -> (card.saveHard ?: card.pass).rating
                is StudyCard.Flashcard -> card.pass.rating
                is StudyCard.Choice, is StudyCard.Typed -> null
            }
        }
        StudyCommand.GRADE_FAIL -> gradeAction(session, context) { card ->
            when (card) {
                is StudyCard.Writing -> card.fail.rating
                is StudyCard.Flashcard -> card.fail.rating
                is StudyCard.Choice, is StudyCard.Typed -> null
            }
        }
    }

    private fun primaryAction(session: StudySession, context: StudyInputContext): KaniAction? {
        // Continue first and unconditionally: once a grade is applied it is the only
        // action the card offers, and it is what the visible primary button became.
        if (session.acceptsContinue) return KaniAction.Study.Continue
        if (!session.acceptsGrade) return null
        return when (val card = session.card) {
            // Face down, so the primary is the reveal; once revealed the primary is
            // Pass, which is what Anki's space does on the answer side.
            is StudyCard.Flashcard ->
                if (context.answerRevealed) card.pass.action else KaniAction.Study.Reveal
            // Submitting requires the field to hold what is being submitted. Enter
            // reaches here because it is not text input; Space never does.
            is StudyCard.Typed -> if (context.textFieldFocused) card.submit.action else null
            // Picking is grading, so there is no primary to invoke.
            is StudyCard.Choice -> null
            // No reveal, and no keyboard shortcut past the ink surface: a primary that
            // graded a writing card would be a way to pass without writing.
            is StudyCard.Writing -> null
            null -> null
        }
    }

    private inline fun gradeAction(
        session: StudySession,
        context: StudyInputContext,
        rating: (StudyCard) -> String?,
    ): KaniAction? {
        if (!session.acceptsGrade) return null
        val card = session.card ?: return null
        // A self-graded card must be turned over before it can be judged.
        if (card is StudyCard.Flashcard && !context.answerRevealed) return null
        val submitted = rating(card) ?: return null
        return KaniAction.Study.Grade(rating = submitted)
    }

    private fun choiceAction(stroke: StudyKeystroke, session: StudySession): KaniAction? {
        if (!stroke.isPlain || !session.acceptsGrade) return null
        val digit = stroke.key.digit ?: return null
        val card = session.card as? StudyCard.Choice ?: return null
        // Only the options actually on screen: a digit past the end of a three-choice
        // card is not a fourth answer, it is nothing.
        return card.choices.getOrNull(digit - 1)?.grade?.action
    }
}
