package dev.bee.kanjianki.anki

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.OperationCanceledException
import java.util.regex.Pattern

class FakeAnkiDroidProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        reset()
        return true
    }

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val result = Bundle()
        when (method) {
            "reset" -> {
                reset()
                result.putBoolean("ok", true)
            }
            "topLevelCardsQueries" -> result.putInt("value", topLevelCardsQueries)
            "perNoteCardsQueries" -> result.putInt("value", perNoteCardsQueries)
            "explicitIdProjectionQueries" -> result.putInt("value", explicitIdProjectionQueries)
            "schedulerProjectionRejects" -> result.putInt("value", schedulerProjectionRejects)
            "fsrsProjectionRejects" -> result.putInt("value", fsrsProjectionRejects)
            "browserQueryQueries" -> result.putInt("value", browserQueryQueries)
            "cardProjectionRejects" -> result.putInt("value", cardProjectionRejects)
            "suspendedTags" -> result.putString("value", suspendedTags)
            "repairedTags" -> result.putInt("value", repairedTags)
            "repairedTagUpdates" -> result.putInt("value", repairedTagUpdates)
            "repairedTagsForNote" -> result.putString("value", repairedTagsByNote[arg?.toLongOrNull()].orEmpty())
            "failRepairedNote" -> {
                failRepairedNoteId = arg?.toLongOrNull()
                result.putBoolean("ok", true)
            }
            "failSuspendedSearch" -> {
                failSuspendedSearch = true
                result.putBoolean("ok", true)
            }
            "rejectFsrsProjection" -> {
                rejectFsrsProjection = true
                result.putBoolean("ok", true)
            }
            "unparseableFsrsData" -> {
                unparseableFsrsData = true
                result.putBoolean("ok", true)
            }
            "dataOnlyFsrs" -> {
                dataOnlyFsrs = true
                result.putBoolean("ok", true)
            }
            "rejectSchedulerProjection" -> {
                rejectSchedulerProjection = true
                result.putBoolean("ok", true)
            }
            "deferSchedulerProjectionFailure" -> {
                rejectSchedulerProjection = true
                deferSchedulerProjectionFailure = true
                result.putBoolean("ok", true)
            }
            "browserQueryMatchesActive" -> {
                browserQueryMatchesActive = true
                result.putBoolean("ok", true)
            }
            "browserQueryMatchesSuspended" -> {
                browserQueryMatchesSuspended = true
                result.putBoolean("ok", true)
            }
            "browserQueryMatchesMissingNote" -> {
                browserQueryMatchesMissingNote = true
                result.putBoolean("ok", true)
            }
            "failBrowserQuery" -> {
                failBrowserQuery = true
                result.putBoolean("ok", true)
            }
            "deferBrowserQueryFailure" -> {
                deferBrowserQueryFailure = true
                result.putBoolean("ok", true)
            }
            "failConfiguredSearch" -> {
                failConfiguredSearch = true
                result.putBoolean("ok", true)
            }
            "secondTemplateCard" -> {
                secondTemplateCard = true
                result.putBoolean("ok", true)
            }
            "permanentProviderFailure" -> {
                permanentProviderFailure = true
                result.putBoolean("ok", true)
            }
            "retryableProviderFailure" -> {
                retryableProviderFailure = true
                result.putBoolean("ok", true)
            }
            "rejectAllCardProjections" -> {
                rejectAllCardProjections = true
                result.putBoolean("ok", true)
            }
            "legacyTopLevelCardsUnsupported" -> {
                legacyTopLevelCardsUnsupported = true
                result.putBoolean("ok", true)
            }
            "nullCardCursor" -> {
                nullCardCursor = true
                result.putBoolean("ok", true)
            }
            "partiallySuspendedNote" -> {
                partiallySuspendedNote = true
                result.putBoolean("ok", true)
            }
            "operationCanceledProviderFailure" -> {
                operationCanceledProviderFailure = true
                result.putBoolean("ok", true)
            }
            "securityProviderFailure" -> {
                securityProviderFailure = true
                result.putBoolean("ok", true)
            }
            "nullModelsCursor" -> {
                nullModelsCursor = true
                result.putBoolean("ok", true)
            }
            "nullConfiguredSearchCursor" -> {
                nullConfiguredSearchCursor = true
                result.putBoolean("ok", true)
            }
            "nullSqlNotesCursor" -> {
                nullSqlNotesCursor = true
                result.putBoolean("ok", true)
            }
            "nullSuspendedSearchCursor" -> {
                nullSuspendedSearchCursor = true
                result.putBoolean("ok", true)
            }
            "nullBrowserQueryCursor" -> {
                nullBrowserQueryCursor = true
                result.putBoolean("ok", true)
            }
            "browserQueryWrongModel" -> {
                browserQueryWrongModel = true
                result.putBoolean("ok", true)
            }
            "nullNoteCursor" -> {
                nullNoteCursor = true
                result.putBoolean("ok", true)
            }
            "configuredSearchIncludesWrongModel" -> {
                configuredSearchIncludesWrongModel = true
                result.putBoolean("ok", true)
            }
            "failBrowserQueryReread" -> {
                failBrowserQueryReread = true
                result.putBoolean("ok", true)
            }
            "pretagSuspendedArchived" -> {
                suspendedTags = "leech kani_archived"
                result.putBoolean("ok", true)
            }
            "pretagSuspendedRepaired" -> {
                suspendedTags = "leech kani_repaired"
                result.putBoolean("ok", true)
            }
        }
        return result
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        val path = uri.path ?: ""
        rejectExplicitIdProjection(projection)
        if (path == "/models") {
            if (operationCanceledProviderFailure) {
                throw OperationCanceledException("fake provider timed out")
            }
            if (securityProviderFailure) {
                throw SecurityException("fake provider denied access")
            }
            if (permanentProviderFailure) {
                throw IllegalStateException("model metadata cursor failed")
            }
            if (retryableProviderFailure) {
                throw IllegalStateException("database locked")
            }
            if (nullModelsCursor) {
                return null
            }
            val cursor = MatrixCursor(arrayOf("_id", "name", "field_names"))
            cursor.addRow(arrayOf<Any?>(100L, "Kiku", fields("Expression", "ExpressionReading", "MainDefinition", "Sentence", "Frequency", "FreqSort", "Glossary")))
            cursor.addRow(arrayOf<Any?>(200L, "Custom Japanese", fields("Front", "Reading", "Back", "Example", "Frequency", "FrequencySort")))
            return cursor
        }
        if (path == "/notes" && nullConfiguredSearchCursor && isConfiguredModelSearch(selection.orEmpty())) {
            return null
        }
        if (path == "/notes_v2" && nullSqlNotesCursor) {
            return null
        }
        if (path == "/notes" && failConfiguredSearch && isConfiguredModelSearch(selection.orEmpty())) {
            throw IllegalArgumentException("model search failed")
        }
        if (path == "/notes" || path == "/notes_v2") {
            return notes(selection.orEmpty(), allowBrowserQuerySearch = path == "/notes")
        }
        if (NOTES_ID_PATH.matcher(path).matches()) {
            return noteById(uri.lastPathSegment!!.toLong(), projection)
        }
        if (NOTES_CARDS_PATH.matcher(path).matches()) {
            if (nullCardCursor) {
                return null
            }
            if (rejectAllCardProjections) {
                cardProjectionRejects++
                throw IllegalArgumentException("card projection exhausted for ${firstProjectionColumn(projection)}")
            }
            rejectFsrsProjectionColumns(projection)
            val rejected = rejectedSchedulerProjectionCursor(uri, projection, true)
            if (rejected != null) {
                return rejected
            }
            perNoteCardsQueries++
            return cardRowsForNote(uri.pathSegments[1].toLong(), projection)
        }
        if (path == "/cards") {
            topLevelCardsQueries++
            if (legacyTopLevelCardsUnsupported) {
                // Mirrors AnkiDroid releases before 2.24.0, whose UriMatcher
                // has no top-level cards path and throws at query time.
                throw IllegalArgumentException("uri $uri is not supported")
            }
            if (nullCardCursor) {
                return null
            }
            if (rejectAllCardProjections) {
                cardProjectionRejects++
                throw IllegalArgumentException("card projection exhausted for ${firstProjectionColumn(projection)}")
            }
            rejectFsrsProjectionColumns(projection)
            val rejected = rejectedSchedulerProjectionCursor(uri, projection, false)
            if (rejected != null) {
                return rejected
            }
            return cardRowsForSelection(projection, selection, selectionArgs)
        }
        return null
    }

    private fun firstProjectionColumn(projection: Array<String>?): String {
        if (projection.isNullOrEmpty()) {
            return "<none>"
        }
        return projection[0]
    }

    private fun rejectExplicitIdProjection(projection: Array<String>?) {
        projection ?: return
        for (column in projection) {
            if (column == "_id") {
                explicitIdProjectionQueries++
                throw IllegalArgumentException("_id is unknown")
            }
        }
    }

    private fun rejectFsrsProjectionColumns(projection: Array<String>?) {
        if (!rejectFsrsProjection || projection == null) {
            return
        }
        for (column in projection) {
            if (
                column == "fsrs_stability" ||
                column == "fsrs_difficulty" ||
                column == "fsrs_retrievability" ||
                column == "stability" ||
                column == "difficulty" ||
                column == "retrievability" ||
                column == "data"
            ) {
                fsrsProjectionRejects++
                throw IllegalArgumentException("$column is not part of this fake provider")
            }
        }
    }

    private fun rejectedSchedulerProjectionCursor(
        uri: Uri,
        projection: Array<String>?,
        countAsPerNoteQuery: Boolean,
    ): Cursor? {
        if (!rejectSchedulerProjection || projection == null) {
            return null
        }
        for (column in projection) {
            if (
                column == "queue" ||
                column == "type" ||
                column == "due" ||
                column == "interval" ||
                column == "reps" ||
                column == "lapses"
            ) {
                schedulerProjectionRejects++
                if (deferSchedulerProjectionFailure) {
                    if (countAsPerNoteQuery) {
                        perNoteCardsQueries++
                    }
                    return ThrowingCursor(projection, "Queue \"$column\" is unknown")
                }
                throw IllegalArgumentException("$column is not part of the public card projection for $uri")
            }
        }
        return null
    }

    private fun notes(selection: String, allowBrowserQuerySearch: Boolean): Cursor? {
        val cursor = MatrixCursor(arrayOf("_id", "mid", "flds", "tags"))
        val suspendedOnly = isSuspendedModelSearch(selection)
        val browserQuery = allowBrowserQuerySearch && isBrowserQuerySearch(selection)
        if (suspendedOnly && nullSuspendedSearchCursor) {
            return null
        }
        if (suspendedOnly && failSuspendedSearch) {
            throw IllegalArgumentException("queue _id is unknown")
        }
        if (browserQuery && failBrowserQuery) {
            throw IllegalArgumentException("Invalid search: malformed browser query")
        }
        val custom = selection.contains("Custom Japanese")
        if (custom && !browserQuery) {
            if (!suspendedOnly) {
                cursor.addRow(arrayOf<Any?>(101L, 200L, fields("確認", "かくにん", "confirmation", "確認した。", "100", "100"), activeTags))
            }
            cursor.addRow(arrayOf<Any?>(102L, 200L, fields("笥箱", "しはこ", "rare box", "笥箱を見た。", "3500", "3500"), suspendedTags))
            return cursor
        }
        if (browserQuery) {
            browserQueryQueries++
            if (deferBrowserQueryFailure) {
                return ThrowingCursor(
                    arrayOf("_id", "mid", "flds", "tags"),
                    "Invalid search: deferred browser query failure",
                )
            }
            if (failBrowserQueryReread && browserQueryQueries > 1) {
                throw IllegalArgumentException("second browser query failed")
            }
            if (nullBrowserQueryCursor) {
                return null
            }
            if (selection.contains("tag:kani_contract_invalid")) {
                throw IllegalArgumentException("Invalid search")
            }
            if (selection.contains("tag:kani_contract_other_type")) {
                cursor.addRow(arrayOf<Any?>(101L, 200L, fields("確認", "かくにん", "confirmation", "確認した。", "100", "100"), activeTags))
            }
            if (selection.contains("tag:kani_contract_active") || browserQueryMatchesActive) {
                cursor.addRow(arrayOf<Any?>(1L, 100L, fields("確認", "かくにん", "confirmation", "確認した。", "100", "100", repeatString("active-glossary", 200)), activeTags))
            }
            if (selection.contains("tag:kani_contract_suspended") || browserQueryMatchesSuspended) {
                cursor.addRow(arrayOf<Any?>(2L, 100L, fields("笥箱", "しはこ", "rare box", "笥箱を見た。", "3500", "3500", repeatString("suspended-glossary", 200)), suspendedTags))
            }
            if (selection.contains("tag:kani_contract_archived")) {
                cursor.addRow(arrayOf<Any?>(3L, 100L, fields("認", "みとめる", "recognize", "認めた。", "200", "200", repeatString("missing-glossary", 200)), "kani_archived"))
            }
            if (browserQueryWrongModel) {
                cursor.addRow(arrayOf<Any?>(1L, 999L, fields("確認", "かくにん", "confirmation", "確認した。", "100", "100", repeatString("active-glossary", 200)), activeTags))
            }
            if (browserQueryMatchesMissingNote) {
                cursor.addRow(arrayOf<Any?>(3L, 100L, fields("認", "みとめる", "recognize", "認めた。", "200", "200", repeatString("missing-glossary", 200)), ""))
            }
            return cursor
        }
        if (!suspendedOnly) {
            if (configuredSearchIncludesWrongModel) {
                cursor.addRow(arrayOf<Any?>(999L, 999L, fields("無視", "むし", "ignored", "無視した。", "10", "10", "ignored"), activeTags))
            }
            cursor.addRow(arrayOf<Any?>(1L, 100L, fields("確認", "かくにん", "confirmation", "確認した。", "100", "100", repeatString("active-glossary", 200)), activeTags))
        }
        cursor.addRow(arrayOf<Any?>(2L, 100L, fields("笥箱", "しはこ", "rare box", "笥箱を見た。", "3500", "3500", repeatString("suspended-glossary", 200)), suspendedTags))
        return cursor
    }

    private fun noteById(noteId: Long, projection: Array<String>?): Cursor? {
        if (nullNoteCursor) {
            return null
        }
        val columns = projection ?: arrayOf("_id", "mid", "flds", "tags")
        val cursor = MatrixCursor(columns)
        when (noteId) {
            1L -> addNoteRow(cursor, columns, 1L, fields("確認", "かくにん", "confirmation", "確認した。", "100", "100", repeatString("active-glossary", 200)), activeTags)
            2L -> addNoteRow(cursor, columns, 2L, fields("笥箱", "しはこ", "rare box", "笥箱を見た。", "3500", "3500", repeatString("suspended-glossary", 200)), suspendedTags)
            3L -> addNoteRow(cursor, columns, 3L, fields("認", "みとめる", "recognize", "認めた。", "200", "200", repeatString("missing-glossary", 200)), "")
        }
        return cursor
    }

    private fun addNoteRow(cursor: MatrixCursor, columns: Array<String>, noteId: Long, fields: String, tags: String) {
        val row = arrayOfNulls<Any>(columns.size)
        for (i in columns.indices) {
            row[i] = when (columns[i]) {
                "_id" -> noteId
                "mid" -> 100L
                "flds" -> fields
                "tags" -> tags
                else -> null
            }
        }
        cursor.addRow(row)
    }

    private fun addCardRow(
        cursor: MatrixCursor,
        columns: Array<String>,
        cardId: Long,
        noteId: Long,
        ord: Int,
        deckId: String,
        cardName: String,
        queue: Int,
        type: Int,
        due: Int,
        interval: Int,
        reps: Int,
        lapses: Int,
        fsrsStability: Double?,
        fsrsDifficulty: Double?,
        fsrsRetrievability: Double?,
    ) {
        val row = arrayOfNulls<Any>(columns.size)
        for (i in columns.indices) {
            row[i] = when (columns[i]) {
                "_id" -> cardId
                "note_id" -> noteId
                "ord" -> ord
                "deck_id" -> deckId
                "card_name" -> cardName
                "queue" -> queue
                "type" -> type
                "due" -> due
                "interval" -> interval
                "reps" -> reps
                "lapses" -> lapses
                "fsrs_stability", "stability" -> if (unparseableFsrsData || dataOnlyFsrs) null else fsrsStability
                "fsrs_difficulty", "difficulty" -> if (unparseableFsrsData || dataOnlyFsrs) null else fsrsDifficulty
                "fsrs_retrievability", "retrievability" -> if (unparseableFsrsData || dataOnlyFsrs) null else fsrsRetrievability
                "data" -> if (unparseableFsrsData) "{memory:'later'}" else "stability=12.5,difficulty=7.0,retrievability=0.42"
                else -> null
            }
        }
        cursor.addRow(row)
    }

    private fun cardRowsForNote(noteId: Long, projection: Array<String>?): Cursor {
        val columns = projection ?: arrayOf("_id", "note_id", "ord", "deck_id", "card_name")
        val cursor = MatrixCursor(columns)
        appendCardRowsForNote(cursor, columns, noteId)
        return cursor
    }

    private fun cardRowsForSelection(projection: Array<String>?, selection: String?, selectionArgs: Array<String>?): Cursor {
        val columns = projection ?: arrayOf("_id", "note_id", "ord", "deck_id", "card_name")
        val cursor = MatrixCursor(columns)
        val noteIds = selectionArgs
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()
            .ifEmpty {
                selection
                    ?.let { selectedCardNoteIds(it) }
                    .orEmpty()
                    .ifEmpty { listOf(1L, 2L, 3L) }
            }
        for (noteId in noteIds) {
            appendCardRowsForNote(cursor, columns, noteId)
        }
        return cursor
    }

    private fun selectedCardNoteIds(selection: String): List<Long> {
        return NID_SELECTION_PATTERN
            .findAll(selection)
            .flatMap { match ->
                match.groupValues
                    .getOrNull(1)
                    ?.split(',')
                    .orEmpty()
                    .asSequence()
            }
            .mapNotNull { it.trim().toLongOrNull() }
            .toList()
    }

    private fun appendCardRowsForNote(cursor: MatrixCursor, columns: Array<String>, noteId: Long) {
        when (noteId) {
            1L, 101L -> addCardRow(
                cursor,
                columns,
                if (noteId == 101L) 110L else 10L,
                noteId,
                if (secondTemplateCard && noteId == 1L) 1 else 0,
                "Kiku",
                "Mining",
                2,
                2,
                12,
                42,
                80,
                3,
                12.5,
                7.0,
                0.42,
            )
            2L, 102L -> {
                addCardRow(
                    cursor,
                    columns,
                    if (noteId == 102L) 120L else 20L,
                    noteId,
                    0,
                    "Kiku",
                    "Mining",
                    -1,
                    2,
                    0,
                    10,
                    5,
                    1,
                    null,
                    null,
                    null,
                )
                if (partiallySuspendedNote) {
                    addCardRow(
                        cursor,
                        columns,
                        21L,
                        noteId,
                        0,
                        "Kiku",
                        "Mining",
                        2,
                        2,
                        4,
                        5,
                        6,
                        0,
                        null,
                        null,
                        null,
                    )
                }
            }
            3L -> addCardRow(
                cursor,
                columns,
                30L,
                noteId,
                0,
                "Kiku",
                "Mining",
                2,
                2,
                4,
                5,
                6,
                0,
                null,
                null,
                null,
            )
            else -> addCardRow(
                cursor,
                columns,
                noteId * 10L,
                noteId,
                0,
                "Kiku",
                "Mining",
                2,
                2,
                12,
                42,
                80,
                3,
                12.5,
                7.0,
                0.42,
            )
        }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?): Int {
        val path = uri.path ?: ""
        if (!NOTES_ID_PATH.matcher(path).matches()) {
            return 0
        }
        val noteId = uri.lastPathSegment!!.toLong()
        val tags = values?.getAsString("tags") ?: ""
        val repairedUpdate = tags.split(Regex("\\s+")).contains("kani_repaired")
        if (repairedUpdate) {
            repairedTagUpdates++
            if (noteId == failRepairedNoteId) {
                return 0
            }
        }
        val updated = when (noteId) {
            1L -> {
                activeTags = tags
                1
            }
            2L -> {
                suspendedTags = tags
                1
            }
            else -> 0
        }
        if (updated > 0 && repairedUpdate) {
            repairedTagsByNote[noteId] = tags
            repairedTags = repairedTagsByNote.size
        }
        return updated
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        throw UnsupportedOperationException("delete not supported by fake AnkiDroid")
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.com.ichi2.anki"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    private fun fields(vararg values: String): String {
        val out = StringBuilder()
        for (i in values.indices) {
            if (i > 0) {
                out.append(FIELD_SEPARATOR)
            }
            out.append(values[i])
        }
        return out.toString()
    }

    private fun repeatString(value: String, count: Int): String {
        val out = StringBuilder(value.length * count)
        repeat(count) {
            out.append(value)
        }
        return out.toString()
    }

    private fun isConfiguredModelSearch(selection: String): Boolean {
        val trimmed = selection.trim()
        return trimmed.startsWith("note:\"") && trimmed.endsWith("\"") && !trimmed.contains("(")
    }

    private fun isSuspendedModelSearch(selection: String): Boolean {
        val trimmed = selection.trim()
        return trimmed.startsWith("note:\"") && trimmed.endsWith(" is:suspended")
    }

    private fun isBrowserQuerySearch(selection: String): Boolean {
        val trimmed = selection.trim()
        if (trimmed.isEmpty()) {
            return false
        }
        val legacyWrappedQuery = trimmed.contains("note:\"Kiku\"") && trimmed.contains("(") && trimmed.endsWith(")")
        return legacyWrappedQuery || (!isConfiguredModelSearch(trimmed) && !isSuspendedModelSearch(trimmed))
    }

    private class ThrowingCursor(columns: Array<String>, message: String) : MatrixCursor(columns) {
        private val error: RuntimeException = IllegalArgumentException(message)

        init {
            addRow(arrayOfNulls<Any>(columns.size))
        }

        override fun onMove(oldPosition: Int, newPosition: Int): Boolean {
            throw error
        }
    }

    companion object {
        const val AUTHORITY = "dev.bee.kanjianki.test.ankidroid"
        private const val FIELD_SEPARATOR = '\u001f'
        private val NOTES_CARDS_PATH: Pattern = Pattern.compile("/notes/\\d+/cards")
        private val NOTES_ID_PATH: Pattern = Pattern.compile("/notes/\\d+")
        private val NID_SELECTION_PATTERN = Regex("""nid:([0-9,]+)""", RegexOption.IGNORE_CASE)

        @JvmField
        var topLevelCardsQueries = 0

        @JvmField
        var perNoteCardsQueries = 0

        @JvmField
        var explicitIdProjectionQueries = 0

        @JvmField
        var schedulerProjectionRejects = 0

        @JvmField
        var fsrsProjectionRejects = 0

        @JvmField
        var browserQueryQueries = 0

        @JvmField
        var cardProjectionRejects = 0

        @JvmField
        var activeTags = ""

        @JvmField
        var suspendedTags = ""

        @JvmField
        var repairedTags = 0

        @JvmField
        var repairedTagUpdates = 0

        @JvmField
        var repairedTagsByNote: MutableMap<Long, String> = linkedMapOf()

        @JvmField
        var failRepairedNoteId: Long? = null

        @JvmField
        var failSuspendedSearch = false

        @JvmField
        var rejectFsrsProjection = false

        @JvmField
        var dataOnlyFsrs = false

        @JvmField
        var unparseableFsrsData = false

        @JvmField
        var rejectSchedulerProjection = false

        @JvmField
        var deferSchedulerProjectionFailure = false

        @JvmField
        var legacyTopLevelCardsUnsupported = false

        @JvmField
        var browserQueryMatchesActive = false

        @JvmField
        var browserQueryMatchesSuspended = false

        @JvmField
        var failBrowserQuery = false

        @JvmField
        var deferBrowserQueryFailure = false

        @JvmField
        var failConfiguredSearch = false

        @JvmField
        var secondTemplateCard = false

        @JvmField
        var browserQueryMatchesMissingNote = false

        @JvmField
        var permanentProviderFailure = false

        @JvmField
        var retryableProviderFailure = false

        @JvmField
        var rejectAllCardProjections = false

        @JvmField
        var nullCardCursor = false

        @JvmField
        var partiallySuspendedNote = false

        @JvmField
        var operationCanceledProviderFailure = false

        @JvmField
        var securityProviderFailure = false

        @JvmField
        var nullModelsCursor = false

        @JvmField
        var nullConfiguredSearchCursor = false

        @JvmField
        var nullSqlNotesCursor = false

        @JvmField
        var nullSuspendedSearchCursor = false

        @JvmField
        var nullBrowserQueryCursor = false

        @JvmField
        var browserQueryWrongModel = false

        @JvmField
        var nullNoteCursor = false

        @JvmField
        var configuredSearchIncludesWrongModel = false

        @JvmField
        var failBrowserQueryReread = false

        @JvmStatic
        fun reset() {
            topLevelCardsQueries = 0
            perNoteCardsQueries = 0
            explicitIdProjectionQueries = 0
            schedulerProjectionRejects = 0
            fsrsProjectionRejects = 0
            browserQueryQueries = 0
            cardProjectionRejects = 0
            activeTags = ""
            suspendedTags = ""
            repairedTags = 0
            repairedTagUpdates = 0
            repairedTagsByNote = linkedMapOf()
            failRepairedNoteId = null
            failSuspendedSearch = false
            rejectFsrsProjection = false
            dataOnlyFsrs = false
            unparseableFsrsData = false
            rejectSchedulerProjection = false
            deferSchedulerProjectionFailure = false
            legacyTopLevelCardsUnsupported = false
            browserQueryMatchesActive = false
            browserQueryMatchesSuspended = false
            failBrowserQuery = false
            deferBrowserQueryFailure = false
            failConfiguredSearch = false
            secondTemplateCard = false
            browserQueryMatchesMissingNote = false
            permanentProviderFailure = false
            retryableProviderFailure = false
            rejectAllCardProjections = false
            nullCardCursor = false
            partiallySuspendedNote = false
            operationCanceledProviderFailure = false
            securityProviderFailure = false
            nullModelsCursor = false
            nullConfiguredSearchCursor = false
            nullSqlNotesCursor = false
            nullSuspendedSearchCursor = false
            nullBrowserQueryCursor = false
            browserQueryWrongModel = false
            nullNoteCursor = false
            configuredSearchIncludesWrongModel = false
            failBrowserQueryReread = false
        }
    }
}
