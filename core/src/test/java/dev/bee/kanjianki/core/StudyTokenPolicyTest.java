package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public final class StudyTokenPolicyTest {
    @Test
    public void keepsExistingActiveToken() {
        assertEquals("already-active", StudyTokenPolicy.studyItem("学", "already-active"));
        assertEquals(" ", StudyTokenPolicy.studyItem("学", " "));
    }

    @Test
    public void createsKanjiPrefixedUuidTokenWhenMissing() {
        String generated = StudyTokenPolicy.studyItem("学", "");
        String generatedFromNull = StudyTokenPolicy.studyItem("習", null);

        assertTrue(generated.startsWith("学-"));
        assertNotEquals("学-", generated);
        UUID.fromString(generated.substring("学-".length()));
        assertTrue(generatedFromNull.startsWith("習-"));
        UUID.fromString(generatedFromNull.substring("習-".length()));
    }

    @Test
    public void preservesJavaStringConcatNullPrefixBehavior() {
        String generated = StudyTokenPolicy.studyItem(null, "");

        assertTrue(generated.startsWith("null-"));
        UUID.fromString(generated.substring("null-".length()));
    }
}
