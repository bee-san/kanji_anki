package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public final class KanjiGameEngine {
    private static final int MAX_CHOICES = 4;

    public enum GameMode {
        MEANING_POP("meaning_pop", "Meaning Pop", "Kanji -> meaning", "Choose the meaning."),
        READING_RUSH("reading_rush", "Reading Rush", "Word -> reading", "Choose the reading."),
        CONFUSABLE_CLASH("confusable_clash", "Confusable Clash", "Meaning -> kanji", "Choose the kanji.");

        public final String id;
        public final String title;
        public final String label;
        public final String prompt;

        GameMode(String id, String title, String label, String prompt) {
            this.id = id;
            this.title = title;
            this.label = label;
            this.prompt = prompt;
        }
    }

    public static final class GameQuestion {
        public final GameMode mode;
        public final String targetKanji;
        public final String prompt;
        public final String promptDetail;
        public final String correctAnswer;
        public final List<String> choices;
        public final String explanation;

        public GameQuestion(
                GameMode mode,
                String targetKanji,
                String prompt,
                String promptDetail,
                String correctAnswer,
                List<String> choices,
                String explanation
        ) {
            this.mode = mode;
            this.targetKanji = clean(targetKanji);
            this.prompt = clean(prompt);
            this.promptDetail = clean(promptDetail);
            this.correctAnswer = clean(correctAnswer);
            this.choices = Collections.unmodifiableList(new ArrayList<>(cleanList(choices)));
            this.explanation = clean(explanation);
        }

        public boolean isCorrect(String selectedAnswer) {
            return answerKey(correctAnswer).equals(answerKey(selectedAnswer));
        }
    }

    public List<GameMode> availableModes(
            List<Records.DashboardRow> rows,
            List<Records.KanjiInventoryItem> inventory,
            List<Records.SimilarKanjiPair> pairs
    ) {
        List<GameMode> out = new ArrayList<>();
        for (GameMode mode : GameMode.values()) {
            if (nextQuestion(mode, rows, inventory, pairs, new Random(0L)) != null) {
                out.add(mode);
            }
        }
        return out;
    }

    public GameQuestion nextQuestion(
            GameMode mode,
            List<Records.DashboardRow> rows,
            List<Records.KanjiInventoryItem> inventory,
            List<Records.SimilarKanjiPair> pairs,
            Random random
    ) {
        GameMode safeMode = mode == null ? GameMode.MEANING_POP : mode;
        Random safeRandom = random == null ? new Random() : random;
        List<GameCandidate> candidates = candidates(rows, inventory);
        return switch (safeMode) {
            case MEANING_POP -> meaningQuestion(candidates, safeRandom);
            case READING_RUSH -> readingQuestion(candidates, safeRandom);
            case CONFUSABLE_CLASH -> confusableQuestion(candidates, pairs, safeRandom);
        };
    }

    private static GameQuestion meaningQuestion(List<GameCandidate> candidates, Random random) {
        List<GameCandidate> targets = targets(candidates, GameCandidate::hasMeaning);
        if (targets.isEmpty()) {
            return null;
        }
        GameCandidate target = randomCandidate(targets, random);
        List<String> choices = answerChoices(candidates, target.meaning, candidate -> candidate.meaning, random);
        if (choices.size() < 2) {
            return null;
        }
        return new GameQuestion(
                GameMode.MEANING_POP,
                target.kanji,
                target.kanji,
                "Pick the meaning",
                target.meaning,
                choices,
                target.kanji + " = " + target.meaning
        );
    }

    private static GameQuestion readingQuestion(List<GameCandidate> candidates, Random random) {
        List<GameCandidate> targets = targets(candidates, GameCandidate::hasReading);
        if (targets.isEmpty()) {
            return null;
        }
        GameCandidate target = randomCandidate(targets, random);
        List<String> choices = answerChoices(candidates, target.reading, candidate -> candidate.reading, random);
        if (choices.size() < 2) {
            return null;
        }
        return new GameQuestion(
                GameMode.READING_RUSH,
                target.kanji,
                target.expression.isEmpty() ? target.kanji : target.expression,
                "Pick the reading for " + target.kanji,
                target.reading,
                choices,
                target.explanation()
        );
    }

    private static GameQuestion confusableQuestion(
            List<GameCandidate> candidates,
            List<Records.SimilarKanjiPair> pairs,
            Random random
    ) {
        Map<String, List<String>> neighbors = neighborMap(pairs);
        List<GameCandidate> targets = new ArrayList<>();
        for (GameCandidate candidate : targets(candidates, GameCandidate::hasMeaning)) {
            List<String> direct = neighbors.get(candidate.kanji);
            if (direct != null && !direct.isEmpty()) {
                targets.add(candidate);
            }
        }
        if (targets.isEmpty()) {
            return null;
        }
        GameCandidate target = randomCandidate(targets, random);
        LinkedHashSet<String> choices = new LinkedHashSet<>();
        choices.add(target.kanji);
        List<String> direct = new ArrayList<>(neighbors.getOrDefault(target.kanji, Collections.emptyList()));
        Collections.shuffle(direct, random);
        for (String kanji : direct) {
            choices.add(kanji);
            if (choices.size() >= MAX_CHOICES) {
                break;
            }
        }
        List<String> shuffled = new ArrayList<>(choices);
        if (shuffled.size() < 2) {
            return null;
        }
        Collections.shuffle(shuffled, random);
        return new GameQuestion(
                GameMode.CONFUSABLE_CLASH,
                target.kanji,
                "Which kanji means " + target.meaning + "?",
                "Watch the shape",
                target.kanji,
                shuffled,
                target.kanji + " = " + target.meaning
        );
    }

    private static List<GameCandidate> candidates(
            List<Records.DashboardRow> rows,
            List<Records.KanjiInventoryItem> inventory
    ) {
        LinkedHashMap<String, GameCandidate> byKanji = new LinkedHashMap<>();
        if (rows != null) {
            for (Records.DashboardRow row : rows) {
                GameCandidate candidate = GameCandidate.fromRow(row);
                if (candidate.hasAnyAnswer()) {
                    byKanji.put(candidate.kanji, candidate);
                }
            }
        }
        if (inventory != null) {
            for (Records.KanjiInventoryItem item : inventory) {
                GameCandidate candidate = GameCandidate.fromInventory(item);
                if (candidate.hasAnyAnswer() && !byKanji.containsKey(candidate.kanji)) {
                    byKanji.put(candidate.kanji, candidate);
                }
            }
        }
        return new ArrayList<>(byKanji.values());
    }

    private static List<GameCandidate> targets(List<GameCandidate> candidates, CandidateFilter filter) {
        List<GameCandidate> active = new ArrayList<>();
        List<GameCandidate> fallback = new ArrayList<>();
        for (GameCandidate candidate : candidates) {
            if (filter.include(candidate)) {
                if (candidate.fromDashboard) {
                    active.add(candidate);
                } else {
                    fallback.add(candidate);
                }
            }
        }
        return active.isEmpty() ? fallback : active;
    }

    private static GameCandidate randomCandidate(List<GameCandidate> candidates, Random random) {
        return candidates.get(random.nextInt(candidates.size()));
    }

    private static List<String> answerChoices(
            List<GameCandidate> candidates,
            String correct,
            AnswerExtractor extractor,
            Random random
    ) {
        LinkedHashSet<String> answers = new LinkedHashSet<>();
        String safeCorrect = clean(correct);
        answers.add(safeCorrect);
        List<String> decoys = new ArrayList<>();
        for (GameCandidate candidate : candidates) {
            String answer = clean(extractor.answer(candidate));
            if (!answer.isEmpty() && !answerKey(answer).equals(answerKey(safeCorrect))) {
                decoys.add(answer);
            }
        }
        decoys = uniqueAnswers(decoys);
        Collections.shuffle(decoys, random);
        for (String decoy : decoys) {
            answers.add(decoy);
            if (answers.size() >= MAX_CHOICES) {
                break;
            }
        }
        List<String> shuffled = new ArrayList<>(answers);
        Collections.shuffle(shuffled, random);
        return shuffled;
    }

    private static List<String> uniqueAnswers(List<String> values) {
        LinkedHashMap<String, String> byKey = new LinkedHashMap<>();
        for (String value : values) {
            byKey.putIfAbsent(answerKey(value), value);
        }
        return new ArrayList<>(byKey.values());
    }

    private static Map<String, List<String>> neighborMap(List<Records.SimilarKanjiPair> pairs) {
        Map<String, List<String>> out = new HashMap<>();
        if (pairs == null) {
            return out;
        }
        for (Records.SimilarKanjiPair pair : pairs) {
            if (pair == null) {
                continue;
            }
            String a = clean(pair.kanjiA);
            String b = clean(pair.kanjiB);
            if (a.isEmpty() || b.isEmpty() || a.equals(b)) {
                continue;
            }
            addNeighbor(out, a, b);
            addNeighbor(out, b, a);
        }
        return out;
    }

    private static void addNeighbor(Map<String, List<String>> neighbors, String kanji, String neighbor) {
        List<String> direct = neighbors.computeIfAbsent(kanji, ignored -> new ArrayList<>());
        if (!direct.contains(neighbor)) {
            direct.add(neighbor);
        }
    }

    private static List<String> cleanList(List<String> values) {
        List<String> out = new ArrayList<>();
        if (values == null) {
            return out;
        }
        for (String value : values) {
            String cleaned = clean(value);
            if (!cleaned.isEmpty()) {
                out.add(cleaned);
            }
        }
        return out;
    }

    private static String firstNonEmpty(String first, String second) {
        String safeFirst = clean(first);
        return safeFirst.isEmpty() ? clean(second) : safeFirst;
    }

    private static String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("Meaning:", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String answerKey(String value) {
        return clean(value).toLowerCase(Locale.ROOT);
    }

    private interface CandidateFilter {
        boolean include(GameCandidate candidate);
    }

    private interface AnswerExtractor {
        String answer(GameCandidate candidate);
    }

    private static final class GameCandidate {
        final String kanji;
        final String meaning;
        final String reading;
        final String expression;
        final boolean fromDashboard;

        GameCandidate(String kanji, String meaning, String reading, String expression, boolean fromDashboard) {
            this.kanji = clean(kanji);
            this.meaning = clean(meaning);
            this.reading = clean(reading);
            this.expression = clean(expression);
            this.fromDashboard = fromDashboard;
        }

        static GameCandidate fromRow(Records.DashboardRow row) {
            if (row == null) {
                return new GameCandidate("", "", "", "", true);
            }
            String exampleMeaning = "";
            String exampleReading = "";
            String expression = "";
            for (Records.Example example : row.examples) {
                if (exampleMeaning.isEmpty()) {
                    exampleMeaning = example.meaning;
                }
                if (exampleReading.isEmpty()) {
                    exampleReading = example.reading;
                }
                if (expression.isEmpty()) {
                    expression = example.expression;
                }
            }
            return new GameCandidate(
                    row.kanji,
                    firstNonEmpty(row.primaryMeaning, exampleMeaning),
                    firstNonEmpty(row.reading, exampleReading),
                    firstNonEmpty(expression, row.kanji),
                    true
            );
        }

        static GameCandidate fromInventory(Records.KanjiInventoryItem item) {
            if (item == null) {
                return new GameCandidate("", "", "", "", false);
            }
            return new GameCandidate(item.kanji, item.primaryMeaning, item.readings, item.kanji, false);
        }

        boolean hasAnyAnswer() {
            return !kanji.isEmpty() && (hasMeaning() || hasReading());
        }

        boolean hasMeaning() {
            return !meaning.isEmpty();
        }

        boolean hasReading() {
            return !reading.isEmpty();
        }

        String explanation() {
            if (meaning.isEmpty()) {
                return kanji + " = " + reading;
            }
            return kanji + " = " + reading + " · " + meaning;
        }
    }
}
