@file:JvmName("MainActivitySettingsReferenceDataCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = ComposeColor(0xFF2D1635)
private val Muted = ComposeColor(0xFF6C5674)
private val White = ComposeColor(0xFFFFFFFF)
private val DictionaryBorder = ComposeColor(0xFF31C7D6)
private val StrokeBorder = ComposeColor(0xFFF6CAE1)
private val FontBorder = ComposeColor(0xFFFFD640)
private val PanelShape = RoundedCornerShape(24.dp)

data class SettingsReferenceDataModel(
    val dictionaryTitle: String,
    val dictionaryBody: String,
    val strokeTitle: String,
    val strokeBody: String,
    val fontsTitle: String,
    val fontsBody: String,
)

internal fun dataSourcesPanelsView(activity: MainActivitySettings, model: SettingsReferenceDataModel): View {
    return ComposeView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                DataSourcesPanels(model)
            }
        }
    }
}

@Composable
fun DataSourcesPanels(model: SettingsReferenceDataModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DataSourcePanel(model.dictionaryTitle, model.dictionaryBody, DictionaryBorder)
        DataSourcePanel(model.strokeTitle, model.strokeBody, StrokeBorder)
        DataSourcePanel(model.fontsTitle, model.fontsBody, FontBorder)
    }
}

@Composable
private fun DataSourcePanel(title: String, body: String, borderColor: ComposeColor) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = White,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                color = Ink,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = body,
                color = Muted,
                fontSize = 14.sp
            )
        }
    }
}
