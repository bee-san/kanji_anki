package dev.bee.kanjianki.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class HowKaniWorksCopyTest {
    @Test
    fun allSectionsNonBlankInDefaultLocale() {
        val sections = HowKaniWorksCopy.sections()
        assertTrue(sections.isNotEmpty())
        for (section in sections) {
            assertTrue(section.title.isNotBlank())
            assertTrue(section.body.isNotBlank())
        }
    }

    @Test
    fun pageTitleNonBlank() {
        assertTrue(HowKaniWorksCopy.pageTitle().isNotBlank())
    }

    @Test
    fun allSectionsUnderLengthBudget() {
        for (section in HowKaniWorksCopy.sections()) {
            assertTrue(
                "Section '${section.title}' body too long: ${section.body.length}",
                section.body.length <= 600
            )
        }
    }

    @Test
    fun atLeastFourSections() {
        assertTrue(HowKaniWorksCopy.sections().size >= 4)
    }

    @Test
    fun adaptiveCopyDoesNotDescribeLegacyLadderDemotion() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val copy = HowKaniWorksCopy.sections().joinToString(" ") { "${it.title} ${it.body}" }.lowercase()

            assertFalse(copy.contains("ladder movement"))
            assertFalse(copy.contains("trigger demotion"))
            assertTrue(copy.contains("revalidation"))
        } finally {
            Locale.setDefault(original)
        }
    }
}
