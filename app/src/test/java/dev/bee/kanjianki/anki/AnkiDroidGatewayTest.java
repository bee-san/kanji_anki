package dev.bee.kanjianki.anki;

import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.sync.SyncProgress;

import org.junit.Test;

import android.database.Cursor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class AnkiDroidGatewayTest {
    @Test
    public void fsrsValuesAreReadFromCardCursorColumns() throws Exception {
        Object fsrs = fsrsFromCursor(cursor(row(
                "fsrs_stability", "12.5",
                "difficulty", "7.25",
                "fsrs_retrievability", "0.86"
        )));

        assertEquals(12.5, (Double) fieldValue(fsrs, "stability"), 0.0001);
        assertEquals(7.25, (Double) fieldValue(fsrs, "difficulty"), 0.0001);
        assertEquals(0.86, (Double) fieldValue(fsrs, "retrievability"), 0.0001);
    }

    @Test
    public void partialFsrsColumnsDoNotFallBackToSerializedData() throws Exception {
        Object difficultyOnly = fsrsFromCursor(cursor(row(
                "difficulty", "5.5",
                "data", "stability=9 retrievability=0.1"
        )));
        Object retrievabilityOnly = fsrsFromCursor(cursor(row(
                "retrievability", "0.33",
                "data", "stability=9 difficulty=8"
        )));

        assertNull(fieldValue(difficultyOnly, "stability"));
        assertEquals(5.5, (Double) fieldValue(difficultyOnly, "difficulty"), 0.0001);
        assertNull(fieldValue(difficultyOnly, "retrievability"));
        assertNull(fieldValue(retrievabilityOnly, "stability"));
        assertNull(fieldValue(retrievabilityOnly, "difficulty"));
        assertEquals(0.33, (Double) fieldValue(retrievabilityOnly, "retrievability"), 0.0001);
    }

    @Test
    public void fsrsCursorParsingIgnoresInvalidValuesAndUsesFiniteDataKeys() throws Exception {
        Object fsrs = fsrsFromCursor(cursor(row(
                "fsrs_stability", "NaN",
                "fsrs_difficulty", "Infinity",
                "data", "stability=bad difficulty=6.5 retrievability=Infinity s=3.0"
        )));

        assertEquals(3.0, (Double) fieldValue(fsrs, "stability"), 0.0001);
        assertEquals(6.5, (Double) fieldValue(fsrs, "difficulty"), 0.0001);
        assertNull(fieldValue(fsrs, "retrievability"));
    }

    @Test
    public void blankFsrsCursorDataProducesEmptyMemoryState() throws Exception {
        Object fsrs = fsrsFromCursor(cursor(row("data", "   ")));

        assertNull(fieldValue(fsrs, "stability"));
        assertNull(fieldValue(fsrs, "difficulty"));
        assertNull(fieldValue(fsrs, "retrievability"));
    }

    @Test
    public void nullFsrsCursorDataProducesEmptyMemoryState() throws Exception {
        Object fsrs = fsrsFromCursor(cursorWithStringNullButNotSqlNull("data"));

        assertNull(fieldValue(fsrs, "stability"));
        assertNull(fieldValue(fsrs, "difficulty"));
        assertNull(fieldValue(fsrs, "retrievability"));
    }

    @Test
    public void fsrsParserUsesLegacyColumnsBeforeDataFallback() throws Exception {
        Object fsrs = fsrsFromCursor(cursor(row(
                "stability", "1.25e1",
                "difficulty", "4.5",
                "retrievability", "0.91",
                "data", "s=3 d=8 r=0.1"
        )));

        assertEquals(12.5, (Double) fieldValue(fsrs, "stability"), 0.0001);
        assertEquals(4.5, (Double) fieldValue(fsrs, "difficulty"), 0.0001);
        assertEquals(0.91, (Double) fieldValue(fsrs, "retrievability"), 0.0001);
    }

    @Test
    public void fsrsParserUsesLegacyColumnsWhenFsrsColumnsAreNull() throws Exception {
        Object fsrs = fsrsFromCursor(cursor(row(
                "fsrs_stability", null,
                "stability", "2.0",
                "fsrs_difficulty", null,
                "difficulty", "3.0",
                "fsrs_retrievability", null,
                "retrievability", "0.55",
                "data", "stability=9 difficulty=8 retrievability=0.1"
        )));

        assertEquals(2.0, (Double) fieldValue(fsrs, "stability"), 0.0001);
        assertEquals(3.0, (Double) fieldValue(fsrs, "difficulty"), 0.0001);
        assertEquals(0.55, (Double) fieldValue(fsrs, "retrievability"), 0.0001);
    }

    @Test
    public void fsrsDataParserAcceptsQuotedAliasesAndLastFiniteValueWins() throws Exception {
        Object fsrs = fsrsFromCursor(cursor(row(
                "data", "'s':\"2.5\" \"difficulty\"=4.25 retrievability=bad r=0.76 s=7.5"
        )));

        assertEquals(7.5, (Double) fieldValue(fsrs, "stability"), 0.0001);
        assertEquals(4.25, (Double) fieldValue(fsrs, "difficulty"), 0.0001);
        assertEquals(0.76, (Double) fieldValue(fsrs, "retrievability"), 0.0001);
    }

    @Test
    public void fsrsDataParserAcceptsFullKeyNames() throws Exception {
        Object fsrs = fsrsFromCursor(cursor(row(
                "data", "stability=2.2 difficulty=3.3 retrievability=0.44"
        )));

        assertEquals(2.2, (Double) fieldValue(fsrs, "stability"), 0.0001);
        assertEquals(3.3, (Double) fieldValue(fsrs, "difficulty"), 0.0001);
        assertEquals(0.44, (Double) fieldValue(fsrs, "retrievability"), 0.0001);
    }

    @Test
    public void cardProgressReportingIsThrottledForLargeSyncs() throws Exception {
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(0, 500));
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(1, 500));
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(10, 500));
        assertFalse(AnkiDroidCardReader.shouldReportCardProgress(11, 500));
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(20, 500));
        assertFalse(AnkiDroidCardReader.shouldReportCardProgress(25, 500));
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(500, 500));
    }

    @Test
    public void cardProgressReportingUsesWiderStepsForVeryLargeSyncs() throws Exception {
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(10, 1500));
        assertFalse(AnkiDroidCardReader.shouldReportCardProgress(20, 1500));
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(50, 1500));
        assertFalse(AnkiDroidCardReader.shouldReportCardProgress(75, 1500));
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(1500, 1500));
    }

    @Test
    public void browserQueryMatchedCardsOnlyCopiesMatchingNotes() throws Exception {
        RecordsSyncModels.Card matched = card(10L, 1L);
        RecordsSyncModels.Card unchanged = card(20L, 2L);

        @SuppressWarnings("unchecked")
        List<RecordsSyncModels.Card> cards = (List<RecordsSyncModels.Card>) invokePrivateStatic(
                "markBrowserQueryMatchedCards",
                new Class<?>[]{List.class, Set.class},
                Arrays.asList(matched, unchanged),
                Collections.singleton(1L)
        );

        assertTrue(cards.get(0).browserQueryMatched);
        assertFalse(cards.get(1).browserQueryMatched);
        assertSame(unchanged, cards.get(1));
    }

    @Test
    public void cardsWithNotesFiltersCardsWhoseNotesWereSkipped() throws Exception {
        Object gateway = uninitializedGateway();
        RecordsSyncModels.Card kept = card(10L, 1L);
        RecordsSyncModels.Card orphan = card(20L, 2L);

        @SuppressWarnings("unchecked")
        List<RecordsSyncModels.Card> cards = (List<RecordsSyncModels.Card>) invokePrivateInstance(
                gateway,
                "cardsWithNotes",
                new Class<?>[]{List.class, Set.class},
                Arrays.asList(kept, orphan),
                Collections.singleton(1L)
        );

        assertEquals(1, cards.size());
        assertSame(kept, cards.get(0));
    }

    @Test
    public void splitTagsTrimsTokensAndDropsWhitespaceOnlyValues() throws Exception {
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) invokePrivateStatic(
                "splitTags",
                new Class<?>[]{String.class},
                "  leech   kani_archived\tmarked  "
        );

        assertEquals(Arrays.asList("leech", "kani_archived", "marked"), tags);
    }

    @Test
    public void cursorValueHelpersUseFallbacksForMissingAndNullColumns() throws Exception {
        Cursor empty = cursor(row());
        Cursor nulls = cursor(row("text", null, "long", null, "int", null));

        assertEquals("", invokePrivateStatic("value", new Class<?>[]{Cursor.class, String.class}, empty, "missing"));
        assertEquals("", invokePrivateStatic("value", new Class<?>[]{Cursor.class, String.class}, nulls, "text"));
        assertEquals("present", invokePrivateStatic(
                "value",
                new Class<?>[]{Cursor.class, String.class},
                cursor(row("text", "present")),
                "text"
        ));
        assertEquals(42L, invokePrivateStatic("longValue", new Class<?>[]{Cursor.class, String.class, long.class}, empty, "missing", 42L));
        assertEquals(43L, invokePrivateStatic("longValue", new Class<?>[]{Cursor.class, String.class, long.class}, nulls, "long", 43L));
        assertEquals(99L, invokePrivateStatic(
                "longValue",
                new Class<?>[]{Cursor.class, String.class, long.class},
                cursor(row("long", "99")),
                "long",
                0L
        ));
    }

    @Test
    public void fsrsCursorTreatsNullStringAsMissingWhenCursorReportsValuePresent() throws Exception {
        Cursor cursor = cursorWithStringNullButNotSqlNull("fsrs_stability");
        Object fsrs = fsrsFromCursor(cursor);

        assertNull(fieldValue(fsrs, "stability"));
        assertNull(fieldValue(fsrs, "difficulty"));
        assertNull(fieldValue(fsrs, "retrievability"));
    }

    @Test
    public void noteTypeConstructorNormalizesNullNameAndFields() throws Exception {
        Class<?> noteTypeClass = Class.forName(AnkiDroidGateway.class.getName() + "$NoteType");
        java.lang.reflect.Constructor<?> constructor = noteTypeClass.getDeclaredConstructor(long.class, String.class, List.class);
        constructor.setAccessible(true);

        Object noteType = constructor.newInstance(44L, null, null);

        assertEquals(44L, fieldValue(noteType, "modelId"));
        assertEquals("", fieldValue(noteType, "name"));
        assertTrue(((List<?>) fieldValue(noteType, "fields")).isEmpty());
    }

    @Test
    public void noteTypeConstructorCopiesNonNullValues() throws Exception {
        Class<?> noteTypeClass = Class.forName(AnkiDroidGateway.class.getName() + "$NoteType");
        java.lang.reflect.Constructor<?> constructor = noteTypeClass.getDeclaredConstructor(long.class, String.class, List.class);
        constructor.setAccessible(true);

        Object noteType = constructor.newInstance(45L, "Kiku", Arrays.asList("Expression", "Meaning"));

        assertEquals(45L, fieldValue(noteType, "modelId"));
        assertEquals("Kiku", fieldValue(noteType, "name"));
        assertEquals(Arrays.asList("Expression", "Meaning"), fieldValue(noteType, "fields"));
    }

    @Test
    public void cardProgressReportingCoversSmallTotalsAndBoundaryValues() throws Exception {
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(-1, 500));
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(8, 100));
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(25, 100));
        assertFalse(AnkiDroidCardReader.shouldReportCardProgress(49, 1500));
        assertTrue(AnkiDroidCardReader.shouldReportCardProgress(100, 1500));
    }

    @Test
    public void cardProgressReporterOnlyEmitsThrottledScanEvents() throws Exception {
        List<SyncProgress> events = new java.util.ArrayList<>();

        AnkiDroidCardReader.reportCardProgressIfNeeded((SyncProgress.Listener) events::add, 11, 500);
        assertTrue(events.isEmpty());

        AnkiDroidCardReader.reportCardProgressIfNeeded((SyncProgress.Listener) events::add, 20, 500);
        assertEquals(1, events.size());
        assertEquals(SyncProgress.Stage.SCANNING_CARDS, events.get(0).stage);
        assertEquals(20, events.get(0).scannedCards);
        assertEquals(500, events.get(0).totalCards);
    }

    @Test
    public void readCardsForNoteReturnsEmptyResultWhenStartProjectionIsExhausted() throws Exception {
        AnkiDroidCardReader reader = new AnkiDroidCardReader(null);

        AnkiDroidCardReader.ProjectionReadResult result = reader.readCardsForNote(
                "authority",
                1L,
                Collections.emptySet(),
                new String[0][],
                0
        );

        assertTrue(result.cards().isEmpty());
        assertEquals(0, result.projectionIndex());
    }

    private static String repeat(String value, int count) {
        StringBuilder builder = new StringBuilder(value.length() * count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }

    private static RecordsSyncModels.Card card(long cardId, long noteId) {
        return new RecordsSyncModels.Card(cardId, noteId, 0, "deck", 0, 0, 0, 0, 0, 0, false);
    }

    private static Object fsrsFromCursor(Cursor cursor) throws Exception {
        return invokePrivateStatic(AnkiDroidCardReader.class, "fsrsMemoryState", new Class<?>[]{Cursor.class}, cursor);
    }

    private static Object invokePrivateStatic(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        return invokePrivateStatic(AnkiDroidGateway.class, name, parameterTypes, args);
    }

    private static Object invokePrivateStatic(Class<?> targetClass, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = targetClass.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(null, args);
    }

    private static Object invokePrivateInstance(Object target, String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = AnkiDroidGateway.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, args);
    }

    private static Object uninitializedGateway() throws Exception {
        Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        java.lang.reflect.Field field = unsafeClass.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        Object unsafe = field.get(null);
        Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return allocateInstance.invoke(unsafe, AnkiDroidGateway.class);
    }

    private static Object fieldValue(Object target, String name) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Map<String, String> row(String... values) {
        Map<String, String> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            row.put(values[i], values[i + 1]);
        }
        return row;
    }

    private static Cursor cursor(Map<String, String> row) {
        List<String> columns = List.copyOf(row.keySet());
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("getColumnIndex".equals(name)) {
                return columns.indexOf(args[0]);
            }
            if ("isNull".equals(name)) {
                return row.get(columns.get((Integer) args[0])) == null;
            }
            if ("getString".equals(name)) {
                return row.get(columns.get((Integer) args[0]));
            }
            if ("getLong".equals(name)) {
                return Long.parseLong(row.get(columns.get((Integer) args[0])));
            }
            if ("getInt".equals(name)) {
                return Integer.parseInt(row.get(columns.get((Integer) args[0])));
            }
            if ("close".equals(name)) {
                return null;
            }
            throw new UnsupportedOperationException(name);
        };
        return (Cursor) Proxy.newProxyInstance(
                Cursor.class.getClassLoader(),
                new Class<?>[]{Cursor.class},
                handler
        );
    }

    private static Cursor cursorWithStringNullButNotSqlNull(String column) {
        InvocationHandler handler = (proxy, method, args) -> {
            String name = method.getName();
            if ("getColumnIndex".equals(name)) {
                return column.equals(args[0]) ? 0 : -1;
            }
            if ("isNull".equals(name)) {
                return false;
            }
            if ("getString".equals(name)) {
                return null;
            }
            if ("close".equals(name)) {
                return null;
            }
            throw new UnsupportedOperationException(name);
        };
        return (Cursor) Proxy.newProxyInstance(
                Cursor.class.getClassLoader(),
                new Class<?>[]{Cursor.class},
                handler
        );
    }
}
