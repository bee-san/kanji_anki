package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class StudyRatingsTest {
    @Test
    fun normalizeKeepsKnownRatingsAndDefaultsUnknownRatingsToAgain() {
        assertEquals("again", StudyRatings.AGAIN)
        assertEquals("hard", StudyRatings.normalize(StudyRatings.HARD))
        assertEquals("good", StudyRatings.normalize(StudyRatings.GOOD))
        assertEquals("easy", StudyRatings.normalize(StudyRatings.EASY))
        assertEquals("again", StudyRatings.normalize(null))
        assertEquals("again", StudyRatings.normalize("pass"))
        assertEquals("again", StudyRatings.Companion.normalize("pass"))

        val normalize = StudyRatings::class.java.getDeclaredMethod("normalize", String::class.java)
        assertEquals("again", normalize.invoke(null, "pass"))

        val constructor = StudyRatings::class.java.getDeclaredConstructor()
        constructor.isAccessible = true
        assertNotNull(constructor.newInstance())
    }
}
