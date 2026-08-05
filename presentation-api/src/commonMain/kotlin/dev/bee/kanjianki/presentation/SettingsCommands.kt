package dev.bee.kanjianki.presentation

/**
 * The settings commands that raise a host file picker rather than saving anything.
 *
 * Here rather than in the host mapper because [ShellReducer] is what turns them into a
 * [KaniEffect.PickFile], and the reducer is the reason they are shared at all: a backup
 * export is a host dialog, so a section cannot perform it and neither persistence mapper
 * should recognize it. Both mappers returning null for these ids is deliberate, and
 * [isPickerCommand] is the single answer to "was that null a picker or an unknown id".
 *
 * Structured like `StudyKeybindingCommands` and, like those ids, read fail-closed: an id
 * this build does not know raises no effect at all rather than a guessed one.
 */
object SettingsCommands {
    /** Export a fresh backup snapshot through the host's save dialog. */
    const val BACKUP_EXPORT: String = "automation.backup_export"

    /** Validate and stage a whole-file restore from the host's open dialog. */
    const val BACKUP_RESTORE: String = "automation.backup_restore"

    /** The file flow [id] asks for, or null when it asks for something else entirely. */
    fun filePurposeFor(id: String): KaniEffect.FilePurpose? = when (id.trim()) {
        BACKUP_EXPORT -> KaniEffect.FilePurpose.BACKUP_EXPORT
        BACKUP_RESTORE -> KaniEffect.FilePurpose.BACKUP_RESTORE
        else -> null
    }

    /** Whether [id] is a picker command, and so is not any mapper's to persist. */
    fun isPickerCommand(id: String): Boolean = filePurposeFor(id) != null
}
