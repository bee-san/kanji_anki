package dev.bee.kanjianki.syncdomain;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SyncModelValidatorTest {
    @Test
    public void validatesModelNameAndRequiredFields() {
        assertTrue(SyncModelValidator.validateModelFields(
                "Kiku",
                Arrays.asList("Expression", "Meaning"),
                "Kiku",
                Arrays.asList("Expression", "Meaning")
        ).isEmpty());
        assertEquals(1, SyncModelValidator.validateModelFields(
                "Other",
                Arrays.asList("Expression", "Meaning"),
                "Kiku",
                Arrays.asList("Expression", "Meaning")
        ).size());
        assertEquals(2, SyncModelValidator.validateModelFields(
                "Kiku",
                Arrays.asList("Expression"),
                "Kiku",
                Arrays.asList("Expression", "Meaning", "Sentence")
        ).size());
    }

    @Test
    public void classifiesProviderFailures() {
        assertEquals("permanent_permission", SyncModelValidator.classifyProviderFailure(new SecurityException("denied")));
        assertEquals("permanent_configuration", SyncModelValidator.classifyProviderFailure(new RuntimeException("missing field Expression")));
        assertEquals("permanent_configuration", SyncModelValidator.classifyProviderFailure(new RuntimeException("wrong model")));
        assertEquals("permanent_configuration", SyncModelValidator.classifyProviderFailure(new RuntimeException("missing note type")));
        assertEquals("permanent_permission", SyncModelValidator.classifyProviderFailure(new RuntimeException("provider permission denied")));
        assertEquals("retryable_provider", SyncModelValidator.classifyProviderFailure(null));
        assertEquals("retryable_provider", SyncModelValidator.classifyProviderFailure(new RuntimeException()));
        assertEquals("retryable_provider", SyncModelValidator.classifyProviderFailure(new RuntimeException("cursor timed out")));
    }
}
