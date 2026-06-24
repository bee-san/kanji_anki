package dev.bee.kanjianki

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeStudyCtaSizingTest {
    @Test
    fun sizingTokensMatchComfortableDesignRange() {
        assertTrue(HomeStudyCtaMinHeight >= 104.dp)
        assertTrue(HomeStudyCtaMinHeight <= 116.dp)
        assertTrue(HomeStudyCtaLabelVerticalPadding >= 24.dp)
        assertTrue(HomeStudyCtaArrowCircleSize >= 56.dp)
        assertTrue(HomeStudyCtaArrowCircleSize <= 64.dp)
        assertTrue(HomeStudyCtaArrowEndPadding >= 24.dp)
        assertTrue(HomeStudyCtaArrowEndPadding <= 28.dp)

        val reservedArrowSpace = HomeStudyCtaArrowEndPadding + HomeStudyCtaArrowCircleSize
        assertTrue(HomeStudyCtaLabelEndPadding >= reservedArrowSpace + 32.dp)
        assertTrue(HomeStudyCtaTopSparkleEndPadding >= reservedArrowSpace + 20.dp)
        assertTrue(HomeStudyCtaBottomSparkleStartPadding >= 20.dp)
    }
}
