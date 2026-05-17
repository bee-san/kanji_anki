package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SyncValidatorTest {
    @Test
    public void validatesKikuRequiredFields() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();

        assertTrue(SyncValidator.validateModelFields("Kiku", settings.requiredFields(), settings).isEmpty());
        assertEquals(1, SyncValidator.validateModelFields("Other", settings.requiredFields(), settings).size());
        assertEquals(1, SyncValidator.validateModelFields(null, settings.requiredFields(), settings).size());
        assertTrue(SyncValidator.validateModelFields("Kiku", Arrays.asList("Expression"), settings).size() > 1);
        assertTrue(SyncValidator.validateModelFields("Kiku", Arrays.asList("Expression"), settings).get(0).contains("Configured note type Kiku"));
    }

    @Test
    public void ignoresBlankOptionalCustomFields() {
        RecordsSyncModels.Settings settings = new RecordsSyncModels.Settings(
                "Custom Japanese",
                "Mining",
                "Front",
                "",
                "Back",
                "",
                "",
                "",
                21,
                2,
                100,
                3000,
                24,
                3,
                RecordsBase.DEFAULT_WRITING_TRIGGER_MISS_DAYS
        );

        assertEquals(Arrays.asList("Front", "Back"), settings.requiredFields());
        assertTrue(SyncValidator.validateModelFields("Custom Japanese", Arrays.asList("Front", "Back"), settings).isEmpty());
    }

    @Test
    public void classifiesProviderFailures() {
        assertEquals("permanent_permission", SyncValidator.classifyProviderFailure(new SecurityException("denied")));
        assertEquals("permanent_configuration", SyncValidator.classifyProviderFailure(new RuntimeException("missing field Expression")));
        assertEquals("permanent_configuration", SyncValidator.classifyProviderFailure(new RuntimeException("wrong model")));
        assertEquals("permanent_configuration", SyncValidator.classifyProviderFailure(new RuntimeException("missing note type")));
        assertEquals("permanent_permission", SyncValidator.classifyProviderFailure(new RuntimeException("provider permission denied")));
        assertEquals("retryable_provider", SyncValidator.classifyProviderFailure(null));
        assertEquals("retryable_provider", SyncValidator.classifyProviderFailure(new RuntimeException()));
        assertEquals("retryable_provider", SyncValidator.classifyProviderFailure(new RuntimeException("cursor timed out")));
    }
}
