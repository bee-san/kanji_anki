@file:JvmName("HomeComposeTestViews")

package dev.bee.kanjianki

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.ComposeView
import dev.bee.kanjianki.core.HomeTextCopy

internal fun homeActionRowTestView(home: MainActivityHome): View {
    return ComposeView(home).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        setContent {
            MaterialTheme {
                Surface {
                    HomeActionGrid(actions = homeActionModels(home))
                }
            }
        }
    }
}

internal fun homeSectionHeaderTestView(
    home: MainActivityHome,
    title: String,
    actionLabel: String?,
    action: Runnable?,
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

internal fun fullWidthHomeButtonTestView(home: MainActivityHome): View {
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
