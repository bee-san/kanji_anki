@file:JvmName("MainActivityHomeChromeCompose")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import dev.bee.kanjianki.core.HomeTextCopy

internal fun homeActionModels(home: MainActivityHome): List<HomeActionModel> {
    return buildList {
        add(HomeActionModel(HomeTextCopy.browseActionLabel(), R.drawable.ic_book_24) { home.renderBrowseKanji("") })
        add(HomeActionModel(HomeTextCopy.recentMistakesTitle(), R.drawable.ic_trending_24, home::renderRecentMistakes))
        add(HomeActionModel(HomeTextCopy.statsActionLabel(), R.drawable.ic_stats_24, home::renderStats))
        add(HomeActionModel(HomeTextCopy.gamesActionLabel(), R.drawable.ic_game_24, home::renderGames))
        add(HomeActionModel(MainActivityBase.NAV_SETTINGS, R.drawable.ic_settings_24, home::renderSettings))
    }
}

internal fun homeSectionHeaderView(
    home: MainActivityHome,
    title: String,
    actionLabel: String?,
    action: Runnable?
): View {
    return ComposeView(home).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                Surface {
                    HomeSectionHeader(
                        title = title,
                        actionLabel = actionLabel,
                        onAction = action?.let { { it.run() } }
                    )
                }
            }
        }
    }
}

internal fun fullWidthHomeButtonView(home: MainActivityHome): View {
    return ComposeView(home).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, home.dp(56)).apply {
            setMargins(0, 0, 0, home.dp(10))
        }
        setOnClickListener { home.renderHome() }
        setContent {
            MaterialTheme {
                Surface {
                    HomeFullWidthHomeButton(
                        label = HomeTextCopy.homeLabel(),
                        onClick = home::renderHome
                    )
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

@Composable
fun HomeSectionHeader(
    title: String,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = Color(0xFF2D1635),
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = "$actionLabel >",
                color = Color(0xFFFF3A70),
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
                modifier = Modifier
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                    .semantics {
                        role = Role.Button
                    }
                    .clickable(onClick = onAction)
            )
        }
    }
}

@Composable
fun HomeFullWidthHomeButton(
    label: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(22.dp)
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
        shape = shape,
        border = BorderStroke(1.dp, Color(0xFFEBD6E4)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1E1E28)
        ),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_home_24),
            contentDescription = null,
            tint = Color(0xFF1E1E28)
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            textAlign = TextAlign.Center,
            maxLines = 1,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
