package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.presentation.SettingsSectionContent
import dev.bee.kanjianki.presentation.SettingsSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The desktop half of the Settings parity: the mapping turns a [SettingsSection] into
 * the portable screen the shared surface renders. The surface's layout is proven by
 * `:feature-settings`'s render tests on both hosts; this checks the root menu the
 * mapping builds and that an un-ported section is the honest placeholder.
 */
class DesktopSettingsModelTest {
    @Test
    fun theRootBuildsTheFiveCategoryMenu() {
        val screen = DesktopSettingsModel.screen(SettingsSection.ROOT)

        assertEquals(SettingsSection.ROOT, screen.section)
        val root = screen.root
        assertNotNull("root screen carries a menu", root)
        assertEquals(
            listOf(
                SettingsSection.IMPORT_SYNC,
                SettingsSection.STUDY_BEHAVIOR,
                SettingsSection.AUTOMATION,
                SettingsSection.APPEARANCE,
                SettingsSection.DISPLAY_DATA,
            ),
            root!!.categories.map { it.section },
        )
        // Every category names itself; none is blank.
        assertTrue(root.categories.all { it.title.isNotBlank() && it.summary.isNotBlank() })
    }

    @Test
    fun automationCarriesTheReminderCapabilityNotice() {
        val screen = DesktopSettingsModel.screen(SettingsSection.ROOT)
        val automation = screen.root!!.categories.first { it.section == SettingsSection.AUTOMATION }

        assertEquals(1, automation.notices.size)
        assertTrue(automation.notices.single().contains("while Kani is open"))
    }

    @Test
    fun otherCategoriesCarryNoNotice() {
        val screen = DesktopSettingsModel.screen(SettingsSection.ROOT)
        val nonAutomation = screen.root!!.categories.filter { it.section != SettingsSection.AUTOMATION }

        assertTrue(nonAutomation.all { it.notices.isEmpty() })
    }

    @Test
    fun anUnportedSectionIsTheHonestPlaceholder() {
        for (section in SettingsSection.entries.filter { it != SettingsSection.ROOT }) {
            val screen = DesktopSettingsModel.screen(section)
            assertEquals(section, screen.section)
            assertNull("a leaf section has no root menu", screen.root)
            assertEquals(SettingsSectionContent.Placeholder, screen.content)
        }
    }
}
