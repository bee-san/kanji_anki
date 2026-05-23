@file:JvmName("MainActivitySettingsReferenceDataCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Ink = ComposeColor(0xFF2D1635)
private val Muted = ComposeColor(0xFF6C5674)
private val White = ComposeColor(0xFFFFFFFF)
private val SettingsPanelFill = ComposeColor(0xFFFFFDFE)
private val SettingsPanelBorder = ComposeColor(0xFFFFC7DE)
private val SettingsButtonBorder = ComposeColor(0xFFEEBDDA)
private val DictionaryBorder = ComposeColor(0xFF31C7D6)
private val StrokeBorder = ComposeColor(0xFFF6CAE1)
private val FontBorder = ComposeColor(0xFFFFD640)
private val PanelShape = RoundedCornerShape(24.dp)
private val ButtonShape = RoundedCornerShape(12.dp)

@Composable
fun ReferenceDataLinkPanel(model: SettingsReferenceDataLinkModel) {
    SettingsPanel {
        Text(
            text = model.title,
            color = Ink,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = model.body,
            color = Muted,
            fontSize = 15.sp
        )
        SettingsSecondaryButton(
            label = model.actionLabel,
            onClick = { model.onAction.run() }
        )
    }
}

@Composable
fun ReferenceDataScreen(model: SettingsReferenceDataScreenModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HomeFullWidthHomeButton(
            label = model.homeLabel,
            onClick = { model.onHome.run() }
        )
        DataSourcesIntro(model.intro)
        DataSourcesPanels(model.dataSources)
    }
}

@Composable
fun DataSourcesIntro(model: SettingsReferenceDataIntroModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SettingsSecondaryButton(
            label = model.backLabel,
            onClick = { model.onBack.run() }
        )
        Text(
            text = model.title,
            color = Ink,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = model.body,
            color = Muted,
            fontSize = 16.sp
        )
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
private fun SettingsPanel(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PanelShape,
        color = SettingsPanelFill,
        border = BorderStroke(1.dp, SettingsPanelBorder),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(start = 18.dp, top = 17.dp, end = 18.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsSecondaryButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 3.dp, vertical = 6.dp)
            .heightIn(min = 54.dp),
        shape = ButtonShape,
        border = BorderStroke(1.dp, SettingsButtonBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = White,
            contentColor = Ink
        )
    ) {
        Text(text = label)
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
