package dev.bee.kanjianki.presentation

/**
 * The key to advertise beside each visible study control, for the running platform.
 *
 * The keyboard already works and the native menu already prints its accelerators, but a
 * menu is not where a screen reader user is: the control itself has to say which key
 * invokes it, or the shortcut exists only for people who can see a menu bar. This is that
 * announcement as portable data — one accelerator label per command, in the host's own
 * notation — so a surface adds action semantics without deriving keys itself.
 *
 * Two rules make it more than a lookup:
 *
 * - **A key a focused text field would swallow is not advertised.** On the typed card the
 *   answer box owns Space, so the primary action announces `Enter` instead — the key that
 *   actually submits there. Announcing Space would name a key that types a space.
 * - **A host with no keyboard supplies none of this.** The hints are opt-in: a surface
 *   takes them as nullable and announces nothing when absent, which is why a phone's Pass
 *   button does not tell TalkBack to press `3`. Only a host that actually routes key
 *   events builds them.
 *
 * Built from the same [StudyKeybindings] the keyboard reads, so a remap moves the
 * announcement with it and the two cannot disagree.
 */
data class StudyActionHints(
    val platform: KeyboardPlatform,
    private val accelerators: Map<StudyCommand, String>,
) {
    /** The key to announce for a command, or null when it has none worth naming here. */
    fun accelerator(command: StudyCommand): String? = accelerators[command]

    /**
     * The key that picks the choice in a 1-based [position], or null past the ninth.
     *
     * Positional rather than bound: on a multiple-choice card every digit selects the
     * option in that place, which [StudyKeyboardPolicy.actionFor] resolves ahead of the
     * bindings because picking *is* grading there. So this is not read from
     * [StudyKeybindings] — there is nothing in it to read — and a remap of `3` does not
     * move it. Nothing past the ninth, because there is no tenth digit key.
     */
    fun choiceAccelerator(position: Int): String? =
        CHOICE_KEYS.getOrNull(position - 1)?.let { StudyKeystroke(it).label(platform) }

    companion object {
        /**
         * The hints for [bindings] as read on [platform], given what holds the keyboard.
         *
         * The first binding that [context] does not claim, because [StudyKeybindings]
         * preserves insertion order and the reviewed defaults list the Anki-canonical key
         * first. A command whose every key is claimed announces nothing rather than a key
         * that would not fire.
         */
        fun of(
            platform: KeyboardPlatform,
            bindings: StudyKeybindings = StudyKeybindings.DEFAULT,
            context: StudyInputContext = StudyInputContext(),
        ): StudyActionHints = StudyActionHints(
            platform = platform,
            accelerators = StudyCommand.entries.mapNotNull { command ->
                bindings.strokesFor(command)
                    .firstOrNull { !(context.textFieldFocused && it.isClaimedByTextField) }
                    ?.let { command to it.label(platform) }
            }.toMap(),
        )
    }
}

/**
 * The digit keys that select choices, in position order.
 *
 * The number row only. The numpad digits select too, but a control announces one key and
 * the number row is the one every keyboard has.
 */
private val CHOICE_KEYS: List<StudyKey> = listOf(
    StudyKey.DIGIT_1,
    StudyKey.DIGIT_2,
    StudyKey.DIGIT_3,
    StudyKey.DIGIT_4,
    StudyKey.DIGIT_5,
    StudyKey.DIGIT_6,
    StudyKey.DIGIT_7,
    StudyKey.DIGIT_8,
    StudyKey.DIGIT_9,
)
