package dev.bee.kanjianki.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.lifecycle.lifecycleScope
import dev.bee.kanjianki.KaniTheme
import dev.bee.kanjianki.SettingsThemeCopy
import dev.bee.kanjianki.core.WidgetTextCopy
import dev.bee.kanjianki.core.KaniThemeChoice
import kotlinx.coroutines.launch

internal fun ownsLegacyConfigurableWidget(context: Context, appWidgetId: Int): Boolean =
    ownsStudyOverviewWidget(context, appWidgetId)

internal suspend fun loadKaniWidgetInstanceOptions(
    context: Context,
    appWidgetId: Int,
): KaniWidgetInstanceOptions? {
    if (!ownsLegacyConfigurableWidget(context, appWidgetId)) return null
    val glanceId = GlanceAppWidgetManager(context).getGlanceIdBy(appWidgetId)
    val prefs = KaniWidget().getAppWidgetState<Preferences>(context, glanceId)
    return KaniWidgetInstanceOptions.fromStorageValues(
        prefs[stringPreferencesKey(KaniWidgetInstanceOptions.STYLE_PREF_KEY)],
        prefs[stringPreferencesKey(KaniWidgetInstanceOptions.THEME_OVERRIDE_PREF_KEY)],
    )
}

/**
 * Standard `APPWIDGET_CONFIGURE` flow. Configuration is optional
 * (`configuration_optional` in the provider info): dropping the widget
 * without visiting this screen keeps the zero-config due card following the
 * in-app theme. Long-press > reconfigure reopens it per instance.
 */
class KaniWidgetConfigActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appWidgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, resultIntent(appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        lifecycleScope.launch {
            val initialOptions = runCatching {
                loadKaniWidgetInstanceOptions(this@KaniWidgetConfigActivity, appWidgetId)
            }.getOrNull()
            if (initialOptions == null) {
                finish()
                return@launch
            }
            setContent {
                KaniTheme {
                    KaniWidgetConfigScreen(
                        initialOptions = initialOptions,
                        onSave = { options -> persistAndFinish(appWidgetId, options) },
                    )
                }
            }
        }
    }

    private fun persistAndFinish(appWidgetId: Int, options: KaniWidgetInstanceOptions) {
        lifecycleScope.launch {
            val saved = runCatching {
                if (!ownsLegacyConfigurableWidget(this@KaniWidgetConfigActivity, appWidgetId)) {
                    return@runCatching false
                }
                val glanceId = GlanceAppWidgetManager(this@KaniWidgetConfigActivity)
                    .getGlanceIdBy(appWidgetId)
                updateAppWidgetState(this@KaniWidgetConfigActivity, glanceId) { prefs ->
                    prefs[stringPreferencesKey(KaniWidgetInstanceOptions.STYLE_PREF_KEY)] =
                        options.style.storageKey
                    prefs[stringPreferencesKey(KaniWidgetInstanceOptions.THEME_OVERRIDE_PREF_KEY)] =
                        options.themeStorageValue()
                }
                KaniWidget().update(this@KaniWidgetConfigActivity, glanceId)
                true
            }.getOrDefault(false)
            if (saved) setResult(RESULT_OK, resultIntent(appWidgetId))
            finish()
        }
    }

    private fun resultIntent(appWidgetId: Int): Intent =
        Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
}

@Composable
internal fun KaniWidgetConfigScreen(
    initialOptions: KaniWidgetInstanceOptions,
    onSave: (KaniWidgetInstanceOptions) -> Unit,
) {
    var style by remember(initialOptions) { mutableStateOf(initialOptions.style) }
    var themeOverride by remember(initialOptions) { mutableStateOf(initialOptions.themeOverride) }
    val isLegacyHeatmap = initialOptions.style == KaniWidgetStyle.HEATMAP
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(WidgetTextCopy.widgetConfigTitle(), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))

            if (isLegacyHeatmap) {
                Text(WidgetTextCopy.widgetStyleSectionTitle(), style = MaterialTheme.typography.titleMedium)
                ConfigChoiceRow(
                    label = WidgetTextCopy.widgetStyleHeatmapLabel(),
                    selected = style == KaniWidgetStyle.HEATMAP,
                    testTag = "widget_style_heatmap",
                ) { style = KaniWidgetStyle.HEATMAP }
                ConfigChoiceRow(
                    label = WidgetTextCopy.widgetStyleDueCardLabel(),
                    selected = style == KaniWidgetStyle.DUE_CARD,
                    testTag = "widget_style_due_card",
                ) { style = KaniWidgetStyle.DUE_CARD }
                Spacer(Modifier.height(16.dp))
            }
            Text(WidgetTextCopy.widgetThemeSectionTitle(), style = MaterialTheme.typography.titleMedium)
            ConfigChoiceRow(
                label = WidgetTextCopy.widgetThemeFollowAppLabel(),
                selected = themeOverride == null,
                testTag = "widget_theme_follow_app",
            ) { themeOverride = null }
            for (choice in KaniThemeChoice.entries) {
                ConfigChoiceRow(
                    label = SettingsThemeCopy.choiceTitle(choice),
                    selected = themeOverride == choice,
                    testTag = "widget_theme_${choice.storageKey}",
                ) { themeOverride = choice }
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { onSave(KaniWidgetInstanceOptions(style, themeOverride)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("widget_config_save"),
            ) {
                Text(WidgetTextCopy.widgetSaveLabel())
            }
        }
    }
}

@Composable
private fun ConfigChoiceRow(
    label: String,
    selected: Boolean,
    testTag: String,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .testTag(testTag)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.height(0.dp))
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
