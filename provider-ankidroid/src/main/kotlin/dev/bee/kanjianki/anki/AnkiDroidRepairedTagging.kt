package dev.bee.kanjianki.anki

import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import dev.bee.kanjianki.syncapi.RepairedTagSummary
import android.util.Log
import dev.bee.kanjianki.syncdomain.ProviderArchiveCleanupPolicy
import dev.bee.kanjianki.syncdomain.ProviderNotePolicy
import java.util.regex.Pattern

/** Per-note, failure-isolated `kani_repaired` tag writer. */
internal class AnkiDroidRepairedTagging(
    private val resolver: ContentResolver,
) {
    fun tagRepairedNotes(authority: String, requestedNoteIds: Set<Long>): RepairedTagSummary {
        val requested = requestedNoteIds.filterTo(sortedSetOf()) { it > 0L }
        if (requested.isEmpty()) return RepairedTagSummary.noOp()

        val tagged = linkedSetOf<Long>()
        val failed = linkedSetOf<Long>()
        for (noteId in requested) {
            val ok = try {
                tagNote(authority, noteId)
            } catch (error: RuntimeException) {
                Log.w(TAG, "Failed to tag repaired note $noteId.", error)
                false
            }
            if (ok) tagged += noteId else failed += noteId
        }
        return RepairedTagSummary(
            requested,
            tagged,
            failed,
            ProviderNotePolicy.repairedTagMessage(
                tagged.size,
                failed.size,
                ProviderArchiveCleanupPolicy.ANKIDROID_PROVIDER_NAME,
            ),
        )
    }

    private fun tagNote(authority: String, noteId: Long): Boolean {
        val noteUri = Uri.Builder()
            .scheme(CONTENT_SCHEME)
            .authority(authority)
            .appendPath(URI_SEGMENT_NOTES)
            .appendPath(noteId.toString())
            .build()
        val rawCursor = resolver.query(noteUri, arrayOf(COLUMN_TAGS), null, null, null)
            ?: return false
        val tags = rawCursor.use { cursor ->
            if (!cursor.moveToFirst()) return false
            value(cursor, COLUMN_TAGS)
        }
        var updatedTags = tags
        if (!ProviderNotePolicy.isRepairedTagPresent(splitTags(tags))) {
            updatedTags = "$tags ${ProviderNotePolicy.REPAIRED_TAG}".trim()
        }
        return resolver.update(
            noteUri,
            ContentValues().apply { put(COLUMN_TAGS, updatedTags) },
            null,
            null,
        ) > 0
    }

    private fun value(cursor: Cursor, column: String): String {
        val index = cursor.getColumnIndex(column)
        return if (index < 0 || cursor.isNull(index)) "" else cursor.getString(index)
    }

    private fun splitTags(value: String): List<String> = NOTES_WHITESPACE_SEPARATOR
        .split(value)
        .map(String::trim)
        .filter(String::isNotEmpty)

    companion object {
        private const val TAG = "AnkiRepairedTagging"
        private const val CONTENT_SCHEME = "content"
        private const val COLUMN_TAGS = "tags"
        private const val URI_SEGMENT_NOTES = "notes"
        private val NOTES_WHITESPACE_SEPARATOR: Pattern = Pattern.compile("\\s+")
    }
}
