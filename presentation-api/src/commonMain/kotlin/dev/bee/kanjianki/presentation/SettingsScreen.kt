package dev.bee.kanjianki.presentation

/**
 * The Settings surface, as portable data both hosts render.
 *
 * The Android host drove Settings from `MainActivitySettings` and ~40 panel files;
 * this is the shared shape, built up section by section. [SettingsScreen] is what a
 * host renders for one [SettingsSection]: the [root] category menu at
 * [SettingsSection.ROOT], and a section's controls otherwise. Settings validation,
 * capability gating, and persistence stay in `:core`/`:application`; a host maps its
 * snapshot into this and dispatches the toggles/edits as actions.
 *
 * A section not yet shared renders [SettingsSectionContent.Placeholder], which names
 * itself — honest about what is and is not ported yet — rather than a blank panel.
 */
data class SettingsScreen(
    val section: SettingsSection,
    val root: SettingsRoot? = null,
    val content: SettingsSectionContent = SettingsSectionContent.Placeholder,
)

/** The category menu shown at [SettingsSection.ROOT]. */
data class SettingsRoot(
    val title: String,
    val categories: List<SettingsCategory>,
)

/**
 * One category card that opens a section.
 *
 * [notices] are the truthful capability lines a category carries — "reminders only
 * fire while the app is open", "this AnkiConnect cannot accept notes" — so a platform
 * limitation is visible at the category, not hidden until the user opens it.
 */
data class SettingsCategory(
    val section: SettingsSection,
    val title: String,
    val summary: String,
    val notices: List<String> = emptyList(),
) {
    /** Opening the category navigates to its section. */
    val action: KaniAction
        get() = KaniAction.Navigation.Open(KaniDestination.Settings(section))
}

/**
 * A section's rendered controls.
 *
 * A sealed hierarchy grown one section at a time; [Placeholder] is the honest
 * not-yet-shared state that names the section rather than showing an empty panel.
 * Each concrete section is a flat list of [SettingsControl]s — the shared vocabulary
 * of toggles, sliders, choices, and read-only capability rows the panels reduce to.
 */
sealed interface SettingsSectionContent {
    data object Placeholder : SettingsSectionContent

    data class Controls(
        val title: String,
        val controls: List<SettingsControl>,
    ) : SettingsSectionContent
}

/**
 * One settings control, in the shared vocabulary the Android panels reduce to.
 *
 * Enough shapes for the section surfaces to render without each re-inventing a
 * toggle: a switch, a bounded number, a one-of-many choice, a plain action button,
 * and a read-only info row for a truthful capability statement. Each carries the
 * action it dispatches when changed; the info row carries none.
 */
sealed interface SettingsControl {
    val label: String

    data class Toggle(
        override val label: String,
        val checked: Boolean,
        val enabled: Boolean = true,
        val onChange: (Boolean) -> KaniAction,
    ) : SettingsControl

    data class Choice(
        override val label: String,
        val options: List<SettingsChoiceOption>,
        val selectedId: String,
    ) : SettingsControl

    /**
     * A bounded integer, decremented and incremented within [[min], [max]].
     *
     * The shape every "days", "passes", "cards per day", and "minutes" setting reduces
     * to. [onChange] takes the clamped next value, so the surface never has to know a
     * setting's bounds — it steps by [step] and hands the result back, and this decides
     * what that value means. The controls that would step past a bound are the surface's
     * to disable, from [value] against [min]/[max].
     */
    data class Stepper(
        override val label: String,
        val value: Int,
        val min: Int,
        val max: Int,
        val step: Int = 1,
        val unit: String = "",
        val onChange: (Int) -> KaniAction,
    ) : SettingsControl {
        init {
            require(min <= max) { "a stepper's min must not exceed its max" }
            require(step > 0) { "a stepper steps by a positive amount" }
        }

        /** The clamped value one [step] down, for the decrement control. */
        fun decremented(): Int = (value - step).coerceIn(min, max)

        /** The clamped value one [step] up, for the increment control. */
        fun incremented(): Int = (value + step).coerceIn(min, max)

        /** Whether the value can still go down; the surface disables the control otherwise. */
        val canDecrement: Boolean get() = value > min

        /** Whether the value can still go up; the surface disables the control otherwise. */
        val canIncrement: Boolean get() = value < max
    }

    data class ActionButton(
        override val label: String,
        val action: KaniAction,
        val destructive: Boolean = false,
        val enabled: Boolean = true,
    ) : SettingsControl

    /** A read-only capability or state line — no control, just truthful text. */
    data class Info(
        override val label: String,
        val value: String,
    ) : SettingsControl
}

data class SettingsChoiceOption(
    val id: String,
    val label: String,
    val action: KaniAction,
) {
    init {
        require(id.isNotBlank()) { "a choice option needs an id" }
    }
}
