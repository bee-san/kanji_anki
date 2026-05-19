package dev.bee.kanjianki.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class StudyLayoutPolicyTest {
    @Test
    public void writingPadHeightPreservesScreenBreakpoints() {
        assertEquals(300, StudyLayoutPolicy.writingPadHeightDp(699));
        assertEquals(340, StudyLayoutPolicy.writingPadHeightDp(700));
        assertEquals(340, StudyLayoutPolicy.writingPadHeightDp(819));
        assertEquals(390, StudyLayoutPolicy.writingPadHeightDp(820));
    }
}
