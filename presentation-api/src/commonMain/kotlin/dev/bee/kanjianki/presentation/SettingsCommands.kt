package dev.bee.kanjianki.presentation

/**
 * The settings commands a host performs rather than persists.
 *
 * Here rather than in the host mapper because [ShellReducer] is what turns the file ones
 * into a [KaniEffect.PickFile], and the reducer is the reason they are shared at all: a
 * backup export is a host dialog, so a section cannot perform it and neither persistence
 * mapper should recognize it. Every id in this file is one both mappers deliberately
 * return null for, and [isHostCommand] is the single answer to "was that null a command
 * the host owns, or an unknown id" — a distinction the alternative loses, because falling
 * through to the collection mapper on an unrecognized id is how an `update.*` key would
 * end up written to the collection database.
 *
 * Structured like `StudyKeybindingCommands` and, like those ids, read fail-closed: an id
 * this build does not know raises no effect at all rather than a guessed one.
 */
object SettingsCommands {
    /** Export a fresh backup snapshot through the host's save dialog. */
    const val BACKUP_EXPORT: String = "automation.backup_export"

    /** Validate and stage a whole-file restore from the host's open dialog. */
    const val BACKUP_RESTORE: String = "automation.backup_restore"

    /** Check GitHub for a newer release now. */
    const val UPDATE_CHECK: String = "update.check_now"

    /**
     * Install the already-staged, verified artifact.
     *
     * The host confirms the exact version first — `DesktopUpdateHandoffPolicy` on desktop,
     * the package installer's own prompt on Android. Dispatching this id is asking to be
     * asked, not consenting.
     */
    const val UPDATE_INSTALL: String = "update.install_pending"

    /** Open the OS page that grants the install permission updates need. */
    const val UPDATE_PERMISSION: String = "update.open_install_permission"

    /** The one-tap path: enable automatic checks, then ask for the install permission. */
    const val UPDATE_BACKGROUND_SETUP: String = "update.background_setup"

    private val HOST_COMMANDS: Set<String> = setOf(
        BACKUP_EXPORT,
        BACKUP_RESTORE,
        UPDATE_CHECK,
        UPDATE_INSTALL,
        UPDATE_PERMISSION,
        UPDATE_BACKGROUND_SETUP,
    )

    /** The file flow [id] asks for, or null when it asks for something else entirely. */
    fun filePurposeFor(id: String): KaniEffect.FilePurpose? = when (id.trim()) {
        BACKUP_EXPORT -> KaniEffect.FilePurpose.BACKUP_EXPORT
        BACKUP_RESTORE -> KaniEffect.FilePurpose.BACKUP_RESTORE
        else -> null
    }

    /** Whether [id] is a picker command, and so is not any mapper's to persist. */
    fun isPickerCommand(id: String): Boolean = filePurposeFor(id) != null

    /** Whether [id] is any command the host performs, and so is not one to persist. */
    fun isHostCommand(id: String): Boolean = id.trim() in HOST_COMMANDS
}
