@file:JvmName("MainActivityHomeChromeCompose")

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.ComposeView
import dev.bee.kanjianki.core.HomeTextCopy

data class HomeActionModel(
    val label: String,
    val iconRes: Int,
    val onClick: () -> Unit,
)

internal fun homeActionRowView(home: MainActivityHome): View {
    val actions = buildList {
        add(HomeActionModel(HomeTextCopy.browseActionLabel(), R.drawable.ic_book_24) { home.renderBrowseKanji("") })
        add(HomeActionModel(HomeTextCopy.recentMistakesTitle(), R.drawable.ic_trending_24, home::renderRecentMistakes))
        add(HomeActionModel(HomeTextCopy.statsActionLabel(), R.drawable.ic_stats_24, home::renderStats))
        add(HomeActionModel(HomeTextCopy.gamesActionLabel(), R.drawable.ic_game_24, home::renderGames))
        add(HomeActionModel(MainActivityBase.NAV_SETTINGS, R.drawable.ic_settings_24, home::renderSettings))
        if (BuildConfig.DEBUG) {
            add(HomeActionModel("Compose shell", R.drawable.ic_sparkle_24, home::openComposeShell))
        }
    }
    return ComposeView(home).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                Surface {
                    HomeActionGrid(actions = actions)
                }
            }
        }
    }
}

@Composable
fun HomeActionGrid(actions: List<HomeActionModel>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.chunked(2).forEach { rowActions ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowActions.forEach { action ->
                    HomeActionButton(
                        action = action,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowActions.size == 1) {
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun HomeActionButton(action: HomeActionModel, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(18.dp)
    OutlinedButton(
        onClick = action.onClick,
        modifier = modifier.heightIn(min = 62.dp),
        shape = shape,
        border = BorderStroke(1.dp, Color(0xFFEBD6E4)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1E1E28)
        )
    ) {
        Icon(
            painter = painterResource(id = action.iconRes),
            contentDescription = null,
            tint = Color(0xFF1E1E28)
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = action.label,
            textAlign = TextAlign.Center,
            maxLines = 2,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
