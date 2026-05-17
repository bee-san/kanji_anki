package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.domain.model.study.StudyItemState;
import dev.bee.kanjianki.domain.model.study.StudyPhase;
import dev.bee.kanjianki.domain.model.study.StudyQueueItem;
import dev.bee.kanjianki.domain.model.study.StudyRating;
import dev.bee.kanjianki.domain.model.study.StudyRung;
import dev.bee.kanjianki.domain.model.study.TaskMemory;
import dev.bee.kanjianki.domain.model.study.TaskMemoryBank;
import dev.bee.kanjianki.domain.scheduler.LearningStepSettings;
import dev.bee.kanjianki.domain.scheduler.StudyLadderSettings;
import dev.bee.kanjianki.domain.scheduler.StudyReviewRequest;
import dev.bee.kanjianki.domain.scheduler.StudyReviewTransitionEngine;
import dev.bee.kanjianki.domain.scheduler.StudyReviewTransitionInput;
import dev.bee.kanjianki.domain.scheduler.StudyReviewTransitionResult;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class LegacyStudyReviewBridge {
    private static final int LEGACY_TYPE_MEANING_STAGE = -1;
    private static final int LEGACY_FONT_MEANING_STAGE = 1;
    private static final int LEGACY_WORD_READING_STAGE = 2;

    private final StudyReviewTransitionEngine transitionEngine;

    LegacyStudyReviewBridge() {
        this(new StudyReviewTransitionEngine());
    }

    LegacyStudyReviewBridge(StudyReviewTransitionEngine transitionEngine) {
        this.transitionEngine = Objects.requireNonNull(transitionEngine);
    }

    RecordsSchedulerModels.ReviewResult applyReview(
            RecordsStudyModels.StudyItem item,
            RecordsSchedulerModels.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            RecordsSchedulerModels.SchedulerParameters parameters,
            RecordsSyncModels.Settings settings,
            RecordsBase.StudyLadderSettings ladder
    ) {
        StudyReviewTransitionResult result = transitionEngine.apply(
                new StudyReviewTransitionInput(
                        toDomain(item),
                        toDomain(request),
                        nowMillis,
                        consumedTokens == null ? new LinkedHashSet<>() : new LinkedHashSet<>(consumedTokens),
                        safeParameters(parameters).targetRetention,
                        toDomain(safeLearningSettings(null)),
                        toDomain(settings, ladder)
                )
        );
        if (!result.getDuplicate() && consumedTokens != null) {
            consumedTokens.add(request.token == null ? "" : request.token);
        }
        return new RecordsSchedulerModels.ReviewResult(
                result.getDuplicate() ? item : toLegacy(item, result.getItem()),
                result.getAppliedRating() == null ? "duplicate" : result.getAppliedRating().getWireName(),
                result.getDuplicate(),
                result.getMessage()
        );
    }

    RecordsSchedulerModels.ReviewResult applyReview(
            RecordsStudyModels.StudyItem item,
            RecordsSchedulerModels.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            RecordsSchedulerModels.SchedulerParameters parameters,
            RecordsSyncModels.Settings settings,
            RecordsSchedulerModels.LearningStepSettings learningSettings,
            RecordsBase.StudyLadderSettings ladder
    ) {
        StudyReviewTransitionResult result = transitionEngine.apply(
                new StudyReviewTransitionInput(
                        toDomain(item),
                        toDomain(request),
                        nowMillis,
                        consumedTokens == null ? new LinkedHashSet<>() : new LinkedHashSet<>(consumedTokens),
                        safeParameters(parameters).targetRetention,
                        toDomain(safeLearningSettings(learningSettings)),
                        toDomain(settings, ladder)
                )
        );
        if (!result.getDuplicate() && consumedTokens != null) {
            consumedTokens.add(request.token == null ? "" : request.token);
        }
        return new RecordsSchedulerModels.ReviewResult(
                result.getDuplicate() ? item : toLegacy(item, result.getItem()),
                result.getAppliedRating() == null ? "duplicate" : result.getAppliedRating().getWireName(),
                result.getDuplicate(),
                result.getMessage()
        );
    }

    private static RecordsSchedulerModels.SchedulerParameters safeParameters(
            RecordsSchedulerModels.SchedulerParameters parameters
    ) {
        return parameters == null ? RecordsSchedulerModels.SchedulerParameters.defaults() : parameters;
    }

    private static RecordsSchedulerModels.LearningStepSettings safeLearningSettings(
            RecordsSchedulerModels.LearningStepSettings settings
    ) {
        return settings == null ? RecordsSchedulerModels.LearningStepSettings.defaults() : settings;
    }

    private static StudyReviewRequest toDomain(RecordsSchedulerModels.ReviewRequest request) {
        return new StudyReviewRequest(
                request.kanji,
                rating(request.rating),
                request.token == null ? "" : request.token,
                request.writingRequired,
                request.writingPassed,
                request.writingClean,
                request.hintsUsed,
                request.manualOverride
        );
    }

    private static StudyQueueItem toDomain(RecordsStudyModels.StudyItem item) {
        return new StudyQueueItem(
                item.kanji,
                state(item.state),
                item.dueAtMillis,
                item.stability,
                item.difficulty,
                item.totalReviews,
                item.lapses,
                item.learningStep,
                item.writingLevel,
                item.matureIntervalDays,
                item.answerSignature,
                rung(item.rung),
                phase(item.phase),
                item.realPassStreak,
                item.realAgainStreak,
                item.lastRealReviewDueAtMillis,
                item.suppressedByTaskType,
                item.hasSimilarKanji,
                item.activeToken,
                new TaskMemoryBank(
                        toDomain(item.typingMeaningMemory),
                        toDomain(item.meaningKanjiMemory),
                        toDomain(item.kanjiMeaningMemory),
                        toDomain(item.fontMeaningMemory),
                        toDomain(item.wordReadingMemory),
                        toDomain(item.writingRemediationMemory),
                        toDomain(item.similarKanjiMemory)
                )
        );
    }

    private static RecordsStudyModels.StudyItem toLegacy(
            RecordsStudyModels.StudyItem original,
            StudyQueueItem item
    ) {
        return original.copyBuilder()
                .state(item.getState().getWireName())
                .dueAtMillis(item.getDueAtMillis())
                .stability(item.getStability())
                .difficulty(item.getDifficulty())
                .totalReviews(item.getTotalReviews())
                .lapses(item.getLapses())
                .learningStep(item.getLearningStep())
                .writingLevel(item.getWritingLevel())
                .recognitionStage(legacyRecognitionStage(item.getRung()))
                .consecutiveFailedRecognitionDays(item.getRealAgainStreak())
                .lastFailedRecognitionDayMillis(item.getLastRealReviewDueAtMillis())
                .writingRemediationPending(item.getRung() == StudyRung.WRITE_KANJI)
                .matureIntervalDays(item.getMatureIntervalDays())
                .activeToken(item.getActiveToken())
                .typingMeaningMemory(toLegacy(item.getMemories().getTypingMeaningMemory()))
                .meaningKanjiMemory(toLegacy(item.getMemories().getMeaningKanjiMemory()))
                .kanjiMeaningMemory(toLegacy(item.getMemories().getKanjiMeaningMemory()))
                .fontMeaningMemory(toLegacy(item.getMemories().getFontMeaningMemory()))
                .wordReadingMemory(toLegacy(item.getMemories().getWordReadingMemory()))
                .writingRemediationMemory(toLegacy(item.getMemories().getWritingRemediationMemory()))
                .rung(toLegacy(item.getRung()))
                .phase(toLegacy(item.getPhase()))
                .realPassStreak(item.getRealPassStreak())
                .realAgainStreak(item.getRealAgainStreak())
                .lastRealReviewDueAtMillis(item.getLastRealReviewDueAtMillis())
                .hasSimilarKanji(item.getHasSimilarKanji())
                .similarKanjiMemory(toLegacy(item.getMemories().getSimilarKanjiMemory()))
                .build();
    }

    private static LearningStepSettings toDomain(RecordsSchedulerModels.LearningStepSettings settings) {
        return new LearningStepSettings(
                new ArrayList<>(settings.newStepsMinutes),
                new ArrayList<>(settings.reviewStepsMinutes)
        );
    }

    private static StudyLadderSettings toDomain(
            RecordsSyncModels.Settings settings,
            RecordsBase.StudyLadderSettings ladder
    ) {
        RecordsSyncModels.Settings safeSettings = settings == null
                ? RecordsSyncModels.Settings.kikuDefaults()
                : settings;
        RecordsBase.StudyLadderSettings safeLadder = ladder == null
                ? RecordsBase.StudyLadderSettings.defaults()
                : ladder;
        List<StudyRung> order = new ArrayList<>();
        for (RecordsBase.LadderRung rung : safeLadder.orderedRungs) {
            order.add(rung(rung));
        }
        Set<StudyRung> enabled = new LinkedHashSet<>();
        for (RecordsBase.LadderRung rung : safeLadder.enabledRungs) {
            enabled.add(rung(rung));
        }
        return new StudyLadderSettings(
                order,
                enabled,
                safeSettings.ladderPromotionIntervalDays,
                safeSettings.ladderDemotionFailStreak
        );
    }

    private static TaskMemory toDomain(RecordsStudyModels.TaskMemory memory) {
        return new TaskMemory(
                memory.state,
                memory.dueAtMillis,
                memory.stability,
                memory.difficulty,
                memory.totalReviews,
                memory.lapses,
                memory.learningStep,
                memory.lastRating,
                memory.matureIntervalDays,
                memory.consecutivePasses,
                memory.lastPassedDueAtMillis
        );
    }

    private static RecordsStudyModels.TaskMemory toLegacy(TaskMemory memory) {
        return new RecordsStudyModels.TaskMemory(
                memory.getState(),
                memory.getDueAtMillis(),
                memory.getStability(),
                memory.getDifficulty(),
                memory.getTotalReviews(),
                memory.getLapses(),
                memory.getLearningStep(),
                memory.getLastRating(),
                memory.getMatureIntervalDays(),
                memory.getConsecutivePasses(),
                memory.getLastPassedDueAtMillis()
        );
    }

    private static StudyItemState state(String state) {
        if ("learning".equals(state)) {
            return StudyItemState.LEARNING;
        }
        if ("review".equals(state)) {
            return StudyItemState.REVIEW;
        }
        if ("retired".equals(state)) {
            return StudyItemState.RETIRED;
        }
        return StudyItemState.NEW;
    }

    private static StudyRating rating(String rating) {
        if ("hard".equals(rating)) {
            return StudyRating.HARD;
        }
        if ("good".equals(rating)) {
            return StudyRating.GOOD;
        }
        if ("easy".equals(rating)) {
            return StudyRating.EASY;
        }
        return StudyRating.AGAIN;
    }

    private static StudyRung rung(RecordsBase.LadderRung rung) {
        if (rung == null) {
            return StudyRung.KANJI_MEANING;
        }
        return switch (rung) {
            case WRITE_KANJI -> StudyRung.WRITE_KANJI;
            case TYPE_MEANING -> StudyRung.TYPE_MEANING;
            case SIMILAR_KANJI -> StudyRung.SIMILAR_KANJI;
            case MEANING_KANJI -> StudyRung.MEANING_KANJI;
            case FONT_MEANING -> StudyRung.FONT_MEANING;
            case WORD_READING -> StudyRung.WORD_READING;
            case KANJI_MEANING -> StudyRung.KANJI_MEANING;
        };
    }

    private static RecordsBase.LadderRung toLegacy(StudyRung rung) {
        return switch (rung) {
            case WRITE_KANJI -> RecordsBase.LadderRung.WRITE_KANJI;
            case TYPE_MEANING -> RecordsBase.LadderRung.TYPE_MEANING;
            case SIMILAR_KANJI -> RecordsBase.LadderRung.SIMILAR_KANJI;
            case MEANING_KANJI -> RecordsBase.LadderRung.MEANING_KANJI;
            case FONT_MEANING -> RecordsBase.LadderRung.FONT_MEANING;
            case WORD_READING -> RecordsBase.LadderRung.WORD_READING;
            case KANJI_MEANING -> RecordsBase.LadderRung.KANJI_MEANING;
        };
    }

    private static StudyPhase phase(RecordsBase.SchedulerPhase phase) {
        if (phase == RecordsBase.SchedulerPhase.REVIEW) {
            return StudyPhase.REVIEW;
        }
        if (phase == RecordsBase.SchedulerPhase.RELEARNING) {
            return StudyPhase.RELEARNING;
        }
        return StudyPhase.NEW_LEARNING;
    }

    private static RecordsBase.SchedulerPhase toLegacy(StudyPhase phase) {
        return switch (phase) {
            case REVIEW -> RecordsBase.SchedulerPhase.REVIEW;
            case RELEARNING -> RecordsBase.SchedulerPhase.RELEARNING;
            case NEW_LEARNING -> RecordsBase.SchedulerPhase.NEW_LEARNING;
        };
    }

    private static int legacyRecognitionStage(StudyRung rung) {
        return switch (rung) {
            case TYPE_MEANING -> LEGACY_TYPE_MEANING_STAGE;
            case FONT_MEANING -> LEGACY_FONT_MEANING_STAGE;
            case WORD_READING -> LEGACY_WORD_READING_STAGE;
            default -> 0;
        };
    }
}
