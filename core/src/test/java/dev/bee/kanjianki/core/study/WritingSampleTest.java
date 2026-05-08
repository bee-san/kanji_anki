package dev.bee.kanjianki.core.study;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class WritingSampleTest {
    @Test
    public void emptySampleHasNoInkOrStrokes() {
        WritingSample sample = WritingSample.empty();

        assertFalse(sample.hasInk());
        assertEquals(0, sample.strokeCount());
        assertEquals(0f, sample.width, 0.001f);
        assertEquals(0f, sample.height, 0.001f);
    }

    @Test
    public void strokeCountsIgnoreEmptyStrokesAndExposeImmutableList() {
        WritingSample sample = new WritingSample(Arrays.asList(
                new InkStroke(Collections.emptyList()),
                new InkStroke(Arrays.asList(new InkPoint(0f, 0f, 0), new InkPoint(1f, 1f, 1)))
        ), 10f, 20f);

        assertTrue(sample.hasInk());
        assertEquals(1, sample.strokeCount());
        assertThrows(UnsupportedOperationException.class, () -> sample.strokes.add(new InkStroke(Collections.emptyList())));
    }
}
