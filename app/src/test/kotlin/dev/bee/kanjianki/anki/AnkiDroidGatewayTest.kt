@file:Suppress("UNCHECKED_CAST")

package dev.bee.kanjianki.anki

import android.database.Cursor
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.sync.SyncProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnkiDroidGatewayTest {
    @Test
    fun fsrsValuesAreReadFromCardCursorColumns() {
        val fsrs = fsrsFromCursor(
            cursor(
                row(
                    "fsrs_stability", "12.5",
                    "difficulty", "7.25",
                    "fsrs_retrievability", "0.86",
                ),
            ),
        )

        assertEquals(12.5, fieldValue(fsrs, "stability") as Double, 0.0001)
        assertEquals(7.25, fieldValue(fsrs, "difficulty") as Double, 0.0001)
        assertEquals(0.86, fieldValue(fsrs, "retrievability") as Double, 0.0001)
    }

    @Test
    fun partialFsrsColumnsDoNotFallBackToSerializedData() {
        val difficultyOnly = fsrsFromCursor(
            cursor(
                row(
                    "difficulty", "5.5",
                    "data", "stability=9 retrievability=0.1",
                ),
            ),
        )
        val retrievabilityOnly = fsrsFromCursor(
            cursor(
                row(
                    "retrievability", "0.33",
                    "data", "stability=9 difficulty=8",
                ),
            ),
        )

        assertNull(fieldValue(difficultyOnly, "stability"))
        assertEquals(5.5, fieldValue(difficultyOnly, "difficulty") as Double, 0.0001)
        assertNull(fieldValue(difficultyOnly, "retrievability"))
        assertNull(fieldValue(retrievabilityOnly, "stability"))
        assertNull(fieldValue(retrievabilityOnly, "difficulty"))
        assertEquals(0.33, fieldValue(retrievabilityOnly, "retrievability") as Double, 0.0001)
    }

    @Test
    fun fsrsCursorParsingIgnoresInvalidValuesAndUsesFiniteDataKeys() {
        val fsrs = fsrsFromCursor(
            cursor(
                row(
                    "fsrs_stability", "NaN",
                    "fsrs_difficulty", "Infinity",
                    "data", "stability=bad difficulty=6.5 retrievability=Infinity s=3.0",
                ),
            ),
        )

        assertEquals(3.0, fieldValue(fsrs, "stability") as Double, 0.0001)
        assertEquals(6.5, fieldValue(fsrs, "difficulty") as Double, 0.0001)
        assertNull(fieldValue(fsrs, "retrievability"))
    }

    @Test
    fun blankFsrsCursorDataProducesEmptyMemoryState() {
        val fsrs = fsrsFromCursor(cursor(row("data", "   ")))

        assertNull(fieldValue(fsrs, "stability"))
        assertNull(fieldValue(fsrs, "difficulty"))
        assertNull(fieldValue(fsrs, "retrievability"))
    }

    @Test
    fun nullFsrsCursorDataProducesEmptyMemoryState() {
        val fsrs = fsrsFromCursor(cursorWithStringNullButNotSqlNull("data"))

        assertNull(fieldValue(fsrs, "stability"))
        assertNull(fieldValue(fsrs, "difficulty"))
        assertNull(fieldValue(fsrs, "retrievability"))
    }

    @Test
    fun fsrsParserUsesLegacyColumnsBeforeDataFallback() {
        val fsrs = fsrsFromCursor(
            cursor(
                row(
                    "stability", "1.25e1",
                    "difficulty", "4.5",
                    "retrievability", "0.91",
                    "data", "s=3 d=8 r=0.1",
                ),
            ),
        )

        assertEquals(12.5, fieldValue(fsrs, "stability") as Double, 0.0001)
        assertEquals(4.5, fieldValue(fsrs, "difficulty") as Double, 0.0001)
        assertEquals(0.91, fieldValue(fsrs, "retrievability") as Double, 0.0001)
    }

    @Test
    fun fsrsParserUsesLegacyColumnsWhenFsrsColumnsAreNull() {
        val fsrs = fsrsFromCursor(
            cursor(
                row(
                    "fsrs_stability", null,
                    "stability", "2.0",
                    "fsrs_difficulty", null,
                    "difficulty", "3.0",
                    "fsrs_retrievability", null,
                    "retrievability", "0.55",
                    "data", "stability=9 difficulty=8 retrievability=0.1",
                ),
            ),
        )

        assertEquals(2.0, fieldValue(fsrs, "stability") as Double, 0.0001)
        assertEquals(3.0, fieldValue(fsrs, "difficulty") as Double, 0.0001)
        assertEquals(0.55, fieldValue(fsrs, "retrievability") as Double, 0.0001)
    }

    @Test
    fun fsrsDataParserAcceptsQuotedAliasesAndLastFiniteValueWins() {
        val fsrs = fsrsFromCursor(
            cursor(
                row(
                    "data", "'s':\"2.5\" \"difficulty\"=4.25 retrievability=bad r=0.76 s=7.5",
                ),
            ),
        )

        assertEquals(7.5, fieldValue(fsrs, "stability") as Double, 0.0001)
        assertEquals(4.25, fieldValue(fsrs, "difficulty") as Double, 0.0001)
        assertEquals(0.76, fieldValue(fsrs, "retrievability") as Double, 0.0001)
    }

    @Test
    fun fsrsDataParserAcceptsFullKeyNames() {
        val fsrs = fsrsFromCursor(cursor(row("data", "stability=2.2 difficulty=3.3 retrievability=0.44")))

        assertEquals(2.2, fieldValue(fsrs, "stability") as Double, 0.0001)
        assertEquals(3.3, fieldValue(fsrs, "difficulty") as Double, 0.0001)
        assertEquals(0.44, fieldValue(fsrs, "retrievability") as Double, 0.0001)
    }

    @Test
    fun cardProgressReportingIsThrottledForLargeSyncs() {
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(0, 500))
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(1, 500))
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(10, 500))
        assertFalse(AnkiDroidCardReader.shouldReportCardProgress(11, 500))
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(20, 500))
        assertFalse(AnkiDroidCardReader.shouldReportCardProgress(25, 500))
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(500, 500))
    }

    @Test
    fun cardProgressReportingUsesWiderStepsForVeryLargeSyncs() {
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(10, 1500))
        assertFalse(AnkiDroidCardReader.shouldReportCardProgress(20, 1500))
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(50, 1500))
        assertFalse(AnkiDroidCardReader.shouldReportCardProgress(75, 1500))
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(1500, 1500))
    }

    @Test
    fun browserQueryMatchedCardsOnlyCopiesMatchingNotes() {
        val matched = card(10L, 1L)
        val unchanged = card(20L, 2L)

        val cards = invokePrivateStatic(
            "markBrowserQueryMatchedCards",
            arrayOf(List::class.java, Set::class.java),
            listOf(matched, unchanged),
            setOf(1L),
        ) as List<RecordsSyncModels.Card>

        assertTrue(cards[0].browserQueryMatched)
        assertFalse(cards[1].browserQueryMatched)
        assertSame(unchanged, cards[1])
    }

    @Test
    fun browserQueryFailureIsPermanentAndActionable() {
        val gateway = uninitializedGateway()
        val cause = IllegalArgumentException("bad syntax")

        val failure = invokePrivateInstance(
            gateway,
            "browserQueryFailure",
            arrayOf(Throwable::class.java),
            cause,
        ) as AnkiDroidGateway.SyncFailure

        assertTrue(failure.permanentFailure)
        assertEquals("AnkiDroid could not run the browser query. Check the query in Import filters.", failure.message)
        assertSame(cause, failure.cause)
    }

    @Test
    fun cardsWithNotesFiltersCardsWhoseNotesWereSkipped() {
        val gateway = uninitializedGateway()
        val kept = card(10L, 1L)
        val orphan = card(20L, 2L)

        val cards = invokePrivateInstance(
            gateway,
            "cardsWithNotes",
            arrayOf(List::class.java, Set::class.java),
            listOf(kept, orphan),
            setOf(1L),
        ) as List<RecordsSyncModels.Card>

        assertEquals(1, cards.size)
        assertSame(kept, cards[0])
    }

    @Test
    fun splitTagsTrimsTokensAndDropsWhitespaceOnlyValues() {
        val tags = invokePrivateStatic(
            "splitTags",
            arrayOf(String::class.java),
            "  leech   kani_archived\tmarked  ",
        ) as List<String>

        assertEquals(listOf("leech", "kani_archived", "marked"), tags)
    }

    @Test
    fun cursorValueHelpersUseFallbacksForMissingAndNullColumns() {
        val empty = cursor(row())
        val nulls = cursor(row("text", null, "long", null, "int", null))

        assertEquals("", invokePrivateStatic("value", arrayOf(Cursor::class.java, String::class.java), empty, "missing") as String)
        assertEquals("", invokePrivateStatic("value", arrayOf(Cursor::class.java, String::class.java), nulls, "text") as String)
        assertEquals(
            "present",
            invokePrivateStatic(
                "value",
                arrayOf(Cursor::class.java, String::class.java),
                cursor(row("text", "present")),
                "text",
            ) as String,
        )
        assertEquals(
            42L,
            invokePrivateStatic("longValue", arrayOf(Cursor::class.java, String::class.java, java.lang.Long.TYPE), empty, "missing", 42L) as Long,
        )
        assertEquals(
            43L,
            invokePrivateStatic("longValue", arrayOf(Cursor::class.java, String::class.java, java.lang.Long.TYPE), nulls, "long", 43L) as Long,
        )
        assertEquals(
            99L,
            invokePrivateStatic(
                "longValue",
                arrayOf(Cursor::class.java, String::class.java, java.lang.Long.TYPE),
                cursor(row("long", "99")),
                "long",
                0L,
            ) as Long,
        )
    }

    @Test
    fun fsrsCursorTreatsNullStringAsMissingWhenCursorReportsValuePresent() {
        val cursor = cursorWithStringNullButNotSqlNull("fsrs_stability")
        val fsrs = fsrsFromCursor(cursor)

        assertNull(fieldValue(fsrs, "stability"))
        assertNull(fieldValue(fsrs, "difficulty"))
        assertNull(fieldValue(fsrs, "retrievability"))
    }

    @Test
    fun noteTypeConstructorNormalizesNullNameAndFields() {
        val noteTypeClass = Class.forName("${AnkiDroidGateway::class.java.name}\$NoteType")
        val constructor = noteTypeClass.getDeclaredConstructor(Long::class.javaPrimitiveType!!, String::class.java, List::class.java)
        constructor.isAccessible = true

        val noteType = constructor.newInstance(44L, null, null)

        assertEquals(44L, fieldValue(noteType, "modelId") as Long)
        assertEquals("", fieldValue(noteType, "name") as String)
        assertTrue((fieldValue(noteType, "fields") as List<*>).isEmpty())
    }

    @Test
    fun noteTypeConstructorCopiesNonNullValues() {
        val noteTypeClass = Class.forName("${AnkiDroidGateway::class.java.name}\$NoteType")
        val constructor = noteTypeClass.getDeclaredConstructor(Long::class.javaPrimitiveType!!, String::class.java, List::class.java)
        constructor.isAccessible = true

        val noteType = constructor.newInstance(45L, "Kiku", listOf("Expression", "Meaning"))

        assertEquals(45L, fieldValue(noteType, "modelId") as Long)
        assertEquals("Kiku", fieldValue(noteType, "name") as String)
        assertEquals(listOf("Expression", "Meaning"), fieldValue(noteType, "fields"))
    }

    @Test
    fun cardProgressReportingCoversSmallTotalsAndBoundaryValues() {
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(-1, 500))
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(8, 100))
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(25, 100))
        assertFalse(AnkiDroidCardReader.shouldReportCardProgress(49, 1500))
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(100, 1500))
    }

    @Test
    fun cardProgressReporterOnlyEmitsThrottledScanEvents() {
        val events = mutableListOf<SyncProgress>()

        AnkiDroidCardReader.reportCardProgressIfNeeded(SyncProgress.Listener { events.add(it) }, 11, 500)
        assertTrue(events.isEmpty())

        AnkiDroidCardReader.reportCardProgressIfNeeded(SyncProgress.Listener { events.add(it) }, 20, 500)
        assertEquals(1, events.size)
        assertEquals(SyncProgress.Stage.SCANNING_CARDS, events[0].stage)
        assertEquals(20, events[0].scannedCards)
        assertEquals(500, events[0].totalCards)
    }

    @Test
    fun readCardsForNoteReturnsEmptyResultWhenStartProjectionIsExhausted() {
        val reader = AnkiDroidCardReader(null)

        val result = reader.readCardsForNote(
            "authority",
            1L,
            emptySet(),
            arrayOf(arrayOf<String>()),
            1,
        )

        assertTrue(result.cards().isEmpty())
        assertEquals(1, result.projectionIndex())
    }

    @Test
    fun splitFieldsPreservesBlankMiddleAndTrailingAnkiFields() {
        val fields = invokePrivateStatic(
            "splitFields",
            arrayOf(String::class.java),
            "箱\u001f\u001fbox\u001f",
        ) as List<String>

        assertEquals(listOf("箱", "", "box", ""), fields)
    }

    @Test
    fun uriForBuildsProviderUrisWithEncodedPathSegments() {
        val uri = invokePrivateStatic(
            "uriFor",
            arrayOf(String::class.java, Array<String>::class.java),
            "com.ichi2.anki.flashcards",
            arrayOf("notes", "deck name/with slash", "cards"),
        )

        assertEquals("content://com.ichi2.anki.flashcards/notes/deck%20name%2Fwith%20slash/cards", uri.toString())
    }

    @Test
    fun browserQueryMatchingReturnsOriginalListWhenQueryDisabled() {
        val cards = listOf(card(10L, 1L), card(20L, 2L))

        val result = invokePrivateStatic(
            "markBrowserQueryMatchedCards",
            arrayOf(List::class.java, Set::class.java),
            cards,
            emptySet<Long>(),
        ) as List<RecordsSyncModels.Card>

        assertEquals(cards, result)
        assertFalse(result.any { it.browserQueryMatched })
    }

    @Test
    fun validateTemplateCardsAcceptsOnlyOrdZeroCards() {
        val gateway = uninitializedGateway()

        invokePrivateInstance(
            gateway,
            "validateTemplateCards",
            arrayOf(List::class.java, RecordsSyncModels.Settings::class.java),
            listOf(card(10L, 1L, ord = 0)),
            RecordsSyncModels.Settings.kikuDefaults(),
        )
    }

    @Test
    fun validateTemplateCardsRejectsSecondaryTemplateOrdWithActionableMessage() {
        val gateway = uninitializedGateway()

        try {
            invokePrivateInstance(
                gateway,
                "validateTemplateCards",
                arrayOf(List::class.java, RecordsSyncModels.Settings::class.java),
                listOf(card(10L, 1L, ord = 1)),
                RecordsSyncModels.Settings.kikuDefaults(),
            )
            throw AssertionError("Expected secondary template cards to be rejected")
        } catch (error: InvocationTargetException) {
            val failure = error.targetException as AnkiDroidGateway.SyncFailure
            assertTrue(failure.permanentFailure)
            assertEquals(
                "Kiku has card template ord 1. This app supports only the first card template at ord 0.",
                failure.message,
            )
        }
    }

    @Test
    fun syncFailureFactoriesPreserveRetryabilityCauseAndNullableMessage() {
        val cause = IllegalStateException("provider busy")

        val retryable = AnkiDroidGateway.SyncFailure.retryable("retry later", cause)
        val permanent = AnkiDroidGateway.SyncFailure.permanent(null, cause)

        assertFalse(retryable.permanentFailure)
        assertEquals("retry later", retryable.message)
        assertSame(cause, retryable.cause)
        assertTrue(permanent.permanentFailure)
        assertNull(permanent.message)
        assertSame(cause, permanent.cause)
    }

    private fun card(cardId: Long, noteId: Long, ord: Int = 0): RecordsSyncModels.Card {
        return RecordsSyncModels.Card(cardId, noteId, ord, "deck", 0, 0, 0, 0, 0, 0, false)
    }

    private fun fsrsFromCursor(cursor: Cursor): Any {
        return requireNotNull(
            invokePrivateStatic(
                AnkiDroidCardReader::class.java,
                "fsrsMemoryState",
                arrayOf(Cursor::class.java),
                cursor,
            ),
        )
    }

    private fun invokePrivateStatic(
        name: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any?,
    ): Any? = invokePrivateStatic(AnkiDroidGateway::class.java, name, parameterTypes, *args)

    private fun invokePrivateStatic(
        targetClass: Class<*>,
        name: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any?,
    ): Any? {
        val method = targetClass.getDeclaredMethod(name, *parameterTypes)
        method.isAccessible = true
        return method.invoke(null, *args)
    }

    private fun invokePrivateInstance(
        target: Any,
        name: String,
        parameterTypes: Array<Class<*>>,
        vararg args: Any?,
    ): Any? {
        val method = target.javaClass.getDeclaredMethod(name, *parameterTypes)
        method.isAccessible = true
        return method.invoke(target, *args)
    }

    private fun uninitializedGateway(): Any {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe")
        field.isAccessible = true
        val unsafe = field.get(null)
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return requireNotNull(allocateInstance.invoke(unsafe, AnkiDroidGateway::class.java))
    }

    private fun fieldValue(target: Any, name: String): Any? {
        val field = target.javaClass.getDeclaredField(name)
        field.isAccessible = true
        return field.get(target)
    }

    private fun row(vararg values: String?): Map<String, String?> {
        check(values.size % 2 == 0) { "row values must come in key/value pairs" }
        val row = linkedMapOf<String, String?>()
        var index = 0
        while (index < values.size) {
            row[requireNotNull(values[index])] = values[index + 1]
            index += 2
        }
        return row
    }

    private fun cursor(row: Map<String, String?>): Cursor {
        val columns = row.keys.toList()
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getColumnIndex" -> columns.indexOf(cursorColumn(args))
                "isNull" -> row[columns[cursorIndex(args)]] == null
                "getString" -> row[columns[cursorIndex(args)]]
                "getLong" -> row[columns[cursorIndex(args)]]!!.toLong()
                "getInt" -> row[columns[cursorIndex(args)]]!!.toInt()
                "close" -> null
                else -> throw UnsupportedOperationException(method.name)
            }
        }
        val classLoader = Cursor::class.java.classLoader ?: AnkiDroidGatewayTest::class.java.classLoader
        return Proxy.newProxyInstance(classLoader, arrayOf(Cursor::class.java), handler) as Cursor
    }

    private fun cursorWithStringNullButNotSqlNull(column: String): Cursor {
        val handler = InvocationHandler { _, method, args ->
            when (method.name) {
                "getColumnIndex" -> if (cursorColumn(args) == column) 0 else -1
                "isNull" -> false
                "getString" -> null
                "close" -> null
                else -> throw UnsupportedOperationException(method.name)
            }
        }
        val classLoader = Cursor::class.java.classLoader ?: AnkiDroidGatewayTest::class.java.classLoader
        return Proxy.newProxyInstance(classLoader, arrayOf(Cursor::class.java), handler) as Cursor
    }

    private fun cursorIndex(args: Array<out Any?>?): Int = requireNotNull(args)[0] as Int

    private fun cursorColumn(args: Array<out Any?>?): String = requireNotNull(args)[0] as String
}
