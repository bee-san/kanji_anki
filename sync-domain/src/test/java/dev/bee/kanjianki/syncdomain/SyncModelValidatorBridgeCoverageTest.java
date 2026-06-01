package dev.bee.kanjianki.syncdomain;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SyncModelValidatorBridgeCoverageTest {
    @Test
    public void coversStaticBridgeMethods() {
        assertTrue(SyncModelValidator.validateModelFields(
                "Kiku",
                Arrays.asList("Expression", "Meaning"),
                "Kiku",
                Arrays.asList("Expression", "Meaning")
        ).isEmpty());
        assertEquals("retryable_provider", SyncModelValidator.classifyProviderFailure(null));
    }
}
