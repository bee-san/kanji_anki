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
        InkStroke emptyStroke = new InkStroke(Collections.emptyList());
        assertThrows(UnsupportedOperationException.class, () -> sample.strokes.add(emptyStroke));
    }

    @Test
    public void inkPointEqualityUsesCoordinatesAndTimestamp() {
        InkPoint point = new InkPoint(1f, 2f, 3);

        assertEquals(new InkPoint(2f, 6f, 3), point.scaled(2f, 3f));
        assertEquals(point, new InkPoint(1f, 2f, 3));
        assertEquals(point.hashCode(), new InkPoint(1f, 2f, 3).hashCode());
        Object otherType = "point";
        boolean equalsOtherType = point.equals(otherType);
        boolean equalsDifferentX = point.equals(new InkPoint(2f, 2f, 3));
        boolean equalsDifferentY = point.equals(new InkPoint(1f, 3f, 3));
        boolean equalsDifferentTimestamp = point.equals(new InkPoint(1f, 2f, 4));
        assertFalse(equalsOtherType);
        assertFalse(equalsDifferentX);
        assertFalse(equalsDifferentY);
        assertFalse(equalsDifferentTimestamp);
    }
}
