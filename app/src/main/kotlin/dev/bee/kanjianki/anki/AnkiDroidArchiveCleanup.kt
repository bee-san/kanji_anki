package dev.bee.kanjianki.anki

import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncdomain.ProviderArchiveCleanupPolicy
import dev.bee.kanjianki.syncdomain.ProviderNotePolicy
import dev.bee.kanjianki.syncdomain.SyncMirrorPolicy
import java.util.regex.Pattern

internal class AnkiDroidArchiveCleanup(
    private val resolver: ContentResolver,
) {
    fun removeArchivedSuspendedCards(
        authority: String,
        snapshot: RecordsSyncModels.CollectionSnapshot,
        selectedSuspendedImports: List<RecordsImportModels.SuspendedImport>?,
    ): AnkiDroidGateway.RemovalSummary {
        val cleanup = ProviderArchiveCleanupPolicy.plan(
            archiveCleanupCards(snapshot.cards),
            selectedSuspendedCardIds(selectedSuspendedImports),
        )
        if (!cleanup.hasSuspendedCards()) {
            return AnkiDroidGateway.RemovalSummary(0, 0, 0, "No suspended cards needed provider cleanup.")
        }

        var tagged = 0
        var failed = cleanup.alreadyFailedCards
        for (noteId in cleanup.notesToTag) {
            if (tagNoteArchived(authority, noteId)) {
                tagged++
            } else {
                failed++
            }
        }
        val message = ProviderArchiveCleanupPolicy.removalMessage(tagged, failed)
        return AnkiDroidGateway.RemovalSummary(cleanup.sourceCards, 0, tagged, message)
    }

    private fun archiveCleanupCards(cards: List<RecordsSyncModels.Card>): List<ProviderArchiveCleanupPolicy.Card> {
        val cleanupCards = ArrayList<ProviderArchiveCleanupPolicy.Card>()
        for (card in cards) {
            cleanupCards.add(ProviderArchiveCleanupPolicy.Card(card.cardId, card.noteId, card.suspended))
        }
        return cleanupCards
    }

    private fun selectedSuspendedCardIds(imports: List<RecordsImportModels.SuspendedImport>?): Set<Long>? {
        if (imports == null) {
            return null
        }
        val sources = ArrayList<SyncMirrorPolicy.SelectedSource>()
        for (imported in imports) {
            for (source in imported.sources) {
                sources.add(SyncMirrorPolicy.SelectedSource(source.cardId, source.suspended))
            }
        }
        return SyncMirrorPolicy.selectedSuspendedCardIds(sources)
    }

    private fun tagNoteArchived(authority: String, noteId: Long): Boolean {
        val noteUri = uriFor(authority, URI_SEGMENT_NOTES, noteId.toString())
        var tags = ""
        val rawCursor = resolver.query(noteUri, arrayOf(COLUMN_TAGS), null, null, null)
        if (rawCursor != null) {
            rawCursor.use { cursor ->
                if (cursor.moveToFirst()) {
                    tags = value(cursor, COLUMN_TAGS)
                }
            }
        }
        if (!ProviderNotePolicy.isArchivedTagPresent(splitTags(tags))) {
            tags = "$tags ${ProviderNotePolicy.ARCHIVED_TAG}".trim()
        }
        val values = ContentValues()
        values.put(COLUMN_TAGS, tags)
        return resolver.update(noteUri, values, null, null) > 0
    }

    private fun uriFor(authority: String, vararg segments: String): Uri {
        val builder = Uri.Builder().scheme(CONTENT_SCHEME).authority(authority)
        for (segment in segments) {
            builder.appendPath(segment)
        }
        return builder.build()
    }

    private fun value(cursor: Cursor, column: String): String {
        val index = cursor.getColumnIndex(column)
        if (index < 0 || cursor.isNull(index)) {
            return ""
        }
        return cursor.getString(index)
    }

    private fun splitTags(value: String): List<String> {
        val tags = ArrayList<String>()
        for (tag in NOTES_WHITESPACE_SEPARATOR.split(value)) {
            val trimmed = tag.trim()
            if (trimmed.isNotEmpty()) {
                tags.add(trimmed)
            }
        }
        return tags
    }

    companion object {
        private const val CONTENT_SCHEME = "content"
        private const val COLUMN_TAGS = "tags"
        private const val URI_SEGMENT_NOTES = "notes"
        private val NOTES_WHITESPACE_SEPARATOR: Pattern = Pattern.compile("\\s+")
    }
}
