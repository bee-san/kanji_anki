package dev.bee.kanjianki;

import static org.junit.Assert.assertEquals;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.domain.model.study.StudyDashboardRow;
import dev.bee.kanjianki.domain.model.study.StudyExample;

import org.junit.Test;

import java.util.Collections;

public final class LegacyStudyMappersTest {
    @Test
    public void toDomainRowsPreservesExampleIdentityAndSentence() {
        RecordsImportModels.Example legacyExample = new RecordsImportModels.Example(
                "suspended",
                123L,
                456L,
                "日本",
                "にほん",
                "Japan",
                "日本へ行く。",
                true,
                2,
                21,
                9,
                13.0,
                7.5,
                0.41
        );
        RecordsImportModels.DashboardRow legacyRow = new RecordsImportModels.DashboardRow(
                "日",
                42,
                "sun",
                "にち",
                "nid:123",
                88,
                "suspended",
                "Suspended support",
                1,
                1,
                1,
                Collections.singletonList(legacyExample)
        );

        StudyDashboardRow row = LegacyStudyMappers.toDomainRows(
                Collections.singletonList(legacyRow)
        ).get(0);
        StudyExample example = row.getExamples().get(0);

        assertEquals(123L, example.getCardId());
        assertEquals(456L, example.getNoteId());
        assertEquals("日本へ行く。", example.getSentence());
        assertEquals("日本", example.getExpression());
        assertEquals("にほん", example.getReading());
        assertEquals("Japan", example.getMeaning());
    }
}
