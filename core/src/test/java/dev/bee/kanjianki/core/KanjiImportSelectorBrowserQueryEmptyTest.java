package dev.bee.kanjianki.core;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public final class KanjiImportSelectorBrowserQueryEmptyTest {

    @Parameters(name = "{0}")
    public static Iterable<Object[]> cases() {
        return Arrays.asList(new Object[][]{
                {"disabled flag ignores matched card", false, "tag:kani", "裂,1500\n"},
                {"enabled with blank query ignores matched card", true, "  ", "裂,1500\n"},
                {"enabled but rank out of range filters card", true, "tag:kani", "裂,5000\n"},
        });
    }

    @Parameter(0) public String description;
    @Parameter(1) public boolean browserQueryCards;
    @Parameter(2) public String browserQuery;
    @Parameter(3) public String rankCsv;

    @Test
    public void importsRemainEmpty() throws Exception {
        Records.Settings settings = settingsWithBrowserQuery(browserQueryCards, browserQuery);
        JitenKanjiRanks ranks = JitenKanjiRanks.parseCsv(new StringReader(rankCsv));
        Records.Card queryMatched = card(10, 1, false).withBrowserQueryMatched(true);
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(
                Collections.singletonList(note(1, "裂ける", "さける")),
                Collections.singletonList(queryMatched)
        );

        List<Records.SuspendedImport> imports = new KanjiImportSelector(ranks, 100, 3000).importFrom(snapshot, settings);

        assertTrue(description, imports.isEmpty());
    }

    private static Records.Note note(long id, String expression, String reading) {
        Map<String, String> fields = new LinkedHashMap<>();
        Records.Settings defaults = Records.Settings.kikuDefaults();
        fields.put(defaults.expressionField, expression);
        fields.put(defaults.readingField, reading);
        fields.put(defaults.meaningField, "meaning");
        fields.put(defaults.sentenceField, expression + " sentence");
        fields.put(defaults.frequencyField, "9999");
        fields.put(defaults.frequencySortField, "9999");
        return new Records.Note(id, "Kiku", fields, Collections.emptyList());
    }

    private static Records.Card card(long cardId, long noteId, boolean suspended) {
        return new Records.Card(
                cardId,
                noteId,
                0,
                "例文マイニング",
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

    private static Records.Settings settingsWithBrowserQuery(boolean browserQueryCards, String browserQuery) {
        Records.Settings defaults = Records.Settings.kikuDefaults();
        return new Records.Settings(
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
                false,
                false,
                false,
                Collections.emptyList(),
                false,
                7.0,
                2,
                1,
                browserQueryCards,
                browserQuery
        );
    }
}
