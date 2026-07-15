package dev.bee.kanjianki.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.TimelineCopy

internal class LocalStoreTimeline(private val activity: LocalStoreHistory) {
    fun createTimelineTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS kanji_timeline_events (id INTEGER PRIMARY KEY AUTOINCREMENT, kanji TEXT NOT NULL, occurred_at INTEGER NOT NULL, event_type TEXT NOT NULL, title TEXT NOT NULL, detail TEXT NOT NULL, source_expression TEXT NOT NULL, source_reading TEXT NOT NULL, rating TEXT NOT NULL, writing_required INTEGER NOT NULL DEFAULT 0, writing_passed INTEGER NOT NULL DEFAULT 0, manual_override INTEGER NOT NULL DEFAULT 0, weakness_score INTEGER, mature_support_count INTEGER, sync_id INTEGER, dedupe_key TEXT NOT NULL)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_timeline_dedupe ON kanji_timeline_events(dedupe_key)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_timeline_kanji_time ON kanji_timeline_events(kanji, occurred_at, id)")
    }

    fun backfillTimelineEvents(db: SQLiteDatabase) {
        val rows = activity.rowSnapshots(db)
        backfillSuspendedImportTimeline(db)
        backfillRowTimeline(db, rows)
        backfillStudyTimeline(db, rows)
        backfillReviewTimeline(db)
    }

    fun backfillSuspendedImportTimeline(db: SQLiteDatabase) {
        db.query(
            LocalStoreBase.TABLE_SUSPENDED_IMPORTS,
            null,
            null,
            null,
            null,
            null,
            "first_imported_at ASC, kanji ASC"
        ).use { imports ->
            while (imports.moveToNext()) {
                val kanji = LocalStoreBase.string(imports, LocalStoreBase.COLUMN_KANJI)
                val source = activity.firstSuspendedSourceForKanji(db, kanji)
                val importedAt = LocalStoreBase.longValue(imports, LocalStoreBase.COLUMN_FIRST_IMPORTED_AT)
                insertTimelineEvent(
                    db,
                    kanji,
                    if (importedAt == 0L) System.currentTimeMillis() else importedAt,
                    "suspended_imported",
                    TimelineCopy.suspendedImportedTitle(),
                    TimelineCopy.suspendedImportedDetail(),
                    source.expression,
                    source.reading,
                    "",
                    false,
                    false,
                    false,
                    null,
                    null,
                    LocalStoreBase.longValue(imports, LocalStoreBase.COLUMN_LAST_SEEN_SYNC_ID),
                    "suspended_imported:$kanji"
                )
            }
        }
    }

    fun backfillRowTimeline(db: SQLiteDatabase, rows: Map<String, LocalStoreBase.RowSnapshot>) {
        for (row in rows.values) {
            val occurredAt = if (row.rebuiltAt == 0L) System.currentTimeMillis() else row.rebuiltAt
            insertTimelineEvent(
                db,
                row.kanji,
                occurredAt,
                LocalStoreBase.TIMELINE_FIRST_SEEN,
                TimelineCopy.firstSeenTitle(),
                TimelineCopy.firstSeenAnkiEvidenceDetail(),
                row.source.expression,
                row.source.reading,
                "",
                false,
                false,
                false,
                row.weaknessScore,
                row.matureSupportCount,
                null,
                LocalStoreBase.TIMELINE_FIRST_SEEN_KEY_PREFIX + row.kanji
            )
            insertTimelineEvent(
                db,
                row.kanji,
                occurredAt,
                "weak_support_seen",
                TimelineCopy.weakSupportSeenTitle(),
                TimelineCopy.supportDetail(
                    "Anki evidence still needs repair",
                    row.matureSupportCount,
                    RecordsSyncModels.Settings.kikuDefaults().matureSupportThreshold
                ),
                row.source.expression,
                row.source.reading,
                "",
                false,
                false,
                false,
                row.weaknessScore,
                row.matureSupportCount,
                null,
                "weak_support_seen:" + row.kanji + ":backfill"
            )
        }
    }

    fun backfillStudyTimeline(db: SQLiteDatabase, rows: Map<String, LocalStoreBase.RowSnapshot>) {
        db.query(LocalStoreBase.TABLE_STUDY_ITEMS, null, null, null, null, null, "created_at ASC, kanji ASC").use { study ->
            while (study.moveToNext()) {
                backfillStudyTimelineRow(db, rows, study)
            }
        }
    }

    fun backfillStudyTimelineRow(
        db: SQLiteDatabase,
        rows: Map<String, LocalStoreBase.RowSnapshot>,
        study: Cursor,
    ) {
        val kanji = LocalStoreBase.string(study, LocalStoreBase.COLUMN_KANJI)
        val occurredAt = defaultTimelineTime(LocalStoreBase.longValue(study, LocalStoreBase.COLUMN_CREATED_AT))
        val row = rows[kanji]
        val source = row?.source ?: activity.firstExampleForKanji(db, kanji)
        if (row == null) {
            insertTimelineEvent(
                db,
                kanji,
                occurredAt,
                LocalStoreBase.TIMELINE_FIRST_SEEN,
                TimelineCopy.firstSeenTitle(),
                TimelineCopy.firstSeenHistoricalStudyDetail(),
                source.expression,
                source.reading,
                "",
                false,
                false,
                false,
                null,
                null,
                null,
                LocalStoreBase.TIMELINE_FIRST_SEEN_KEY_PREFIX + kanji
            )
        }
        if (LocalStoreBase.STATE_RETIRED == LocalStoreBase.string(study, LocalStoreBase.COLUMN_STATE)) {
            val mature = row?.matureSupportCount
            insertTimelineEvent(
                db,
                kanji,
                occurredAt,
                LocalStoreBase.STATE_RETIRED,
                TimelineCopy.retiredByAnkiSupportTitle(),
                if (mature == null) {
                    TimelineCopy.historicalRetiredDetail()
                } else {
                    TimelineCopy.supportDetail(
                        "Mature Anki support met the target",
                        mature,
                        RecordsSyncModels.Settings.kikuDefaults().matureSupportThreshold
                    )
                },
                source.expression,
                source.reading,
                "",
                false,
                false,
                false,
                row?.weaknessScore,
                mature,
                null,
                "retired:$kanji:backfill"
            )
        }
    }

    fun backfillReviewTimeline(db: SQLiteDatabase) {
        db.query(LocalStoreBase.TABLE_REVIEW_LOG, null, null, null, null, null, "reviewed_at ASC, id ASC").use { reviews ->
            while (reviews.moveToNext()) {
                val rating = LocalStoreBase.string(reviews, LocalStoreBase.COLUMN_RATING)
                val writingPassed = LocalStoreBase.integer(reviews, LocalStoreBase.COLUMN_WRITING_PASSED) == 1
                val request = RecordsSchedulerModels.ReviewRequest.fromFields(
                    RecordsSchedulerModels.ReviewRequest.Fields(
                        kanji = LocalStoreBase.string(reviews, LocalStoreBase.COLUMN_KANJI),
                        token = LocalStoreBase.string(reviews, LocalStoreBase.COLUMN_TOKEN),
                        rating = rating,
                        writingRequired = LocalStoreBase.integer(
                            reviews,
                            LocalStoreBase.COLUMN_WRITING_REQUIRED,
                        ) == 1,
                        writingPassed = writingPassed,
                        writingClean = writingPassed && (rating == "good" || rating == "easy"),
                        manualOverride = LocalStoreBase.integer(
                            reviews,
                            LocalStoreBase.COLUMN_MANUAL_OVERRIDE,
                        ) == 1,
                        hintsUsed = 0,
                    ),
                )
                appendReviewTimelineEvent(
                    db,
                    request,
                    LocalStoreBase.string(reviews, LocalStoreBase.COLUMN_RATING),
                    LocalStoreBase.longValue(reviews, LocalStoreBase.COLUMN_REVIEWED_AT),
                    "review:" + request.token
                )
            }
        }
    }

    fun appendSyncTimelineEvents(
        db: SQLiteDatabase,
        previousRows: Map<String, LocalStoreBase.RowSnapshot>,
        imports: List<RecordsImportModels.SuspendedImport>,
        rows: List<RecordsImportModels.DashboardRow>,
        syncId: Long,
        occurredAt: Long,
        settings: RecordsSyncModels.Settings?,
    ) {
        val target = settings?.matureSupportThreshold ?: RecordsSyncModels.Settings.kikuDefaults().matureSupportThreshold
        for (imported in imports) {
            val source = activity.sourceFromImport(imported)
            insertTimelineEvent(
                db,
                imported.kanji,
                occurredAt,
                "suspended_imported",
                TimelineCopy.suspendedImportedTitle(),
                TimelineCopy.suspendedImportedDetail(),
                source.expression,
                source.reading,
                "",
                false,
                false,
                false,
                null,
                null,
                syncId,
                "suspended_imported:" + imported.kanji
            )
        }

        for (row in rows) {
            val previous = previousRows[row.kanji]
            val source = activity.sourceForRow(row)
            insertTimelineEvent(
                db,
                row.kanji,
                occurredAt,
                LocalStoreBase.TIMELINE_FIRST_SEEN,
                TimelineCopy.firstSeenTitle(),
                TimelineCopy.firstSeenAnkiEvidenceDetail(),
                source.expression,
                source.reading,
                "",
                false,
                false,
                false,
                row.weaknessScore,
                row.matureSupportCount,
                syncId,
                LocalStoreBase.TIMELINE_FIRST_SEEN_KEY_PREFIX + row.kanji
            )
            when (previous) {
                null -> {
                    insertTimelineEvent(
                        db,
                        row.kanji,
                        occurredAt,
                        "weak_support_seen",
                        TimelineCopy.weakSupportSeenTitle(),
                        TimelineCopy.supportDetail("Anki evidence still needs repair", row.matureSupportCount, target),
                        source.expression,
                        source.reading,
                        "",
                        false,
                        false,
                        false,
                        row.weaknessScore,
                        row.matureSupportCount,
                        syncId,
                        "weak_support_seen:" + row.kanji + ":" + syncId
                    )
                }
                else -> when {
                    row.matureSupportCount > previous.matureSupportCount -> {
                        insertTimelineEvent(
                            db,
                            row.kanji,
                            occurredAt,
                            "support_improved",
                            TimelineCopy.supportImprovedTitle(),
                            TimelineCopy.supportImprovedDetail(previous.matureSupportCount, row.matureSupportCount),
                            source.expression,
                            source.reading,
                            "",
                            false,
                            false,
                            false,
                            row.weaknessScore,
                            row.matureSupportCount,
                            syncId,
                            "support_improved:" + row.kanji + ":" + syncId + ":" +
                                previous.matureSupportCount + "-" + row.matureSupportCount
                        )
                    }
                    row.matureSupportCount < previous.matureSupportCount -> {
                        insertTimelineEvent(
                            db,
                            row.kanji,
                            occurredAt,
                            "support_dropped",
                            TimelineCopy.supportDroppedTitle(),
                            TimelineCopy.supportDroppedDetail(previous.matureSupportCount, row.matureSupportCount),
                            source.expression,
                            source.reading,
                            "",
                            false,
                            false,
                            false,
                            row.weaknessScore,
                            row.matureSupportCount,
                            syncId,
                            "support_dropped:" + row.kanji + ":" + syncId + ":" +
                                previous.matureSupportCount + "-" + row.matureSupportCount
                        )
                    }
                }
            }
        }
    }

    fun appendStudyStateTimelineEvents(
        db: SQLiteDatabase,
        previousItems: Map<String, LocalStoreBase.StudySnapshot>,
        currentItems: List<RecordsStudyModels.StudyItem>,
        syncId: Long,
        occurredAt: Long,
        settings: RecordsSyncModels.Settings?,
    ) {
        val target = settings?.matureSupportThreshold ?: RecordsSyncModels.Settings.kikuDefaults().matureSupportThreshold
        for (item in currentItems) {
            val previous = previousItems[LocalStoreBase.studyFamilyKey(item.kanji, item.answerSignature)]
            if (previous != null) {
                appendStudyStateTimelineEvent(db, item, previous, syncId, occurredAt, target)
            }
        }
    }

    fun appendStudyStateTimelineEvent(
        db: SQLiteDatabase,
        item: RecordsStudyModels.StudyItem,
        previous: LocalStoreBase.StudySnapshot,
        syncId: Long,
        occurredAt: Long,
        target: Int,
    ) {
        if ((LocalStoreBase.STATE_RETIRED == item.state) == (LocalStoreBase.STATE_RETIRED == previous.state)) {
            return
        }
        val row = activity.rowSnapshot(db, item.kanji)
        val source = row?.source ?: activity.firstExampleForKanji(db, item.kanji)
        val mature = row?.matureSupportCount
        val retired = LocalStoreBase.STATE_RETIRED == item.state
        insertTimelineEvent(
            db,
            item.kanji,
            occurredAt,
            if (retired) LocalStoreBase.STATE_RETIRED else "reopened",
            if (retired) TimelineCopy.retiredByAnkiSupportTitle() else TimelineCopy.repairReopenedTitle(),
            TimelineCopy.studyStateDetail(retired, mature, target),
            source.expression,
            source.reading,
            "",
            false,
            false,
            false,
            row?.weaknessScore,
            mature,
            syncId,
            (if (retired) "retired:" else "reopened:") + LocalStoreBase.studyTimelineKey(item) + ":" + syncId
        )
    }

    fun appendReviewTimelineEvent(
        db: SQLiteDatabase,
        request: RecordsSchedulerModels.ReviewRequest,
        appliedRating: String?,
        reviewedAt: Long,
        dedupeKey: String?,
    ) {
        val event = TimelineCopy.reviewEvent(request, appliedRating)
        val source = activity.firstExampleForKanji(db, request.kanji)
        val row = activity.rowSnapshot(db, request.kanji)
        insertTimelineEvent(
            db,
            request.kanji,
            reviewedAt,
            event.eventType(),
            event.title(),
            event.detail(),
            source.expression,
            source.reading,
            appliedRating,
            request.writingRequired,
            request.writingPassed,
            request.manualOverride,
            row?.weaknessScore,
            row?.matureSupportCount,
            null,
            dedupeKey
        )
    }

    fun readTimelineEvent(cursor: Cursor): RecordsImportModels.KanjiTimelineEvent {
        return RecordsImportModels.KanjiTimelineEvent(
            LocalStoreBase.longValue(cursor, "id"),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_KANJI),
            LocalStoreBase.longValue(cursor, LocalStoreBase.COLUMN_OCCURRED_AT),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_EVENT_TYPE),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_TITLE),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_DETAIL),
            LocalStoreBase.string(cursor, "source_expression"),
            LocalStoreBase.string(cursor, "source_reading"),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_RATING),
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_WRITING_REQUIRED) == 1,
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_WRITING_PASSED) == 1,
            LocalStoreBase.integer(cursor, LocalStoreBase.COLUMN_MANUAL_OVERRIDE) == 1,
            LocalStoreBase.nullableInt(cursor, LocalStoreBase.COLUMN_WEAKNESS_SCORE),
            LocalStoreBase.nullableInt(cursor, LocalStoreBase.COLUMN_MATURE_SUPPORT_COUNT),
            LocalStoreBase.nullableLong(cursor, LocalStoreBase.COLUMN_SYNC_ID),
            LocalStoreBase.string(cursor, LocalStoreBase.COLUMN_DEDUPE_KEY)
        )
    }

    fun insertTimelineEvent(
        db: SQLiteDatabase,
        kanji: String?,
        occurredAt: Long,
        eventType: String?,
        title: String?,
        detail: String?,
        vararg eventValues: Any?,
    ) {
        val values = ContentValues()
        values.put(LocalStoreBase.COLUMN_KANJI, kanji)
        values.put(LocalStoreBase.COLUMN_OCCURRED_AT, occurredAt)
        values.put(LocalStoreBase.COLUMN_EVENT_TYPE, eventType ?: "")
        values.put(LocalStoreBase.COLUMN_TITLE, title ?: "")
        values.put(LocalStoreBase.COLUMN_DETAIL, detail ?: "")
        val sourceExpression = LocalStoreHistory.stringValueAt(eventValues, 0)
        val sourceReading = LocalStoreHistory.stringValueAt(eventValues, 1)
        val rating = LocalStoreHistory.stringValueAt(eventValues, 2)
        val writingRequired = LocalStoreHistory.booleanValueAt(eventValues, 3)
        val writingPassed = LocalStoreHistory.booleanValueAt(eventValues, 4)
        val manualOverride = LocalStoreHistory.booleanValueAt(eventValues, 5)
        val weaknessScore = LocalStoreHistory.integerValueAt(eventValues, 6)
        val matureSupportCount = LocalStoreHistory.integerValueAt(eventValues, 7)
        val syncId = LocalStoreHistory.longValueAt(eventValues, 8)
        val dedupeKey = LocalStoreHistory.stringValueAt(eventValues, 9)
        values.put("source_expression", sourceExpression)
        values.put("source_reading", sourceReading)
        values.put(LocalStoreBase.COLUMN_RATING, rating)
        values.put(LocalStoreBase.COLUMN_WRITING_REQUIRED, if (writingRequired) 1 else 0)
        values.put(LocalStoreBase.COLUMN_WRITING_PASSED, if (writingPassed) 1 else 0)
        values.put(LocalStoreBase.COLUMN_MANUAL_OVERRIDE, if (manualOverride) 1 else 0)
        values.put(LocalStoreBase.COLUMN_WEAKNESS_SCORE, weaknessScore)
        values.put(LocalStoreBase.COLUMN_MATURE_SUPPORT_COUNT, matureSupportCount)
        values.put(LocalStoreBase.COLUMN_SYNC_ID, syncId)
        values.put(LocalStoreBase.COLUMN_DEDUPE_KEY, dedupeKey)
        db.insertWithOnConflict(
            LocalStoreBase.TABLE_KANJI_TIMELINE_EVENTS,
            null,
            values,
            SQLiteDatabase.CONFLICT_IGNORE
        )
    }

    fun defaultTimelineTime(occurredAt: Long): Long {
        return if (occurredAt == 0L) System.currentTimeMillis() else occurredAt
    }
}
