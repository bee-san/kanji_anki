package dev.bee.kanjianki

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.SettingsTextCopy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

class SettingsStudySortComposeTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun updatesStatusAndSavesSelectedSortMode() {
        val savedMode = AtomicReference<String>()
        composeRule.setContent {
            SettingsNewCardSortPanel(
                model = SettingsNewCardSortPanelModel(
                    title = SettingsTextCopy.newCardSortTitle(),
                    body = SettingsTextCopy.newCardSortBody(),
                    initialMode = RecordsBase.NEW_CARD_SORT_FREQUENCY,
                    options = listOf(
                        SettingsNewCardSortOptionModel(
                            SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_FREQUENCY),
                            RecordsBase.NEW_CARD_SORT_FREQUENCY,
                            SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_FREQUENCY)
                        ),
                        SettingsNewCardSortOptionModel(
                            SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK),
                            RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK,
                            SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK)
                        )
                    ),
                    saveLabel = SettingsTextCopy.saveNewCardSortLabel(),
                    onSave = SettingsNewCardSortSaver { savedMode.set(it) }
                )
            )
        }

        composeRule
            .onNodeWithText(SettingsTextCopy.newCardSortStatusText(RecordsBase.NEW_CARD_SORT_FREQUENCY))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
            .performClick()
        composeRule
            .onNodeWithText(SettingsTextCopy.newCardSortDescription(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
            .assertIsDisplayed()
        composeRule
            .onNodeWithText(SettingsTextCopy.newCardSortStatusText(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK))
            .assertIsDisplayed()
        composeRule.onNodeWithText(SettingsTextCopy.saveNewCardSortLabel()).performClick()

        composeRule.runOnIdle {
            assertEquals(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK, savedMode.get())
        }
    }
}
