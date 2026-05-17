package dev.bee.kanjianki.core;

import org.junit.Test;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CoreSchedulerImportEdgeTest {
    @Test
    public void extraNewCardsOnlyReopensRetiredRowsBelowMatureSupportThreshold() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsSyncModels.Settings settings = settingsWithMatureSupport(2);
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(
                row("裂", 1),
                row("語", 2),
                row("新", 0)
        );
        List<RecordsStudyModels.StudyItem> existing = Arrays.asList(
                retiredItem("裂"),
                retiredItem("語")
        );

        BridgeScheduler.ExtraNewCardsResult result = scheduler.seedExtraNewCards(
                rows,
                existing,
                settings,
                1000L,
                0L,
                5
        );

        assertEquals(2, result.availableCount);
        assertEquals(Arrays.asList("裂", "新"), result.admittedKanji);
        assertEquals("new", studyItem(result.items, "裂").state);
        assertEquals("retired", studyItem(result.items, "語").state);
        assertEquals("new", studyItem(result.items, "新").state);
    }

    @Test
    public void activeQueueFiltersRetiredSuppressedAndDisallowedItems() {
        BridgeScheduler scheduler = new BridgeScheduler();
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(
                row("裂", 0),
                row("語", 0),
                row("退", 0),
                row("外", 0)
        );
        List<RecordsStudyModels.StudyItem> items = Arrays.asList(
                reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING),
                reviewItem("語", RecordsBase.LadderRung.KANJI_MEANING).withSuppression(BridgeScheduler.TASK_FONT_MEANING, 1000L, 21),
                retiredItem("退"),
                reviewItem("外", RecordsBase.LadderRung.KANJI_MEANING)
        );

        List<RecordsStudyModels.StudyItem> unrestricted = scheduler.activeQueueItems(items, rows, 2000L, null);
        assertEquals(Arrays.asList("外", "裂"), sortedKanji(unrestricted));

        List<RecordsStudyModels.StudyItem> restricted = scheduler.activeQueueItems(items, rows, 2000L, Collections.singleton("裂"));
        assertEquals(Collections.singletonList("裂"), sortedKanji(restricted));
    }

    @Test
    public void suppressionRequiresDominatingMatureReviewedSibling() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem lower = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING);

        assertFalse(suppressedAfter(scheduler, lower, reviewItem("裂", RecordsBase.LadderRung.FONT_MEANING)
                .copyBuilder()
                .matureIntervalDays(20)
                .totalReviews(12)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build()));
        assertFalse(suppressedAfter(scheduler, lower, reviewItem("裂", RecordsBase.LadderRung.FONT_MEANING)
                .copyBuilder()
                .matureIntervalDays(21)
                .totalReviews(0)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build()));
        assertFalse(suppressedAfter(scheduler, lower, reviewItem("裂", RecordsBase.LadderRung.FONT_MEANING)
                .copyBuilder()
                .matureIntervalDays(21)
                .totalReviews(12)
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .build()));
        assertTrue(suppressedAfter(scheduler, lower, matureReview("裂", RecordsBase.LadderRung.FONT_MEANING)));
    }

    @Test
    public void wordReadingDominatesRecognitionRungsButNotTypeMeaning() {
        BridgeScheduler scheduler = new BridgeScheduler();
        RecordsStudyModels.StudyItem word = matureReview("裂", RecordsBase.LadderRung.WORD_READING);
        RecordsStudyModels.StudyItem font = reviewItem("裂", RecordsBase.LadderRung.FONT_MEANING);
        RecordsStudyModels.StudyItem kanji = reviewItem("裂", RecordsBase.LadderRung.KANJI_MEANING);
        RecordsStudyModels.StudyItem typed = reviewItem("裂", RecordsBase.LadderRung.TYPE_MEANING);

        List<RecordsStudyModels.StudyItem> result = scheduler.applySuppression(Arrays.asList(word, font, kanji, typed));

        assertEquals(BridgeScheduler.TASK_WORD_READING, itemAtRung(result, RecordsBase.LadderRung.FONT_MEANING).suppressedByTaskType);
        assertEquals(BridgeScheduler.TASK_WORD_READING, itemAtRung(result, RecordsBase.LadderRung.KANJI_MEANING).suppressedByTaskType);
        assertEquals("", itemAtRung(result, RecordsBase.LadderRung.TYPE_MEANING).suppressedByTaskType);
    }

    @Test
    public void importSelectorRequiresActiveCardsToBeUnsuspendedAndBrowserCardsToMatchQuery() throws Exception {
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader("裂,1500\n問,1600\n"));
        KanjiImportSelector selector = new KanjiImportSelector(ranks, 100, 3000);

        RecordsSyncModels.CollectionSnapshot suspendedActiveSource = snapshot(
                Collections.singletonList(note(1L, "裂ける", "さける")),
                Collections.singletonList(card(10L, 1L, true))
        );
        assertTrue(selector.importFrom(suspendedActiveSource, settings(true, false, false, false, "")).isEmpty());

        RecordsSyncModels.CollectionSnapshot unmatchedBrowserSource = snapshot(
                Collections.singletonList(note(2L, "問題", "もんだい")),
                Collections.singletonList(card(20L, 2L, false).withBrowserQueryMatched(false))
        );
        assertTrue(selector.importFrom(unmatchedBrowserSource, settings(false, false, false, true, "tag:kani")).isEmpty());
    }

    private static boolean suppressedAfter(BridgeScheduler scheduler, RecordsStudyModels.StudyItem lower, RecordsStudyModels.StudyItem higher) {
        return !studyItem(scheduler.applySuppression(Arrays.asList(lower, higher)), lower.kanji, lower.rung)
                .suppressedByTaskType
                .isEmpty();
    }

    private static RecordsStudyModels.StudyItem studyItem(List<RecordsStudyModels.StudyItem> items, String kanji) {
        for (RecordsStudyModels.StudyItem item : items) {
            if (item.kanji.equals(kanji)) {
                return item;
            }
        }
        throw new AssertionError("Missing study item for " + kanji);
    }

    private static RecordsStudyModels.StudyItem studyItem(List<RecordsStudyModels.StudyItem> items, String kanji, RecordsBase.LadderRung rung) {
        for (RecordsStudyModels.StudyItem item : items) {
            if (item.kanji.equals(kanji) && item.rung == rung) {
                return item;
            }
        }
        throw new AssertionError("Missing study item for " + kanji + " / " + rung);
    }

    private static RecordsStudyModels.StudyItem itemAtRung(List<RecordsStudyModels.StudyItem> items, RecordsBase.LadderRung rung) {
        for (RecordsStudyModels.StudyItem item : items) {
            if (item.rung == rung) {
                return item;
            }
        }
        throw new AssertionError("Missing rung " + rung);
    }

    private static List<String> sortedKanji(List<RecordsStudyModels.StudyItem> items) {
        List<String> out = new java.util.ArrayList<>();
        for (RecordsStudyModels.StudyItem item : items) {
            out.add(item.kanji);
        }
        Collections.sort(out);
        return out;
    }

    private static RecordsStudyModels.StudyItem retiredItem(String kanji) {
        return baseItem(kanji)
                .copyBuilder()
                .state("retired")
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build();
    }

    private static RecordsStudyModels.StudyItem reviewItem(String kanji, RecordsBase.LadderRung rung) {
        return baseItem(kanji)
                .copyBuilder()
                .state("review")
                .dueAtMillis(0L)
                .stability(1.0)
                .difficulty(5.0)
                .totalReviews(1)
                .rung(rung)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build();
    }

    private static RecordsStudyModels.StudyItem matureReview(String kanji, RecordsBase.LadderRung rung) {
        return reviewItem(kanji, rung)
                .copyBuilder()
                .matureIntervalDays(21)
                .totalReviews(12)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .build();
    }

    private static RecordsStudyModels.StudyItem baseItem(String kanji) {
        return new RecordsStudyModels.StudyItem(
                kanji,
                "new",
                0L,
                0.4,
                5.0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                false,
                "",
                0L
        );
    }

    private static RecordsImportModels.DashboardRow row(String kanji, int matureSupportCount) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                1000,
                "meaning",
                "reading",
                kanji,
                1,
                "reason",
                "reason",
                1,
                0,
                matureSupportCount,
                Collections.emptyList()
        );
    }

    private static RecordsSyncModels.Settings settingsWithMatureSupport(int matureSupportThreshold) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
                defaults.modelName,
                defaults.templateName,
                defaults.expressionField,
                defaults.readingField,
                defaults.meaningField,
                defaults.sentenceField,
                defaults.frequencyField,
                defaults.frequencySortField,
                defaults.matureDays,
                matureSupportThreshold,
                defaults.suspendedRankMin,
                defaults.suspendedRankMax,
                defaults.activeQueueCap,
                defaults.newPerDay,
                defaults.writingTriggerMissDays,
                defaults.recognitionPromotionPasses,
                defaults.realDueReviewsToMove
        );
    }

    private static RecordsSyncModels.CollectionSnapshot snapshot(List<RecordsSyncModels.Note> notes, List<RecordsSyncModels.Card> cards) {
        return new RecordsSyncModels.CollectionSnapshot(notes, cards);
    }

    private static RecordsSyncModels.Note note(long id, String expression, String reading) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(defaults.expressionField, expression);
        fields.put(defaults.readingField, reading);
        fields.put(defaults.meaningField, "meaning");
        fields.put(defaults.sentenceField, expression + " sentence");
        fields.put(defaults.frequencyField, "9999");
        fields.put(defaults.frequencySortField, "9999");
        return new RecordsSyncModels.Note(id, defaults.modelName, fields, Collections.emptyList());
    }

    private static RecordsSyncModels.Card card(long cardId, long noteId, boolean suspended) {
        return new RecordsSyncModels.Card(
                cardId,
                noteId,
                0,
                "Deck",
                suspended ? -1 : 2,
                suspended ? 3 : 2,
                0,
                suspended ? 0 : 30,
                3,
                0,
                suspended,
                null,
                null,
                null
        );
    }

    private static RecordsSyncModels.Settings settings(
            boolean active,
            boolean suspended,
            boolean weak,
            boolean browserQuery,
            String query
    ) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
                defaults.modelName,
                defaults.templateName,
                defaults.expressionField,
                defaults.readingField,
                defaults.meaningField,
                defaults.sentenceField,
                defaults.frequencyField,
                defaults.frequencySortField,
                defaults.matureDays,
                defaults.matureSupportThreshold,
                defaults.suspendedRankMin,
                defaults.suspendedRankMax,
                defaults.activeQueueCap,
                defaults.newPerDay,
                defaults.writingTriggerMissDays,
                defaults.recognitionPromotionPasses,
                defaults.realDueReviewsToMove,
                active,
                suspended,
                false,
                Collections.emptyList(),
                weak,
                defaults.importWeakFsrsDifficultyThreshold,
                defaults.importWeakLapsesThreshold,
                defaults.importMinMatchingCardsPerKanji,
                browserQuery,
                query
        );
    }
}
