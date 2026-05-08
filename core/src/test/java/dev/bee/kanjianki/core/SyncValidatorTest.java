package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SyncValidatorTest {
    @Test
    public void validatesKikuRequiredFields() {
        Records.Settings settings = Records.Settings.kikuDefaults();

        assertTrue(SyncValidator.validateModelFields("Kiku", settings.requiredFields(), settings).isEmpty());
        assertEquals(1, SyncValidator.validateModelFields("Other", settings.requiredFields(), settings).size());
        assertTrue(SyncValidator.validateModelFields("Kiku", Arrays.asList("Expression"), settings).size() > 1);
        assertTrue(SyncValidator.validateModelFields("Kiku", Arrays.asList("Expression"), settings).get(0).contains("Configured note type Kiku"));
    }

    @Test
    public void classifiesProviderFailures() {
        assertEquals("permanent_permission", SyncValidator.classifyProviderFailure(new SecurityException("denied")));
        assertEquals("permanent_configuration", SyncValidator.classifyProviderFailure(new RuntimeException("missing field Expression")));
        assertEquals("retryable_provider", SyncValidator.classifyProviderFailure(new RuntimeException("cursor timed out")));
    }
}
