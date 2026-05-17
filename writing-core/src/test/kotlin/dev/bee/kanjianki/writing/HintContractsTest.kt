package dev.bee.kanjianki.writing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HintContractsTest {
    @Test
    fun writingLevelsMatchCurrentStorageContract() {
        assertEquals(0, HintLevel.TRACE.writingLevel)
        assertEquals(1, HintLevel.OUTLINE.writingLevel)
        assertEquals(2, HintLevel.MINIMAL.writingLevel)
        assertEquals(3, HintLevel.BLIND.writingLevel)
    }

    @Test
    fun hintLevelClampsUnknownStoredValues() {
        assertEquals(HintLevel.TRACE, HintLevel.fromWritingLevel(-1))
        assertEquals(HintLevel.TRACE, HintLevel.fromWritingLevel(0))
        assertEquals(HintLevel.MINIMAL, HintLevel.fromWritingLevel(2))
        assertEquals(HintLevel.BLIND, HintLevel.fromWritingLevel(99))
    }

    @Test
    fun hintNavigationStaysInsideBounds() {
        assertEquals(HintLevel.OUTLINE, HintLevel.TRACE.next())
        assertEquals(HintLevel.BLIND, HintLevel.BLIND.next())
        assertEquals(HintLevel.MINIMAL, HintLevel.BLIND.previous())
        assertEquals(HintLevel.TRACE, HintLevel.TRACE.previous())
    }

    @Test
    fun hintStateRejectsNegativeStrokeCount() {
        assertThrows(IllegalArgumentException::class.java) {
            HintState(HintLevel.TRACE, acceptedStrokeCount = -1)
        }
    }
}
