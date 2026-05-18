package dev.bee.kanjianki.core;

import org.junit.Test;

import java.lang.reflect.Constructor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class StudyRatingsTest {
    @Test
    public void normalizeKeepsKnownRatingsAndDefaultsUnknownRatingsToAgain() throws Exception {
        assertEquals("again", StudyRatings.AGAIN);
        assertEquals("hard", StudyRatings.normalize(StudyRatings.HARD));
        assertEquals("good", StudyRatings.normalize(StudyRatings.GOOD));
        assertEquals("easy", StudyRatings.normalize(StudyRatings.EASY));
        assertEquals("again", StudyRatings.normalize(null));
        assertEquals("again", StudyRatings.normalize("pass"));

        Constructor<StudyRatings> constructor = StudyRatings.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        assertNotNull(constructor.newInstance());
    }
}
