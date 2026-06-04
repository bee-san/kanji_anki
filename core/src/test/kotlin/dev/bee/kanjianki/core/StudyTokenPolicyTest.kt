package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class StudyTokenPolicyTest {
    @Test
    fun keepsExistingActiveToken() {
        assertEquals("already-active", StudyTokenPolicy.studyItem("学", "already-active"))
        assertEquals(" ", StudyTokenPolicy.studyItem("学", " "))
    }

    @Test
    fun createsKanjiPrefixedUuidTokenWhenMissing() {
        val generated = StudyTokenPolicy.studyItem("学", "")
        val generatedFromNull = StudyTokenPolicy.studyItem("習", null)

        assertTrue(generated.startsWith("学-"))
        assertNotEquals("学-", generated)
        UUID.fromString(generated.substring("学-".length))
        assertTrue(generatedFromNull.startsWith("習-"))
        UUID.fromString(generatedFromNull.substring("習-".length))
    }

    @Test
    fun preservesJavaStringConcatNullPrefixBehavior() {
        val generated = StudyTokenPolicy.studyItem(null, "")

        assertTrue(generated.startsWith("null-"))
        UUID.fromString(generated.substring("null-".length))
    }
}
