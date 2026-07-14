@file:JvmName("MainActivitySettingsHowItWorksCompose")

package dev.bee.kanjianki

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.kanjianki.core.HowKaniWorksCopy

@Composable
internal fun HowKaniWorksScreen(sections: List<HowKaniWorksCopy.Section>) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = HowKaniWorksCopy.pageTitle(),
            color = KaniTheme.colors.ink,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
        )
        sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = section.title,
                    color = KaniTheme.colors.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = section.body,
                    color = KaniTheme.colors.muted,
                    fontSize = 15.sp,
                )
            }
        }
    }
}
