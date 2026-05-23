package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public final class StudyReviewActionsTest {
    @Test
    public void saveAppliedReviewPersistsItemReviewOutcomeAndPassMarkerInOrder() {
        RecordsStudyModels.StudyItem before = item("語", 1);
        RecordsStudyModels.StudyItem after = item("語", 2);
        RecordsSchedulerModels.ReviewRequest request = request("語", MainActivityBase.RATING_GOOD);
        RecordsSchedulerModels.ReviewResult result = new RecordsSchedulerModels.ReviewResult(after, MainActivityBase.RATING_GOOD, false, "ok");
        List<String> events = new ArrayList<>();
        RecordingReviewWriter writer = new RecordingReviewWriter(events);
        RecordingOutcomeRecorder recorder = new RecordingOutcomeRecorder(events);
        AtomicReference<String> passedKanji = new AtomicReference<>();

        StudyReviewActions.saveAppliedReview(
                request,
                result,
                before,
                123L,
                writer,
                recorder,
                kanji -> {
                    events.add("markPassed");
                    passedKanji.set(kanji);
                }
        );

        assertEquals(List.of("saveItem", "saveReview", "recordOutcome", "markPassed"), events);
        assertSame(after, writer.savedItem);
        assertSame(request, writer.savedRequest);
        assertEquals(MainActivityBase.RATING_GOOD, writer.savedRating);
        assertEquals(123L, writer.reviewedAt);
        assertSame(before, writer.beforeReview);
        assertSame(after, writer.afterReview);
        assertEquals("語", recorder.kanji);
        assertEquals(MainActivityBase.RATING_GOOD, recorder.appliedRating);
        assertSame(before, recorder.beforeReview);
        assertSame(after, recorder.afterReview);
        assertEquals("語", passedKanji.get());
    }

    @Test
    public void saveAppliedReviewDoesNotMarkAgainAsPassed() {
        RecordsStudyModels.StudyItem before = item("語", 1);
        RecordsStudyModels.StudyItem after = item("語", 2);
        RecordsSchedulerModels.ReviewRequest request = request("語", MainActivityBase.RATING_AGAIN);
        RecordsSchedulerModels.ReviewResult result = new RecordsSchedulerModels.ReviewResult(after, MainActivityBase.RATING_AGAIN, false, "again");
        List<String> events = new ArrayList<>();
        RecordingReviewWriter writer = new RecordingReviewWriter(events);
        RecordingOutcomeRecorder recorder = new RecordingOutcomeRecorder(events);
        AtomicReference<String> passedKanji = new AtomicReference<>();

        StudyReviewActions.saveAppliedReview(
                request,
                result,
                before,
                123L,
                writer,
                recorder,
                kanji -> {
                    events.add("markPassed");
                    passedKanji.set(kanji);
                }
        );

        assertEquals(List.of("saveItem", "saveReview", "recordOutcome"), events);
        assertNull(passedKanji.get());
    }

    @Test
    public void saveTunedSchedulerWritesOnlyChangedAdjustmentState() {
        RecordsSchedulerModels.SchedulerParameters original = new RecordsSchedulerModels.SchedulerParameters(0.90, 0.45, 1.2, 2.0, 3.1, 100L, 10);
        RecordsSchedulerModels.SchedulerParameters same = new RecordsSchedulerModels.SchedulerParameters(0.95, 0.45, 1.2, 2.0, 3.1, 100L, 10);
        RecordsSchedulerModels.SchedulerParameters changed = new RecordsSchedulerModels.SchedulerParameters(0.95, 0.45, 1.2, 2.0, 3.1, 200L, 11);
        AtomicReference<RecordsSchedulerModels.SchedulerParameters> saved = new AtomicReference<>();

        StudyReviewActions.saveTunedSchedulerIfChanged(original, same, saved::set);
        assertNull(saved.get());

        StudyReviewActions.saveTunedSchedulerIfChanged(original, changed, saved::set);
        assertSame(changed, saved.get());
    }

    private static RecordsSchedulerModels.ReviewRequest request(String kanji, String rating) {
        return new RecordsSchedulerModels.ReviewRequest(kanji, "token", rating, false, true, false, 0);
    }

    private static RecordsStudyModels.StudyItem item(String kanji, int totalReviews) {
        return new RecordsStudyModels.StudyItem(kanji, "review", 1000L, 1.0, 2.0, totalReviews, 0, 0, 0, "", 1000L);
    }

    private static final class RecordingReviewWriter implements StudyReviewActions.ReviewWriter {
        private final List<String> events;
        private RecordsStudyModels.StudyItem savedItem;
        private RecordsSchedulerModels.ReviewRequest savedRequest;
        private String savedRating;
        private long reviewedAt;
        private RecordsStudyModels.StudyItem beforeReview;
        private RecordsStudyModels.StudyItem afterReview;

        private RecordingReviewWriter(List<String> events) {
            this.events = events;
        }

        @Override
        public void saveStudyItem(RecordsStudyModels.StudyItem item) {
            events.add("saveItem");
            savedItem = item;
        }

        @Override
        public void saveReview(
                RecordsSchedulerModels.ReviewRequest request,
                String appliedRating,
                long reviewedAt,
                RecordsStudyModels.StudyItem beforeReview,
                RecordsStudyModels.StudyItem afterReview
        ) {
            events.add("saveReview");
            savedRequest = request;
            savedRating = appliedRating;
            this.reviewedAt = reviewedAt;
            this.beforeReview = beforeReview;
            this.afterReview = afterReview;
        }
    }

    private static final class RecordingOutcomeRecorder implements StudyReviewActions.ReviewOutcomeRecorder {
        private final List<String> events;
        private String kanji;
        private String appliedRating;
        private RecordsStudyModels.StudyItem beforeReview;
        private RecordsStudyModels.StudyItem afterReview;

        private RecordingOutcomeRecorder(List<String> events) {
            this.events = events;
        }

        @Override
        public void recordReviewOutcome(
                String kanji,
                String appliedRating,
                RecordsStudyModels.StudyItem beforeReview,
                RecordsStudyModels.StudyItem afterReview
        ) {
            events.add("recordOutcome");
            this.kanji = kanji;
            this.appliedRating = appliedRating;
            this.beforeReview = beforeReview;
            this.afterReview = afterReview;
        }
    }
}
