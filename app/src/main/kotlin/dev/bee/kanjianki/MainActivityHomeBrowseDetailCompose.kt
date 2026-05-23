@file:JvmName("MainActivityHomeBrowseDetailCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HomeTextCopy

@Composable
internal fun BrowseDetailScreen(model: BrowseDetailScreenModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        BrowseDetailHero(model.hero)
        BrowseDetailIdentity(model.identity)
        Box(modifier = Modifier.padding(top = 10.dp)) {
            BrowseDetailInfoPanel(model.reason)
        }
        model.localInventory?.let { localInventory ->
            BrowseDetailInfoPanel(localInventory)
        }
        BrowseDetailActions(model.actions)
        Box(modifier = Modifier.padding(top = 12.dp)) {
            RecoveryTimelinePanels(model.timeline)
        }
        if (model.examples.isNotEmpty()) {
            Text(
                text = model.examplesTitle,
                modifier = Modifier.padding(top = 12.dp),
                color = BrowseInk,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            model.examples.forEach { example ->
                BrowseExampleCard(example)
            }
        }
    }
}

@Composable
fun BrowseDetailMissing(model: BrowseDetailMissingModel) {
    Column(modifier = Modifier.fillMaxWidth()) {
        HomeFullWidthHomeButton(
            label = model.homeLabel,
            onClick = { model.onHome.run() }
        )
        Box(modifier = Modifier.padding(vertical = 8.dp)) {
            HomeEmptyState(
                title = model.title,
                body = model.body,
                style = HomeEmptyStateStyle.LegacyBand
            )
        }
    }
}

@Composable
fun BrowseDetailHero(model: BrowseDetailHeroModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HomeFullWidthHomeButton(
            label = model.navigationLabel,
            onClick = { model.onNavigate.run() }
        )
        Text(
            text = model.kanji,
            modifier = Modifier.fillMaxWidth(),
            color = BrowseInk,
            fontSize = 92.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            style = browseNoFontPaddingStyle(92)
        )
    }
}

@Composable
fun BrowseDetailIdentity(model: BrowseDetailIdentityModel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (model.suspended) {
            BrowseChip(label = HomeTextCopy.suspendedChipLabel(), color = BrowseCoral)
        }
        Text(
            text = model.title,
            color = BrowseInk,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            style = browseNoFontPaddingStyle(25)
        )
        if (model.reading.isNotEmpty()) {
            Text(
                text = model.reading,
                color = BrowseTeal,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BrowseDetailActions(model: BrowseDetailActionsModel) {
    var copied by remember(model.copyLabel) { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        model.reviewLabel?.let { label ->
            Button(
                onClick = { model.onReview?.run() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrowseCoral,
                    contentColor = BrowseWhite
                )
            ) {
                Text(
                    text = label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        model.copyLabel?.let { label ->
            OutlinedButton(
                onClick = {
                    copied = true
                    model.onCopy?.run()
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, ComposeColor(0xFFEBD6E4)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = BrowseWhite,
                    contentColor = BrowseInk
                )
            ) {
                Text(
                    text = if (copied) model.copiedLabel else label,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        OutlinedButton(
            onClick = { model.onSuspend.run() },
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, ComposeColor(0xFFEBD6E4)),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = BrowseWhite,
                contentColor = BrowseInk
            )
        ) {
            Text(
                text = model.suspendLabel,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun BrowseDetailInfoPanel(model: BrowseDetailPanelModel) {
    val accent = ComposeColor(model.color)
    val band = model.style == BrowseDetailPanelStyle.BAND
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = if (band) 8.dp else 7.dp),
        shape = BrowseCardShape,
        color = if (band) accent else BrowseWhite,
        border = BorderStroke(1.dp, accent)
    ) {
        Column(
            modifier = Modifier.padding(if (band) 20.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = model.title,
                color = if (band) BrowseWhite else BrowseInk,
                fontSize = if (band) 22.sp else 19.sp,
                fontWeight = FontWeight.Bold
            )
            model.lines.forEach { line ->
                Text(
                    text = line,
                    color = if (band) BrowseWhite else BrowseMuted,
                    fontSize = if (band) 17.sp else 15.sp
                )
            }
        }
    }
}

@Composable
private fun BrowseChip(label: String, color: ComposeColor) {
    Surface(
        modifier = Modifier.padding(top = 7.dp, end = 7.dp, bottom = 2.dp),
        shape = RoundedCornerShape(999.dp),
        color = browseSoftenedColor(color),
        border = BorderStroke(1.dp, color),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
