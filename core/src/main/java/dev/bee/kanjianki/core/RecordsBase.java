package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public abstract class RecordsBase {
    public static final int DEFAULT_WRITING_TRIGGER_MISS_DAYS = 3;
    public static final int DEFAULT_RECOGNITION_PROMOTION_PASSES = 3;
    public static final int DEFAULT_REAL_DUE_REVIEWS_TO_MOVE = 3;
    public static final int DEFAULT_SUSPENDED_RANK_MIN = 100;
    public static final int DEFAULT_SUSPENDED_RANK_MAX = 3000;
    public static final boolean DEFAULT_IMPORT_ACTIVE_CARDS = false;
    public static final boolean DEFAULT_IMPORT_SUSPENDED_CARDS = true;
    public static final boolean DEFAULT_IMPORT_TAGGED_CARDS = false;
    public static final boolean DEFAULT_IMPORT_WEAK_CARDS = false;
    public static final double DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY = 7.0;
    public static final int DEFAULT_IMPORT_WEAK_LAPSES = 2;
    public static final int DEFAULT_IMPORT_MIN_MATCHING_CARDS_PER_KANJI = 1;
    public static final boolean DEFAULT_IMPORT_BROWSER_QUERY_CARDS = false;
    public static final String DEFAULT_IMPORT_BROWSER_QUERY = "";
    public static final String LEARNING_REPEAT_NEW = "new";
    public static final String LEARNING_REPEAT_REVIEW = "review";
    public static final String SOURCE_ACTIVE = "active";
    public static final String SOURCE_SUSPENDED = "suspended";
    public static final String SOURCE_BROWSER_QUERY = "browser_query";
    protected static final Logger LOGGER = Logger.getLogger(Records.class.getName());
    protected static final Pattern TASK_MEMORY_SEPARATOR = Pattern.compile("\\t");
    protected static final Pattern IMPORT_TAG_SEPARATOR = Pattern.compile("[,\\s]+");

    /**
     * Ladder rungs that a study item can be on, from lowest to highest.
     * New cards start on {@link #KANJI_MEANING}. The {@link #SIMILAR_KANJI}
     * rung is included in the ladder only when {@code hasSimilarKanji} is
     * true for the card; otherwise promotion and demotion skip over it.
     */
    public enum LadderRung {
        WRITE_KANJI("write_kanji"),
        TYPE_MEANING("type_meaning"),
        SIMILAR_KANJI("similar_kanji"),
        KANJI_MEANING("kanji_meaning"),
        FONT_MEANING("font_meaning"),
        WORD_READING("word_reading");

        final String wireName;

        LadderRung(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static LadderRung startingRung() {
            return KANJI_MEANING;
        }

        public static LadderRung fromWireName(String name) {
            if (name == null) {
                return KANJI_MEANING;
            }
            for (LadderRung rung : values()) {
                if (rung.wireName.equals(name)) {
                    return rung;
                }
            }
            LOGGER.warning(() -> "LadderRung.fromWireName: unknown wire name '" + name + "', defaulting to KANJI_MEANING");
            return KANJI_MEANING;
        }
    }

    /**
     * Phase of the card within Anki learning/relearning/review semantics.
     * Learning and relearning repeats are practice-only and must not count
     * toward ladder promotion or demotion; only {@link #REVIEW}-phase
     * answers on a due card advance the ladder streaks.
     */
    public enum SchedulerPhase {
        NEW_LEARNING("new_learning"),
        REVIEW(LEARNING_REPEAT_REVIEW),
        RELEARNING("relearning");

        final String wireName;

        SchedulerPhase(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static SchedulerPhase fromWireName(String name) {
            if (name == null) {
                return NEW_LEARNING;
            }
            for (SchedulerPhase phase : values()) {
                if (phase.wireName.equals(name)) {
                    return phase;
                }
            }
            LOGGER.warning(() -> "SchedulerPhase.fromWireName: unknown wire name '" + name + "', defaulting to NEW_LEARNING");
            return NEW_LEARNING;
        }
    }
    protected static final String CONTEXT_SETTINGS = "Settings";
    protected static final String CONTEXT_CARD = "Card";
    protected static final String CONTEXT_EXAMPLE = "Example";
    protected static final String CONTEXT_DASHBOARD_ROW = "DashboardRow";
    protected static final String CONTEXT_KANJI_INVENTORY_ITEM = "KanjiInventoryItem";
    protected static final String CONTEXT_SIMILAR_KANJI_CHOICE_CARD = "SimilarKanjiChoiceCard";
    protected static final String CONTEXT_SIMILAR_KANJI_WRITING_REPAIR = "SimilarKanjiWritingRepair";
    protected static final String CONTEXT_KANJI_TIMELINE_EVENT = "KanjiTimelineEvent";
    protected static final String CONTEXT_TASK_MEMORY = "TaskMemory";
    protected static final String CONTEXT_TASK_MEMORY_FROM_STUDY_FIELDS = "TaskMemory.fromStudyFields";
    protected static final String CONTEXT_STUDY_ITEM = "StudyItem";
    protected static final String CONTEXT_LEARNING_REPEAT = "LearningRepeat";
    protected static final String CONTEXT_REVIEW_REQUEST = "ReviewRequest";
    protected static final String CONTEXT_ADAPTIVE_LOAD_PLAN = "AdaptiveLoadPlan";

    RecordsBase() {
    }

    protected static Object arg(Object[] args, int index, String context) {
        if (index >= args.length) {
            throw new IllegalArgumentException(context + " expected more arguments");
        }
        return args[index];
    }

    protected static void requireArgCount(String context, Object[] args, int... expectedCounts) {
        for (int expected : expectedCounts) {
            if (args.length == expected) {
                return;
            }
        }
        throw new IllegalArgumentException(context + " received " + args.length + " trailing arguments");
    }

    protected static String stringArg(Object[] args, int index, String context) {
        return (String) arg(args, index, context);
    }

    protected static String nullToEmpty(String value) {
        return Objects.requireNonNullElse(value, "");
    }

    protected static <T> List<T> nullToEmptyList(List<T> value) {
        return Objects.requireNonNullElse(value, Collections.emptyList());
    }

    protected static int intArg(Object[] args, int index, String context) {
        Object value = arg(args, index, context);
        return ((Number) value).intValue();
    }

    protected static long longArg(Object[] args, int index, String context) {
        Object value = arg(args, index, context);
        return ((Number) value).longValue();
    }

    protected static boolean booleanArg(Object[] args, int index, String context) {
        return (Boolean) arg(args, index, context);
    }

    protected static Double nullableDoubleArg(Object[] args, int index, String context) {
        Object value = arg(args, index, context);
        if (value == null) {
            return null;
        }
        return ((Number) value).doubleValue();
    }

    public static List<String> parseImportTags(String value) {
        if (value == null || value.trim().isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> parsed = new LinkedHashSet<>();
        for (String part : IMPORT_TAG_SEPARATOR.split(value.trim())) {
            parsed.add(part.trim());
        }
        return new ArrayList<>(parsed);
    }
}
