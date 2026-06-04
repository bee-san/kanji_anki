package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyLayoutPolicyTest {
    @Test
    fun writingPadHeightPreservesScreenBreakpoints() {
        assertEquals(300, StudyLayoutPolicy.writingPadHeightDp(699))
        assertEquals(340, StudyLayoutPolicy.writingPadHeightDp(700))
        assertEquals(340, StudyLayoutPolicy.writingPadHeightDp(819))
        assertEquals(390, StudyLayoutPolicy.writingPadHeightDp(820))
    }
}
