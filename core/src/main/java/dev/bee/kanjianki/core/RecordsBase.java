package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public abstract class RecordsBase {
    public static final int DEFAULT_WRITING_TRIGGER_MISS_DAYS = 3;
    public static final int DEFAULT_RECOGNITION_PROMOTION_PASSES = 3;
    public static final int DEFAULT_REAL_DUE_REVIEWS_TO_MOVE = 3;
    public static final int DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS = 21;
    public static final int DEFAULT_LADDER_DEMOTION_FAIL_STREAK = 3;
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
    public static final String NEW_CARD_SORT_FREQUENCY = "frequency";
    public static final String NEW_CARD_SORT_FSRS_DIFFICULTY = "fsrs_difficulty";
    public static final String NEW_CARD_SORT_RETRIEVABILITY_RISK = "retrievability_risk";
    public static final String NEW_CARD_SORT_KANI_WEAKNESS = "kani_weakness";
    public static final String DEFAULT_NEW_CARD_SORT_MODE = NEW_CARD_SORT_FREQUENCY;
    public static final boolean DEFAULT_FREQUENCY_RETENTION_ENABLED = false;
    public static final String DEFAULT_FREQUENCY_RETENTION_RANGES = "";
    public static final String LEARNING_REPEAT_NEW = "new";
    public static final String LEARNING_REPEAT_REVIEW = "review";
    public static final String SOURCE_ACTIVE = "active";
    public static final String SOURCE_SUSPENDED = "suspended";
    public static final String SOURCE_TAGGED = "tagged";
    public static final String SOURCE_WEAK = "weak";
    public static final String SOURCE_BROWSER_QUERY = "browser_query";
    protected static final Logger LOGGER = Logger.getLogger(RecordsBase.class.getName());
    protected static final Pattern TASK_MEMORY_SEPARATOR = Pattern.compile("\\t");
    protected static final Pattern IMPORT_TAG_SEPARATOR = Pattern.compile("[,\\s]+");

    /**
     * Ladder rungs that a study item can be on. User settings own the active
     * low-to-high order; enum order is retained for storage compatibility.
     * New cards start near {@link #KANJI_MEANING}. The {@link #SIMILAR_KANJI}
     * rung is included only when {@code hasSimilarKanji} is true for the card.
     */
    public enum LadderRung {
        WRITE_KANJI("write_kanji"),
        TYPE_MEANING("type_meaning"),
        SIMILAR_KANJI("similar_kanji"),
        MEANING_KANJI("meaning_kanji"),
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

    public static final class StudyLadderSettings {
        public final List<LadderRung> orderedRungs;
        public final List<LadderRung> enabledRungs;

        public StudyLadderSettings(List<LadderRung> orderedRungs, List<LadderRung> enabledRungs) {
            this(orderedRungs, enabledRungs, false);
        }

        private StudyLadderSettings(List<LadderRung> orderedRungs, List<LadderRung> enabledRungs, boolean fallbackOnInvalid) {
            List<LadderRung> normalizedOrder = normalizeOrder(orderedRungs);
            List<LadderRung> normalizedEnabled = normalizeEnabled(enabledRungs, normalizedOrder);
            if (fallbackOnInvalid && !hasAlwaysAvailableRung(normalizedEnabled)) {
                StudyLadderSettings defaults = defaults();
                this.orderedRungs = defaults.orderedRungs;
                this.enabledRungs = defaults.enabledRungs;
                return;
            }
            if (!hasAlwaysAvailableRung(normalizedEnabled)) {
                normalizedEnabled.add(LadderRung.KANJI_MEANING);
            }
            this.orderedRungs = Collections.unmodifiableList(normalizedOrder);
            this.enabledRungs = Collections.unmodifiableList(normalizedEnabled);
        }

        public static StudyLadderSettings defaults() {
            List<LadderRung> order = defaultsOrder();
            return new StudyLadderSettings(order, defaultsEnabled(), false);
        }

        public static StudyLadderSettings fromStored(String orderValue, String enabledValue) {
            List<LadderRung> order = splitRungs(orderValue);
            List<LadderRung> enabled = splitRungs(enabledValue);
            if (!order.contains(LadderRung.MEANING_KANJI) && hasAlwaysAvailableRung(enabled) && !enabled.contains(LadderRung.MEANING_KANJI)) {
                enabled.add(LadderRung.MEANING_KANJI);
            }
            if (order.isEmpty() && enabled.isEmpty()) {
                return defaults();
            }
            return new StudyLadderSettings(order, enabled, true);
        }

        public String orderText() {
            return joinRungs(orderedRungs);
        }

        public String enabledText() {
            return joinRungs(enabledRungs);
        }

        public boolean isEnabled(LadderRung rung) {
            return enabledRungs.contains(rung);
        }

        public boolean isValidForItem(LadderRung rung, boolean hasSimilarKanji) {
            return isEnabled(rung) && (rung != LadderRung.SIMILAR_KANJI || hasSimilarKanji);
        }

        public StudyLadderSettings withRungEnabled(LadderRung rung, boolean enabled) {
            if (rung == null) {
                return this;
            }
            List<LadderRung> nextEnabled = new ArrayList<>(enabledRungs);
            if (enabled) {
                if (!nextEnabled.contains(rung)) {
                    nextEnabled.add(rung);
                }
            } else {
                if (alwaysAvailable(rung) && enabledAlwaysAvailableCount() <= 1 && nextEnabled.contains(rung)) {
                    return this;
                }
                nextEnabled.remove(rung);
            }
            return new StudyLadderSettings(orderedRungs, nextEnabled, false);
        }

        public StudyLadderSettings moveRung(LadderRung rung, int delta) {
            if (rung == null || delta == 0) {
                return this;
            }
            List<LadderRung> order = new ArrayList<>(orderedRungs);
            int from = order.indexOf(rung);
            if (from < 0) {
                return this;
            }
            int to = Math.max(0, Math.min(order.size() - 1, from + delta));
            if (to == from) {
                return this;
            }
            order.remove(from);
            order.add(to, rung);
            return new StudyLadderSettings(order, enabledRungs, false);
        }

        public int enabledAlwaysAvailableCount() {
            int count = 0;
            for (LadderRung rung : enabledRungs) {
                if (alwaysAvailable(rung)) {
                    count++;
                }
            }
            return count;
        }

        public LadderRung startingRung(boolean hasSimilarKanji) {
            return effectiveRung(LadderRung.startingRung(), hasSimilarKanji);
        }

        public LadderRung effectiveRung(LadderRung current, boolean hasSimilarKanji) {
            LadderRung safeCurrent = current == null ? LadderRung.startingRung() : current;
            if (isValidForItem(safeCurrent, hasSimilarKanji)) {
                return safeCurrent;
            }
            int start = orderedRungs.indexOf(safeCurrent);
            if (start < 0) {
                start = orderedRungs.indexOf(LadderRung.startingRung());
            }
            start = Math.max(0, start);
            for (int distance = 1; distance < orderedRungs.size(); distance++) {
                int before = start - distance;
                if (before >= 0) {
                    LadderRung candidate = orderedRungs.get(before);
                    if (isValidForItem(candidate, hasSimilarKanji)) {
                        return candidate;
                    }
                }
                int after = start + distance;
                if (after < orderedRungs.size()) {
                    LadderRung candidate = orderedRungs.get(after);
                    if (isValidForItem(candidate, hasSimilarKanji)) {
                        return candidate;
                    }
                }
            }
            return LadderRung.KANJI_MEANING;
        }

        public LadderRung nextRung(LadderRung current, boolean hasSimilarKanji) {
            LadderRung effective = effectiveRung(current, hasSimilarKanji);
            int start = orderedRungs.indexOf(effective);
            for (int i = start + 1; i < orderedRungs.size(); i++) {
                LadderRung candidate = orderedRungs.get(i);
                if (isValidForItem(candidate, hasSimilarKanji)) {
                    return candidate;
                }
            }
            return effective;
        }

        public LadderRung previousRung(LadderRung current, boolean hasSimilarKanji) {
            LadderRung effective = effectiveRung(current, hasSimilarKanji);
            int start = orderedRungs.indexOf(effective);
            for (int i = start - 1; i >= 0; i--) {
                LadderRung candidate = orderedRungs.get(i);
                if (isValidForItem(candidate, hasSimilarKanji)) {
                    return candidate;
                }
            }
            return effective;
        }

        public int rankForRung(LadderRung rung) {
            int rank = orderedRungs.indexOf(rung);
            return rank < 0 ? orderedRungs.size() : rank;
        }

        public static boolean alwaysAvailable(LadderRung rung) {
            return rung != null && rung != LadderRung.SIMILAR_KANJI;
        }

        private static List<LadderRung> splitRungs(String value) {
            List<LadderRung> out = new ArrayList<>();
            if (value == null || value.trim().isEmpty()) {
                return out;
            }
            String[] parts = value.trim().split("[,\\s]+");
            for (String part : parts) {
                LadderRung rung = LadderRung.fromWireName(part);
                if (part.equals(rung.wireName()) && !out.contains(rung)) {
                    out.add(rung);
                }
            }
            return out;
        }

        private static List<LadderRung> normalizeOrder(List<LadderRung> requested) {
            List<LadderRung> out = new ArrayList<>();
            if (requested != null) {
                for (LadderRung rung : requested) {
                    if (rung != null && !out.contains(rung)) {
                        out.add(rung);
                    }
                }
            }
            for (LadderRung rung : defaultsOrder()) {
                if (!out.contains(rung)) {
                    insertMissingRung(out, rung);
                }
            }
            return out;
        }

        private static void insertMissingRung(List<LadderRung> out, LadderRung missing) {
            List<LadderRung> defaults = defaultsOrder();
            int defaultIndex = defaults.indexOf(missing);
            int previousIndex = -1;
            int nextIndex = -1;
            for (int i = defaultIndex - 1; i >= 0; i--) {
                previousIndex = out.indexOf(defaults.get(i));
                if (previousIndex >= 0) {
                    break;
                }
            }
            for (int i = defaultIndex + 1; i < defaults.size(); i++) {
                nextIndex = out.indexOf(defaults.get(i));
                if (nextIndex >= 0) {
                    break;
                }
            }
            if (previousIndex >= 0 && nextIndex >= 0 && previousIndex < nextIndex) {
                out.add(nextIndex, missing);
            } else if (previousIndex >= 0) {
                out.add(previousIndex + 1, missing);
            } else if (nextIndex >= 0) {
                out.add(nextIndex, missing);
            } else {
                out.add(missing);
            }
        }

        private static List<LadderRung> normalizeEnabled(List<LadderRung> requested, List<LadderRung> order) {
            List<LadderRung> out = new ArrayList<>();
            if (requested == null || requested.isEmpty()) {
                out.addAll(order);
                return out;
            }
            for (LadderRung rung : requested) {
                if (rung != null && !out.contains(rung)) {
                    out.add(rung);
                }
            }
            return out;
        }

        private static boolean hasAlwaysAvailableRung(List<LadderRung> rungs) {
            for (LadderRung rung : rungs) {
                if (alwaysAvailable(rung)) {
                    return true;
                }
            }
            return false;
        }

        private static List<LadderRung> defaultsOrder() {
            List<LadderRung> out = new ArrayList<>();
            out.add(LadderRung.WRITE_KANJI);
            out.add(LadderRung.SIMILAR_KANJI);
            out.add(LadderRung.TYPE_MEANING);
            out.add(LadderRung.MEANING_KANJI);
            out.add(LadderRung.KANJI_MEANING);
            out.add(LadderRung.FONT_MEANING);
            out.add(LadderRung.WORD_READING);
            return out;
        }

        private static List<LadderRung> defaultsEnabled() {
            return new ArrayList<>(defaultsOrder());
        }

        private static String joinRungs(List<LadderRung> rungs) {
            List<String> values = new ArrayList<>();
            for (LadderRung rung : rungs) {
                values.add(rung.wireName());
            }
            return String.join(",", values);
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
    protected static final String CONTEXT_MEANING_KANJI_CHOICE_CARD = "MeaningKanjiChoiceCard";
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
