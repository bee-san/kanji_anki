@file:JvmName("MainActivitySettingsAutomationHeroCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.reminders.ReminderScheduler

private val HeroInk = ComposeColor(0xFF7A245D)
private val HeroMuted = ComposeColor(0xFF6E6E78)
private val HeroWhite = ComposeColor(0xFFFFFFFF)
private val HeroBadgeBorder = ComposeColor(0xFFEBD6E4)
private val HeroPanelFill = ComposeColor(0xFFFFF8FC)
private val HeroPanelBorder = ComposeColor(0xFFFFD4E7)
private val PillBorder = ComposeColor(0xFFF9CFE2)
private val PillFill = ComposeColor(0xFFFFFFFF)
private val PillShape = RoundedCornerShape(20.dp)
private val HeroShape = RoundedCornerShape(30.dp)
private val BadgeShape = RoundedCornerShape(18.dp)

data class SettingsAutomationHeroPillModel(
    val label: String,
    val value: String,
    val valueColor: Int,
)

data class SettingsAutomationHeroModel(
    val cockpitLabel: String,
    val title: String,
    val body: String,
    val rows: List<List<SettingsAutomationHeroPillModel>>,
)

internal fun settingsAutomationHeroView(
    activity: MainActivitySettings,
    current: dev.bee.kanjianki.core.RecordsSyncModels.Settings,
    reminder: LocalStoreBase.ReminderSettings,
    autoSync: LocalStoreBase.AutoSyncSettings,
    autoUpdate: LocalStoreBase.AutoUpdateStatus
): View {
    val model = settingsAutomationHeroModel(activity, current, reminder, autoSync, autoUpdate)
    return ComposeView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                SettingsAutomationHero(model)
            }
        }
    }
}

internal fun settingsAutomationHeroModel(
    activity: MainActivitySettings,
    current: dev.bee.kanjianki.core.RecordsSyncModels.Settings,
    reminder: LocalStoreBase.ReminderSettings,
    autoSync: LocalStoreBase.AutoSyncSettings,
    autoUpdate: LocalStoreBase.AutoUpdateStatus
): SettingsAutomationHeroModel {
    val reminderBlocked = reminder.enabled && !ReminderScheduler.notificationsAllowed(activity)
    return SettingsAutomationHeroModel(
        cockpitLabel = SettingsTextCopy.settingsCockpitLabel(),
        title = MainActivityBase.NAV_SETTINGS,
        body = SettingsTextCopy.settingsHeroBody(),
        rows = listOf(
            listOf(
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.noteTypeStatusLabel(),
                    StudyTextCopy.compact(current.modelName, 56),
                    MainActivityUiSupport.STUDY_PLUM
                ),
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.importFiltersStatusLabel(),
                    StudyTextCopy.compact(SettingsTextCopy.settingsImportSummary(current), 56),
                    MainActivityUiSupport.TEAL
                )
            ),
            listOf(
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.importRanksStatusLabel(),
                    StudyTextCopy.compact("${current.suspendedRankMin}-${current.suspendedRankMax}", 56),
                    MainActivityUiSupport.TEAL
                ),
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.reminderStatusLabel(),
                    StudyTextCopy.compact(
                        SettingsTextCopy.settingsReminderSummary(
                            reminder.enabled,
                            reminderBlocked,
                            reminder.displayTime()
                        ),
                        56
                    ),
                    if (reminder.enabled) MainActivityUiSupport.TEAL else MainActivityUiSupport.MUTED
                )
            ),
            listOf(
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.dailySyncStatusLabel(),
                    StudyTextCopy.compact(
                        SettingsTextCopy.settingsAutoSyncSummary(
                            autoSync.configured,
                            autoSync.enabled,
                            autoSync.displayTime()
                        ),
                        56
                    ),
                    if (autoSync.enabled) MainActivityUiSupport.TEAL else MainActivityUiSupport.MUTED
                ),
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.updatesStatusLabel(),
                    StudyTextCopy.compact(
                        SettingsTextCopy.settingsUpdateSummary(
                            autoUpdate.hasPendingUpdate(),
                            autoUpdate.enabled
                        ),
                        56
                    ),
                    if (autoUpdate.hasPendingUpdate()) MainActivityUiSupport.CORAL else MainActivityUiSupport.STUDY_PINK_DARK
                )
            ),
            listOf(
                SettingsAutomationHeroPillModel(
                    SettingsTextCopy.matchingCardsStatusLabel(),
                    StudyTextCopy.compact(SettingsTextCopy.matchingCardsSummary(current), 56),
                    MainActivityUiSupport.STUDY_PLUM
                )
            )
        )
    )
}

@Composable
fun SettingsAutomationHero(model: SettingsAutomationHeroModel) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = HeroShape,
        color = HeroPanelFill,
        border = BorderStroke(1.dp, HeroPanelBorder),
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = BadgeShape,
                color = HeroWhite,
                border = BorderStroke(1.dp, HeroBadgeBorder)
            ) {
                Text(
                    text = model.cockpitLabel,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    color = ComposeColor(0xFFDA3A7A),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = model.title,
                color = HeroInk,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.body,
                color = HeroMuted,
                fontSize = 16.sp
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                model.rows.forEach { row ->
                    SettingsAutomationHeroRow(row)
                }
            }
        }
    }
}

@Composable
private fun SettingsAutomationHeroRow(pills: List<SettingsAutomationHeroPillModel>) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        pills.forEach { pill ->
            SettingsAutomationHeroPill(
                model = pill,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SettingsAutomationHeroPill(
    model: SettingsAutomationHeroPillModel,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 72.dp),
        shape = PillShape,
        color = PillFill,
        border = BorderStroke(1.dp, PillBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = model.label,
                color = ComposeColor(0xFF6E6E78),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.value,
                color = ComposeColor(model.valueColor),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
